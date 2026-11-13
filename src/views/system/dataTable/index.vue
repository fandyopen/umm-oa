<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false" class="data-tables">
      <div class="dt-heading">
        <div>
          <h2>数据表管理</h2>
          <p>查看系统数据及关联关系，按账户权限维护数据。每次操作均记录修改内容和原因。</p>
        </div>
        <a-button icon="reload" :loading="catalogLoading" @click="refresh">刷新</a-button>
      </div>
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <div class="dt-layout">
        <aside class="dt-directory">
          <a-input-search v-model="keyword" allow-clear placeholder="搜索中文名称 / 表名" />
          <div class="dt-checks"><a-checkbox v-model="coreOnly">仅核心表</a-checkbox><a-checkbox v-model="hasDataOnly">仅有数据</a-checkbox></div>
          <div class="dt-directory-count">共 {{ tables.length }} 张表 · 当前显示 {{ filteredTables.length }} 张</div>
          <a-spin :spinning="catalogLoading">
            <div class="dt-table-list">
              <div v-for="group in groups" :key="group.name">
                <div class="dt-group">
                  {{ group.name }} <small>{{ group.rows.length }}</small>
                </div>
                <button v-for="item in group.rows" :key="item.name" class="dt-table-item" :class="{ active: selected && selected.name === item.name }" @click="selectTable(item)">
                  <span class="dt-item-title"
                    ><span><span v-if="item.core" class="dt-star">★ </span>{{ item.label }}</span
                    ><small>{{ item.count == null ? '—' : item.count }}</small></span
                  >
                  <code>{{ item.name }}</code>
                  <span v-if="item.legacy" class="dt-subtitle">历史表</span><span v-else-if="!item.registered" class="dt-subtitle">待补充业务说明</span>
                </button>
              </div>
              <a-empty v-if="!filteredTables.length && !catalogLoading" description="没有匹配的数据表" />
            </div>
          </a-spin>
        </aside>
        <section class="dt-content">
          <template v-if="selected">
            <div class="dt-table-heading">
              <h3><span v-if="selected.core" class="dt-star">★ </span>{{ selected.label }}</h3>
              <code>{{ selected.name }}</code
              ><a-tag>{{ selected.scope }}</a-tag
              ><a-tag :color="structure.fullMaintenance || structure.editAllowed ? 'cyan' : undefined">{{ maintenanceLabel }}</a-tag>
            </div>
            <a-alert
              v-if="selected.business"
              type="info"
              show-icon
              :message="structure.fullMaintenance ? '当前账号已开放直接维护权限；业务入口：' + selected.business : '业务入口：' + selected.business + '。状态、审批、任职和权限变更请在业务页面操作。'"
            />
            <a-alert v-if="!selected.readable" type="warning" show-icon message="此表的企业归属尚未登记，暂仅开放表结构和关联关系。" />
            <a-tabs v-model="tab" @change="tabChanged">
              <a-tab-pane key="data" tab="表数据">
                <div class="dt-toolbar">
                  <a-select v-model="filterField" show-search option-filter-prop="children" allow-clear placeholder="筛选字段" style="width: 210px" @change="filterValue = ''">
                    <a-select-option v-for="field in visibleFields" :key="field.name" :value="field.name">{{ fieldTitle(field) }}</a-select-option>
                  </a-select>
                  <a-select v-model="filterOp" style="width: 100px"
                    ><a-select-option v-for="op in operations" :key="op.value" :value="op.value">{{ op.label }}</a-select-option></a-select
                  >
                  <a-input v-model="filterValue" placeholder="筛选值" :disabled="['null', 'notnull'].includes(filterOp)" style="width: 180px" @pressEnter="search" />
                  <a-button type="primary" icon="search" :disabled="!selected.readable" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
                  <a-button v-if="structure.createAllowed" type="primary" icon="plus" :disabled="loading" @click="openEditor()">新增数据</a-button>
                  <a-select v-model="shownFields" mode="multiple" placeholder="显示字段" :max-tag-count="0" :max-tag-placeholder="() => '选择显示字段'" style="width: 160px">
                    <a-select-option v-for="field in visibleFields" :key="field.name" :value="field.name">{{ field.label }}</a-select-option>
                  </a-select>
                </div>
                <a-table size="small" :loading="loading" :columns="dataColumns" :data-source="rows" :row-key="rowKey" :pagination="pagination" :scroll="{ x: dataWidth }" @change="tableChanged">
                  <template slot="cell" slot-scope="value"
                    ><a-tooltip :title="display(value)"
                      ><span class="dt-cell">{{ display(value) }}</span></a-tooltip
                    ></template
                  >
                  <template slot="operations" slot-scope="text, row"
                    ><a @click="viewRow(row)">查看</a><template v-if="structure.editAllowed"><a-divider type="vertical" /><a @click="openEditor(row)">修改</a></template
                    ><template v-if="structure.deleteAllowed"><a-divider type="vertical" /><a class="dt-delete" @click="openDelete(row)">删除</a></template></template
                  >
                </a-table>
                <div class="dt-footnote">敏感认证字段不返回数据；大整数和金额按字符串展示，避免精度丢失。点击列头可以排序。</div>
              </a-tab-pane>
              <a-tab-pane key="structure" tab="表结构">
                <a-table size="small" :columns="structureColumns" :data-source="structure.columns || []" row-key="name" :pagination="false" :scroll="{ x: 1060 }">
                  <template slot="flags" slot-scope="text, field"
                    ><a-tag v-if="field.primary" color="gold">主键</a-tag><a-tag v-if="field.auto">自增</a-tag><a-tag v-if="field.sensitive" color="orange">敏感 / 已隐藏</a-tag
                    ><a-tag v-if="field.editable || field.createEditable" color="cyan">{{ field.immutable || !field.editable ? '仅新增填写' : '可修改' }}</a-tag></template
                  >
                  <template slot="nullable" slot-scope="value">{{ value ? '允许' : '不允许' }}</template>
                </a-table>
              </a-tab-pane>
              <a-tab-pane key="relations" tab="关联关系">
                <p class="dt-muted">包含数据库外键与已登记的业务关联。点击表名可跳转查看；删除时会检查被引用的数据。</p>
                <a-table size="small" :columns="relationColumns" :data-source="structure.relations || []" :row-key="relationKey" :pagination="false" :scroll="{ x: 800 }">
                  <a slot="tableLink" slot-scope="value" @click="jump(value)"
                    >{{ tableLabel(value) }}<br /><code>{{ value }}</code></a
                  >
                </a-table>
              </a-tab-pane>
              <a-tab-pane key="history" tab="修改记录">
                <a-table size="small" :loading="historyLoading" :columns="historyColumns" :data-source="historyRows" row-key="id" :pagination="historyPagination" :scroll="{ x: 900 }" @change="historyChanged"
                  ><a slot="historyDetail" slot-scope="text, record" @click="viewHistory(record)">查看对比</a></a-table
                >
              </a-tab-pane>
            </a-tabs>
          </template>
          <a-empty v-else description="请选择左侧数据表" />
        </section>
      </div>
    </a-card>
    <a-drawer :title="viewerTitle" width="min(880px, 100vw)" :visible="viewerVisible" @close="viewerVisible = false">
      <a-descriptions v-if="viewerRow" bordered :column="1" size="small"
        ><a-descriptions-item v-for="field in visibleFields" :key="field.name" :label="fieldTitle(field)">
          <pre class="dt-value">{{ display(viewerRow.values[field.name]) }}</pre>
        </a-descriptions-item></a-descriptions
      >
      <template v-else
        ><p>{{ viewerReason }}</p>
        <a-table size="small" :columns="diffColumns" :data-source="viewerDiff" row-key="field" :pagination="false" :scroll="{ x: 700 }">
          <pre slot="diffValue" slot-scope="value" class="dt-value">{{ display(value) }}</pre>
        </a-table></template
      >
    </a-drawer>
    <a-modal :title="creating ? '新增数据' : '修改数据'" :visible="editorVisible" :width="720" :mask-closable="false" :confirm-loading="saving" ok-text="下一步：核对修改" @ok="preview" @cancel="editorVisible = false">
      <a-alert
        type="info"
        show-icon
        :message="structure.fullMaintenance ? '当前账号可直接维护表数据。主键用于定位记录，企业归属自动填写；敏感字段仅允许写入，不显示原值。' : '仅开放下列普通资料字段。主键、企业归属和业务状态由系统控制。'"
      />
      <a-form layout="vertical" class="dt-edit-form"
        ><a-form-item v-for="field in editorFields" :key="field.name" :label="fieldTitle(field)" :required="!field.nullable && field.defaultValue == null">
          <a-input-password v-if="field.writeOnly" v-model="form[field.name]" :visibility-toggle="false" :max-length="Math.min(field.size, 100000)" :placeholder="creating ? '请输入新值' : '留空则保留原值'" />
          <a-select v-else-if="field.kind === 'boolean'" v-model="form[field.name]" allow-clear style="width: 100%"><a-select-option value="true">是</a-select-option><a-select-option value="false">否</a-select-option></a-select>
          <a-textarea v-else-if="field.kind === 'text'" v-model="form[field.name]" :rows="3" :max-length="Math.min(field.size, 100000)" />
          <a-input
            v-else
            v-model="form[field.name]"
            :max-length="field.kind === 'number' ? 40 : Math.min(field.size, 100000)"
            :placeholder="field.kind === 'date' ? 'YYYY-MM-DD' : field.kind === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : field.defaultValue == null ? '请输入' : '留空时使用默认值：' + field.defaultValue"
          />
          <a-checkbox v-if="field.nullable" :checked="form[field.name] === null" @change="event => setNull(field.name, event.target.checked)">设为空值 NULL</a-checkbox>
        </a-form-item></a-form
      >
      <a-form-item label="操作原因（必填）"><a-textarea v-model="reason" :max-length="500" :rows="2" placeholder="说明本次维护原因" /></a-form-item>
    </a-modal>
    <a-modal
      :title="deleting ? '确认删除' : '确认修改内容'"
      :visible="confirmVisible"
      :width="820"
      :confirm-loading="saving"
      :ok-button-props="{ props: { disabled: deleting && impacts.length > 0 } }"
      :ok-text="deleting ? '确认删除' : '确认保存'"
      :mask-closable="false"
      @ok="commit"
      @cancel="confirmVisible = false"
    >
      <template v-if="deleting"
        ><a-alert :type="impacts.length ? 'error' : 'warning'" show-icon :message="impacts.length ? '存在关联数据，禁止直接删除' : '未发现已登记的引用。删除后仅保留维护记录，请核对数据。'" />
        <p v-for="item in impacts" :key="item.table + item.column">{{ item.label }}（{{ item.table }}.{{ item.column }}）：{{ item.count }} 条</p>
        <pre class="dt-value">{{ pendingRow && JSON.stringify(pendingRow.key) }}</pre>
        <a-textarea v-model="reason" :rows="2" :max-length="500" placeholder="请填写删除原因"
      /></template>
      <template v-else
        ><p>操作原因：{{ reason }}</p>
        <a-table size="small" :columns="diffColumns" :data-source="previewDiff" row-key="field" :pagination="false" :scroll="{ x: 700 }">
          <pre slot="diffValue" slot-scope="value" class="dt-value">{{ display(value) }}</pre>
        </a-table></template
      >
      <a-alert v-if="saveError" type="error" show-icon :message="saveError" style="margin-top: 16px" />
    </a-modal>
  </page-header-wrapper>
</template>

<script>
import { Empty } from 'ant-design-vue'
import { listDataTables, getDataTableStructure, getDataTableRows, getDataTableHistory, getDataTableImpact, saveDataTableRow, deleteDataTableRow } from '/src/api/system/dataTable'

const diffColumns = [
  { title: '字段', dataIndex: 'label', width: 220 },
  { title: '修改前', dataIndex: 'before', scopedSlots: { customRender: 'diffValue' }, width: 230 },
  { title: '修改后', dataIndex: 'after', scopedSlots: { customRender: 'diffValue' }, width: 230 }
]
export default {
  name: 'DataTableManage',
  components: { AEmpty: Empty },
  data() {
    return {
      tables: [],
      selected: null,
      structure: {},
      keyword: '',
      coreOnly: false,
      hasDataOnly: false,
      catalogLoading: false,
      loading: false,
      error: '',
      tab: 'data',
      rows: [],
      total: 0,
      page: 1,
      size: 20,
      filterField: undefined,
      filterOp: 'eq',
      filterValue: '',
      appliedFilters: [],
      sort: undefined,
      direction: 'desc',
      shownFields: [],
      historyRows: [],
      historyTotal: 0,
      historyPage: 1,
      historyLoading: false,
      requestSequence: 0,
      rowSequence: 0,
      historySequence: 0,
      editorVisible: false,
      creating: false,
      form: {},
      original: null,
      reason: '',
      confirmVisible: false,
      deleting: false,
      pendingRow: null,
      pendingValues: {},
      impacts: [],
      saving: false,
      saveError: '',
      viewerVisible: false,
      viewerTitle: '',
      viewerRow: null,
      viewerReason: '',
      viewerDiff: [],
      previewDiff: [],
      diffColumns,
      operations: [
        { value: 'eq', label: '等于' },
        { value: 'contains', label: '包含' },
        { value: 'ne', label: '不等于' },
        { value: 'gt', label: '大于' },
        { value: 'ge', label: '大于等于' },
        { value: 'lt', label: '小于' },
        { value: 'le', label: '小于等于' },
        { value: 'null', label: '为空' },
        { value: 'notnull', label: '不为空' }
      ],
      structureColumns: [
        { title: '字段名', dataIndex: 'name', width: 190 },
        { title: '中文说明', dataIndex: 'label', width: 150 },
        { title: '类型', dataIndex: 'type', width: 160 },
        { title: '长度', dataIndex: 'size', width: 100 },
        { title: '小数位', dataIndex: 'scale', width: 80 },
        { title: '允许空值', dataIndex: 'nullable', scopedSlots: { customRender: 'nullable' }, width: 100 },
        { title: '默认值', dataIndex: 'defaultValue', width: 150 },
        { title: '属性', scopedSlots: { customRender: 'flags' }, width: 250 }
      ],
      relationColumns: [
        { title: '引用表', dataIndex: 'table', scopedSlots: { customRender: 'tableLink' }, width: 250 },
        { title: '关联字段', dataIndex: 'column', width: 150 },
        { title: '目标表', dataIndex: 'target', scopedSlots: { customRender: 'tableLink' }, width: 250 },
        { title: '目标字段', dataIndex: 'targetColumn', width: 150 }
      ],
      historyColumns: [
        { title: '操作时间', dataIndex: 'create_time', width: 180 },
        { title: '操作人', dataIndex: 'operator_name', width: 120 },
        { title: '操作', dataIndex: 'action', width: 90 },
        { title: '记录主键', dataIndex: 'record_key', width: 170 },
        { title: '操作原因', dataIndex: 'reason', width: 220 },
        { title: '详情', scopedSlots: { customRender: 'historyDetail' }, fixed: 'right', width: 100 }
      ]
    }
  },
  computed: {
    filteredTables() {
      const key = this.keyword.toLowerCase().trim()
      return this.tables.filter(t => (!this.coreOnly || t.core) && (!this.hasDataOnly || t.count > 0) && (!key || (t.label + ' ' + t.name).toLowerCase().includes(key)))
    },
    groups() {
      const order = ['系统', '人事', '客户与合同', '招聘', '财务', '审批流程', '历史业务', '其他']
      return order.map(name => ({ name, rows: this.filteredTables.filter(t => t.group === name) })).filter(g => g.rows.length)
    },
    visibleFields() {
      return (this.structure.columns || []).filter(f => !f.sensitive)
    },
    editorFields() {
      return (this.structure.columns || []).filter(f => (this.creating ? f.createEditable : f.editable && !f.immutable))
    },
    maintenanceLabel() {
      if (this.structure.fullMaintenance && (this.structure.editAllowed || this.structure.createAllowed || this.structure.deleteAllowed)) return '完整维护'
      return this.structure.editAllowed ? '允许限定字段维护' : '只读'
    },
    dataColumns() {
      const columns = this.visibleFields
        .filter(f => this.shownFields.includes(f.name))
        .map(f => ({
          title: this.fieldTitle(f),
          dataIndex: 'values.' + f.name,
          key: f.name,
          width: 200,
          sorter: true,
          sortOrder: this.sort === f.name ? (this.direction === 'asc' ? 'ascend' : 'descend') : false,
          scopedSlots: { customRender: 'cell' }
        }))
      columns.push({ title: '操作', key: 'operations', width: this.structure.deleteAllowed ? 180 : this.structure.editAllowed ? 130 : 80, fixed: 'right', scopedSlots: { customRender: 'operations' } })
      return columns
    },
    dataWidth() {
      return Math.max(600, this.shownFields.length * 200 + 180)
    },
    pagination() {
      return { current: this.page, pageSize: this.size, total: this.total, showSizeChanger: true, pageSizeOptions: ['10', '20', '50', '100'], showTotal: n => '共 ' + n + ' 条' }
    },
    historyPagination() {
      return { current: this.historyPage, pageSize: 20, total: this.historyTotal, showTotal: n => '共 ' + n + ' 条' }
    }
  },
  created() {
    this.refresh()
  },
  beforeDestroy() {
    this.requestSequence++
    this.rowSequence++
    this.historySequence++
  },
  methods: {
    fieldTitle(field) {
      const label = (field.label || '').trim()
      return !label || label === field.name ? field.name : label + ' · ' + field.name
    },
    async refresh() {
      this.catalogLoading = true
      this.error = ''
      try {
        const { data } = await listDataTables()
        this.tables = data
        const current = data.find(t => this.selected && t.name === this.selected.name)
        if (current) await this.selectTable(current)
        else if (!this.selected) await this.selectTable(data.find(t => t.name === 'sys_user') || data[0])
      } catch (e) {
        this.error = e.message || '加载数据表失败'
      } finally {
        this.catalogLoading = false
      }
    },
    async selectTable(item) {
      if (!item) return
      const seq = ++this.requestSequence
      this.rowSequence++
      this.historySequence++
      this.selected = item
      this.structure = {}
      this.rows = []
      this.total = 0
      this.historyRows = []
      this.historyTotal = 0
      this.page = 1
      this.historyPage = 1
      this.filterField = undefined
      this.filterValue = ''
      this.filterOp = 'eq'
      this.appliedFilters = []
      this.sort = undefined
      this.direction = 'desc'
      this.error = ''
      this.tab = 'data'
      this.loading = true
      this.editorVisible = false
      this.confirmVisible = false
      this.viewerVisible = false
      try {
        const { data } = await getDataTableStructure(item.name)
        if (seq !== this.requestSequence) return
        this.structure = data
        this.shownFields = data.columns.filter(f => !f.sensitive).map(f => f.name)
        if (item.readable) await this.loadRows()
      } catch (e) {
        if (seq === this.requestSequence) this.error = e.message || '加载表结构失败'
      } finally {
        if (seq === this.requestSequence) this.loading = false
      }
    },
    async loadRows() {
      if (!this.selected || !this.selected.readable) return
      const seq = ++this.rowSequence
      const table = this.selected.name
      this.loading = true
      this.error = ''
      try {
        const { data } = await getDataTableRows(table, { page: this.page, size: this.size, sort: this.sort, direction: this.direction, filters: JSON.stringify(this.appliedFilters) })
        if (seq !== this.rowSequence || table !== this.selected.name) return
        this.rows = data.rows
        this.total = data.total
      } catch (e) {
        if (seq === this.rowSequence) this.error = e.message || '查询数据失败'
      } finally {
        if (seq === this.rowSequence) this.loading = false
      }
    },
    search() {
      this.page = 1
      this.appliedFilters = this.filterField ? [{ field: this.filterField, op: this.filterOp, value: this.filterValue }] : []
      this.loadRows()
    },
    reset() {
      this.filterField = undefined
      this.filterOp = 'eq'
      this.filterValue = ''
      this.appliedFilters = []
      this.page = 1
      this.sort = undefined
      this.direction = 'desc'
      this.loadRows()
    },
    tableChanged(p, filters, sorter) {
      this.page = p.current
      this.size = p.pageSize
      this.sort = sorter.order ? sorter.columnKey : undefined
      this.direction = sorter.order === 'ascend' ? 'asc' : 'desc'
      this.loadRows()
    },
    tabChanged(value) {
      if (value === 'history') this.loadHistory()
    },
    async loadHistory() {
      const seq = ++this.historySequence
      const table = this.selected.name
      this.historyLoading = true
      try {
        const { data } = await getDataTableHistory(table, { page: this.historyPage, size: 20 })
        if (seq === this.historySequence && table === this.selected.name) {
          this.historyRows = data.rows
          this.historyTotal = data.total
        }
      } catch (e) {
        if (seq === this.historySequence) this.error = e.message || '加载修改记录失败'
      } finally {
        if (seq === this.historySequence) this.historyLoading = false
      }
    },
    historyChanged(p) {
      this.historyPage = p.current
      this.loadHistory()
    },
    display(value) {
      return value === null || value === undefined ? 'NULL' : typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
    },
    rowKey(row, index) {
      return JSON.stringify(row.key) + row.version + index
    },
    relationKey(row) {
      return [row.table, row.column, row.target, row.targetColumn].join('.')
    },
    tableLabel(name) {
      return (this.tables.find(t => t.name === name) || {}).label || name
    },
    jump(name) {
      const table = this.tables.find(t => t.name === name)
      if (table) this.selectTable(table)
    },
    viewRow(row) {
      this.viewerTitle = this.selected.label + ' · 数据详情'
      this.viewerRow = row
      this.viewerVisible = true
    },
    diff(before, after) {
      const fields = [...new Set([...Object.keys(before || {}), ...Object.keys(after || {})])]
      return fields
        .filter(field => this.display((before || {})[field]) !== this.display((after || {})[field]))
        .map(field => {
          const definition = (this.structure.columns || []).find(c => c.name === field) || {}
          return { field, label: definition.label || field, before: definition.sensitive ? '[已隐藏]' : (before || {})[field], after: definition.sensitive ? '[已填写，不显示原值]' : (after || {})[field] }
        })
    },
    viewHistory(record) {
      this.viewerTitle = record.action + ' · 修改对比'
      this.viewerRow = null
      this.viewerReason = record.reason
      const before = record.before_data ? JSON.parse(record.before_data) : {}
      const after = record.after_data ? JSON.parse(record.after_data) : {}
      this.viewerDiff = this.diff(before, after)
      this.viewerVisible = true
    },
    openEditor(row) {
      this.creating = !row
      this.original = row || null
      this.form = {}
      this.reason = ''
      this.saveError = ''
      this.deleting = false
      this.$nextTick(() => {
        this.editorFields.forEach(f => this.$set(this.form, f.name, row && !f.writeOnly ? row.values[f.name] : ''))
        this.editorVisible = true
      })
    },
    setNull(field, checked) {
      this.$set(this.form, field, checked ? null : '')
    },
    preview() {
      this.saveError = ''
      if (!this.reason.trim()) return this.$message.warning('请填写操作原因')
      const values = {}
      this.editorFields.forEach(f => {
        const value = this.form[f.name]
        if (!this.creating && f.writeOnly && (value === '' || value === undefined)) return
        if (this.creating && (value === '' || value === undefined) && (f.defaultValue != null || f.nullable)) return
        if (this.creating || this.display(value) !== this.display(this.original.values[f.name])) values[f.name] = value
      })
      if (!Object.keys(values).length) return this.$message.warning('没有需要保存的内容')
      const missing = this.editorFields.find(f => this.creating && !f.nullable && f.defaultValue == null && (values[f.name] === '' || values[f.name] === null || values[f.name] === undefined))
      if (missing) return this.$message.warning('请填写' + missing.label)
      this.pendingValues = values
      this.previewDiff = this.diff(this.creating ? {} : this.original.values, this.creating ? values : { ...this.original.values, ...values })
      this.deleting = false
      this.confirmVisible = true
    },
    async openDelete(row) {
      const table = this.selected.name
      const seq = this.requestSequence
      this.saveError = ''
      this.reason = ''
      try {
        const { data } = await getDataTableImpact(table, { key: row.key, version: row.version })
        if (seq !== this.requestSequence) return
        this.impacts = data
        this.pendingRow = row
        this.deleting = true
        this.confirmVisible = true
      } catch (e) {
        this.$message.error(e.message || '关联检查失败')
      }
    },
    async commit() {
      if (!this.reason.trim()) return this.$message.warning('请填写操作原因')
      this.saving = true
      this.saveError = ''
      const table = this.selected.name
      try {
        if (this.deleting) await deleteDataTableRow(table, { key: this.pendingRow.key, version: this.pendingRow.version, reason: this.reason })
        else await saveDataTableRow(table, { values: this.pendingValues, key: this.original ? this.original.key : {}, version: this.original ? this.original.version : '', reason: this.reason }, this.creating)
        this.$message.success('操作成功，已记录修改内容')
        this.editorVisible = false
        this.confirmVisible = false
        await this.refresh()
      } catch (e) {
        this.saveError = e.message || '操作失败，请刷新后重试'
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.data-tables {
  min-height: 680px;
}
.dt-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
  h2 {
    margin: 0 0 8px;
    font-size: 21px;
  }
  p {
    margin: 0;
    color: var(--umm-muted, #718096);
    font-size: 13px;
  }
}
.dt-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 24px;
  margin-top: 18px;
}
.dt-directory {
  min-width: 0;
  border-right: 1px solid var(--umm-border, #e8edf3);
  padding-right: 18px;
}
.dt-checks {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  font-size: 12px;
  .ant-checkbox-wrapper + .ant-checkbox-wrapper {
    margin-left: 0;
  }
}
.dt-directory-count,
.dt-footnote,
.dt-muted {
  font-size: 12px;
  color: var(--umm-muted, #718096);
  margin: 12px 0;
}
.dt-table-list {
  max-height: 660px;
  overflow: auto;
  padding-right: 4px;
}
.dt-group {
  font-weight: 600;
  font-size: 12px;
  padding: 14px 10px 8px;
  color: var(--umm-muted, #718096);
  small {
    margin-left: 8px;
    font-weight: 400;
  }
}
.dt-table-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  color: inherit;
  border-radius: 7px;
  padding: 9px 10px;
  cursor: pointer;
  margin-bottom: 3px;
  code {
    display: block;
    font-size: 11px;
    overflow-wrap: anywhere;
    color: var(--umm-muted, #718096);
    margin-top: 4px;
  }
  &:hover {
    background: var(--umm-primary-soft, #edf7f6);
  }
  &.active {
    background: var(--umm-primary-soft, #edf7f6);
    border-color: var(--umm-primary, #0eb3a2);
    .dt-item-title {
      color: var(--umm-primary, #0eb3a2);
    }
  }
}
.dt-item-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  small {
    color: var(--umm-muted, #718096);
  }
}
.dt-star {
  color: #d49a23;
}
.dt-subtitle {
  font-size: 10px;
  color: var(--umm-muted, #718096);
}
.dt-content {
  min-width: 0;
}
.dt-table-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 15px;
  h3 {
    margin: 0;
    font-size: 17px;
  }
  code {
    font-size: 12px;
  }
}
.dt-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.dt-cell {
  display: block;
  max-width: 175px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
}
.dt-delete {
  color: #d94a4a;
}
.dt-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
  font-family: inherit;
  font-size: 12px;
  margin: 0;
  background: transparent;
  color: inherit;
}
.dt-edit-form {
  margin-top: 18px;
  max-height: 48vh;
  overflow: auto;
  padding-right: 10px;
}
@media (max-width: 1100px) {
  .dt-layout {
    grid-template-columns: 230px minmax(0, 1fr);
    gap: 16px;
  }
}
@media (max-width: 767px) {
  .dt-layout {
    display: block;
  }
  .dt-directory {
    border-right: 0;
    border-bottom: 1px solid var(--umm-border, #e8edf3);
    padding: 0 0 15px;
    margin-bottom: 20px;
  }
  .dt-table-list {
    max-height: 220px;
  }
  .dt-heading h2 {
    font-size: 18px;
  }
}
</style>
