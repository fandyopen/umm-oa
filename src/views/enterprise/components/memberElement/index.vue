<template>
  <div>
    <div class="depart-info">
      <div class="depart-name">
        <span class="depart-title">{{ userTitle }}</span>
        <span class="member-count">{{ memberCount }} 人</span>
      </div>
      <div class="depart-operate" v-if="title === '全部成员'">
        <div class="edit-name-tip">
          <a-switch size="small" defaultChecked />
          <span>允许成员修改姓名</span>
        </div>
      </div>
      <div class="depart-operate" v-if="!readOnlyDepartments && deptId && title !== '全部成员' && title !== '离职成员'">
        <a-popover v-if="!hideDepartmentNameActions" trigger="click" v-model="visible">
          <template slot="content">
            <div class="editNameInput">
              <a-input placeholder="修改名称" v-model="editNameVal" />
            </div>
            <div class="editNameBtn">
              <a-button @click="editDepName(0)"> 取消 </a-button>
              <a-button type="primary" @click="editDepName(1)"> 确认 </a-button>
            </div>
          </template>
          <a-button type="link">修改名称</a-button>
        </a-popover>
        <a-divider v-if="!hideDepartmentNameActions" type="vertical" />
        <a-popover trigger="click" v-model="DepVisible">
          <template slot="content">
            <div class="editNameInput">
              <a-input placeholder="添加子部门" v-model="addDepchildVal" />
            </div>
            <div class="editNameBtn">
              <a-button @click="addDepchild(0)"> 取消 </a-button>
              <a-button type="primary" @click="addDepchild(1)"> 确认 </a-button>
            </div>
          </template>
          <a-button type="link">添加子部门</a-button>
        </a-popover>
        <a-popover v-if="!hideDepartmentNameActions" trigger="click">
          <template slot="content">
            <div class="editNameBtn">
              <a-button @click="deleteDept(false)"> 取消 </a-button>
              <a-button type="primary" @click="deleteDept(true)"> 确认 </a-button>
            </div>
          </template>
          <a-button type="link">删除部门</a-button>
        </a-popover>
      </div>
    </div>
    <div class="member-btn-pane">
      <div class="btn-group" v-if="title === '离职成员' ? false : true">
        <a-button ghost type="primary" @click="inviteMembers" v-hasModuleAction="{ ...moduleMeta, actionCode: 'invite', allowUnresolved: !!moduleActionGuard }" :loading="!!moduleActionBusy.inviteMembers"> 邀请成员 </a-button>
        <a-popover placement="bottom" :trigger="[]" :visible="adjusDepVisible">
          <template slot="content">
            <div class="setDepTree">
              <SelectDepartmentElement :multiple="false" :treeData="adjusDepTreeData" :replaceFields="adjusReplaceFields" @returnData="returnAdjusData" />
            </div>
            <div class="editNameBtn">
              <a-button @click="adjusting(0)"> 取消 </a-button>
              <a-button type="primary" @click="adjusting(1)"> 确认 </a-button>
            </div>
          </template>
          <a-button ghost type="primary" @click="initAdjusDepTreeData" v-hasModuleAction="{ ...moduleMeta, actionCode: 'transfer', allowUnresolved: !!moduleActionGuard }" :loading="!!moduleActionBusy.initAdjusDepTreeData"
            >调整部门</a-button
          >
        </a-popover>
        <a-button ghost type="primary" @click="mitosis" v-hasModuleAction="{ ...moduleMeta, actionCode: 'dismiss', allowUnresolved: !!moduleActionGuard }" :loading="!!moduleActionBusy.mitosis"> 转为离职 </a-button>
      </div>
      <a-button v-if="canDeleteMembers" type="danger" icon="delete" :disabled="!selectedRowKeys.length" @click="deleteMembers()">删除员工</a-button>
      <div class="member-filter-pane" v-if="showQueryFilters">
        <a-tree-select v-model="filterDeptId" :tree-data="treeData" :replace-fields="deptFilterFields" placeholder="全部部门" allow-clear show-search tree-default-expand-all class="tw-w-[190px]" @change="handleDeptFilterChange" />
        <a-range-picker v-model="addedDateRange" format="YYYY-MM-DD" :placeholder="['新增开始日期', '新增结束日期']" class="tw-w-[260px]" @change="handleAddedDateChange" />
      </div>
      <div class="earch-input">
        <a-input-search placeholder="搜索成员" size="small" class="tw-w-[200px]" allow-clear @search="onSearch" />
      </div>
    </div>
    <div class="member-table-content">
      <div class="member-table">
        <!-- 数据表格 -->
        <s-table ref="table" size="default" rowKey="userId" :columns="visibleColumns" :data="loadData" :rowSelection="rowSelection" :customRow="customRow" :scroll="tableScroll">
          <span slot="deptAction" slot-scope="text, record" class="dept-action" @click.stop>
            <template v-if="showDepartmentActions">
              <a-button v-if="isDepartmentPrincipal(record)" type="link" class="cancel-principal" @click.stop="clearDepartmentPrincipal(record)" :loading="!!moduleActionBusy.clearDepartmentPrincipal">取消主管</a-button>
              <a-button v-else type="link" @click.stop="setDepartmentPrincipal(record)" :loading="!!moduleActionBusy.setDepartmentPrincipal">设为主管</a-button>
            </template>
            <a-button v-if="canDeleteMembers && Number(record.userId) !== 1" type="link" class="delete-member" @click.stop="deleteMembers(record)">删除</a-button>
            <span v-else-if="canDeleteMembers && !showDepartmentActions">固定超级管理员</span>
          </span>
        </s-table>
      </div>
    </div>
    <a-modal :visible="importPreviewVisible" title="通讯录导入预览" :width="1050" :confirm-loading="!!moduleActionBusy.submitMemberImport" ok-text="确认提交" @ok="submitMemberImport" @cancel="importPreviewVisible = false">
      <a-alert type="info" show-icon :message="'校验通过，共 ' + importRows.length + ' 名成员。确认后按邀请成员流程提交；需审批时不会立即创建成员。'" class="tw-mb-4" />
      <a-table :columns="importColumns" :data-source="importRows" row-key="rowNumber" :pagination="{ pageSize: 8 }" :scroll="{ x: 1030 }" size="small" />
    </a-modal>
    <!-- 邀请成员弹窗 -->
    <a-modal v-model="isIMmodal" title="邀请成员" width="600px" :footer="null" destroyOnClose>
      <a-menu v-model="current" mode="horizontal">
        <a-menu-item key="手动添加成员">手动添加成员 </a-menu-item>
        <a-menu-item key="批量导入成员">批量导入成员 </a-menu-item>
        <a-menu-item key="公开链接邀请">公开链接邀请 </a-menu-item>
      </a-menu>
      <div class="modelContent">
        <div v-if="current[0] === '手动添加成员'">
          <div class="manual-tip">
            <li class="tip-item">成员加入时无法更改您输入的内容，请输入正确的信息</li>
            <li class="tip-item">按输入的联系方式发送邀请；同时输入手机和邮箱，只发送手机邀请</li>
          </div>
          <div class="member-info-form">
            <a-form-model ref="ruleForm" :model="form" :rules="rules" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
              <a-form-model-item label="姓名" prop="nickName">
                <a-input v-model="form.nickName" />
              </a-form-model-item>
              <a-form-model-item label="手机" prop="phonenumber">
                <div class="p-a-c">
                  <countryCodeSelector :countryCode.sync="countryCode" />
                  <a-input v-model="form.phonenumber" />
                </div>
              </a-form-model-item>
              <a-form-model-item label="邮箱" prop="email">
                <a-input v-model="form.email" />
              </a-form-model-item>
            </a-form-model>
          </div>
          <div class="dept-item">
            <div class="item-label">部门：</div>
            <div class="item-content">
              <SelectDepartmentElement :multiple="false" :treeData="treeData" @returnData="returnData" />
            </div>
          </div>
          <div class="btn-group">
            <a-button type="primary" @click="onSubmit" :loading="!!moduleActionBusy.onSubmit"> 邀请 </a-button>
            <a-button @click="resetForm">清空</a-button>
          </div>
        </div>
        <div v-if="current[0] === '批量导入成员'">
          <div class="manual-tip">
            <li class="tip-item">请下载<span class="Download_tem" @click="downloadTem">通讯录模板</span>，按格式修改后导入</li>
            <li class="tip-item">部门可填ID或完整名称；名称错误、停用或重名时整批不导入，重名请填写部门ID。</li>
            <li class="tip-item">每次最多200名成员，先校验预览再确认；任一行错误整批不导入，沿用邀请成员审批流程。</li>
          </div>
          <div class="upload-wrapper">
            <a-upload :before-upload="readMemberImport" :show-upload-list="false" accept=".xlsx,.xls">
              <a-button :loading="!!moduleActionBusy.readMemberImport">选择文件并校验</a-button>
            </a-upload>
            <a-alert v-if="importError" type="error" show-icon message="导入校验失败，整批未导入" :description="importError" class="tw-mt-3" style="white-space: pre-wrap" />
          </div>
        </div>
        <div v-if="current[0] === '公开链接邀请'">
          <div class="public-link-panel">
            <div class="public-link-operate">
              <p class="link-msg">邀请链接开启邀请链接后，只需访问此链接，即可加入您的企业</p>
              <div class="switch">
                <a-switch v-model="checked" checked-children="开" un-checked-children="关" />
              </div>
            </div>
            <div class="public-link-address" v-if="checked">
              <p class="link-msg">链接地址</p>
              <div class="input-wallperpar">
                <a-input v-model="defaultValue" ref="inputRef" :read-only="true">
                  <div class="copy" slot="addonAfter" @click="copyInputVal">复制</div>
                </a-input>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
import { withModuleActionGuards } from '/src/utils/moduleActionGuard.js'
import { STable } from '/src/components/index.js'
import { parseMemberImport } from '/src/utils/memberImport.mjs'
import { downloadFixedTemplate, getMemberTemplateSchema, previewMemberImport } from '/src/api/system/templates.js'
import { previewDoc } from '/src/components/Office/office.js'
import { getUserList, getDismissList, editDeptName, getTreeMenu, addSystemDepchild, setDeptPrincipal, clearDeptPrincipal } from '/src/api/enterprise/index.js'
import { delDept } from '/src/api/system/dept.js'
import { delUser } from '/src/api/system/user.js'
import SelectDepartmentElement from '../selectDepartmentElement/index.vue'
import countryCodeSelector from '/src/components/CountryCodeSelector/index.vue'
import Details from './details.vue'
import { hasModuleAction } from '/src/utils/modulePermission.js'

export default {
  mixins: [
    withModuleActionGuards({
      inviteMembers: { module: 'members', action: 'invite', label: '邀请成员' },
      onSubmit: { module: 'members', action: 'invite', label: '提交成员邀请' },
      readMemberImport: { module: 'members', action: 'invite', label: '校验导入成员' },
      submitMemberImport: { module: 'members', action: 'invite', label: '批量邀请成员' },
      initAdjusDepTreeData: { module: 'members', action: 'transfer', label: '调整部门' },
      adjusting: function (value) {
        return value === 1 ? { module: 'members', action: 'transfer', label: '保存部门调整' } : null
      },
      mitosis: { module: 'members', action: 'dismiss', label: '转为离职' },
      setDepartmentPrincipal: { module: 'members', action: 'manage', label: '设为部门主管' },
      clearDepartmentPrincipal: { module: 'members', action: 'manage', label: '取消部门主管' },
      editDepName: function (value) {
        return value === 1 ? { module: 'members', action: 'manage', label: '修改部门名称' } : null
      },
      addDepchild: function (value) {
        return value === 1 ? { module: 'members', action: 'manage', label: '添加子部门' } : null
      },
      deleteDept: function (value) {
        return value ? { module: 'members', action: 'manage', label: '删除部门' } : null
      }
    })
  ],
  components: { STable, SelectDepartmentElement, countryCodeSelector },
  props: {
    configuredActionSubmitter: { type: Function, default: null },
    moduleActionGuard: { type: Function, default: null },
    moduleActionVisible: { type: Function, default: null },
    // The owner (HR or organization) resolves this context; shared members never borrow an unrelated route.
    moduleMeta: { type: Object, default: null },
    title: {
      type: String,
      default: '',
      require: true
    },
    deptId: {
      type: Number,
      default: null
    },
    treeNodeData: {
      type: Object,
      require: false,
      default: () => ({})
    },
    showRoleInfo: {
      type: Boolean,
      default: true
    },
    hideDepartmentNameActions: {
      type: Boolean,
      default: false
    },
    readOnlyDepartments: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      selectedRowKeys: [],
      selectedRows: [],
      /* 查询参数 */
      UserListParams: {
        orderByColumn: 'create_time',
        isAsc: 'desc',
        ...(this.deptId ? { deptId: this.deptId } : {})
      },
      filterDeptId: null,
      addedDateRange: [],
      deptFilterFields: { children: 'children', title: 'label', label: 'label', value: 'id', key: 'id' },
      // 加载数据方法 必须为 Promise 对象
      loadData: param => {
        this.UserListParams = Object.assign({}, this.UserListParams, param, {
          orderByColumn: 'create_time',
          isAsc: 'desc'
        })
        if (this.title === '离职成员') {
          return getDismissList(this.UserListParams).then(res => {
            this.memberCount = Number(res.total || 0)
            return {
              data: res.rows,
              totalCount: res.total,
              pageNum: param.pageNum,
              pageSize: param.pageSize
            }
          })
        } else {
          return getUserList(this.UserListParams).then(res => {
            this.memberCount = Number(res.total || 0)
            return {
              data: res.rows,
              totalCount: res.total,
              pageNum: param.pageNum,
              pageSize: param.pageSize
            }
          })
        }
      },
      columns: [
        {
          title: '姓名',
          dataIndex: 'nickName',
          align: 'center'
        },
        {
          title: '手机',
          dataIndex: 'phonenumber',
          align: 'center'
        },
        {
          title: '邮箱',
          dataIndex: 'email',
          align: 'center'
        },
        {
          title: '部门',
          dataIndex: 'deptName',
          align: 'center',
          customRender: (text, record) => record.dept?.deptName || text || '-'
        },
        {
          title: '部门ID',
          dataIndex: 'deptId',
          width: 100,
          align: 'center',
          customRender: (text, record) => text ?? record.dept?.deptId ?? '-'
        },
        {
          title: '新增日期',
          dataIndex: 'createTime',
          align: 'center',
          customRender: text => text || '-'
        },
        {
          title: '角色',
          dataIndex: 'roles',
          roleRelated: true,
          align: 'center',
          customRender: (text, record) => {
            if (text.length > 1) {
              let roles = ''
              text.forEach((item, index) => {
                roles += item.roleName + ','
              })
              return roles
            } else if (text.length === 1) {
              return text[0].roleName
            } else {
              return ''
            }
          }
        },
        {
          title: '部门身份',
          dataIndex: 'deptPrincipal',
          roleRelated: true,
          align: 'center',
          customRender: text => (Number(text) === 1 ? '部门主管' : '成员')
        }
      ],
      /* 标题 */
      userTitle: '',
      /* 当前筛选条件下的成员人数 */
      memberCount: 0,
      /* 修改名称气泡弹窗 */
      visible: false,
      /* 添加子部门气泡 */
      DepVisible: false,
      /* 修改名称val */
      editNameVal: '',
      /* 添加子部门 */
      addDepchildVal: '',
      /* 部门ID */
      selectTreeNodeId: null,
      /* 邀请成员弹窗 */
      isIMmodal: false,
      current: ['手动添加成员'],
      /* 表单 */
      form: {},
      /* 表单规则 */
      rules: {
        nickName: [{ required: true, message: '输入名称', trigger: 'blur' }],
        phonenumber: [{ required: true, message: '输入手机号', trigger: 'blur' }]
      },
      /* 部门树 */
      treeData: [],
      importPreviewVisible: false,
      importRows: [],
      importFileName: '',
      importError: '',
      importColumns: [
        { title: '文件行号', dataIndex: 'rowNumber', width: 90 },
        { title: '姓名', dataIndex: 'nickName', width: 110 },
        { title: '手机号码', dataIndex: 'phonenumber', width: 150 },
        { title: '邮箱', dataIndex: 'email', width: 180 },
        { title: '部门ID', dataIndex: 'deptId', width: 100 },
        { title: '部门', dataIndex: 'deptName', width: 140 },
        { title: '性别', dataIndex: 'sex', width: 80, customRender: value => ({ 0: '男', 1: '女', 2: '未知' }[value] || value) },
        { title: '备注', dataIndex: 'remark', width: 180 }
      ],
      /* 是否开启链接 */
      checked: false,
      /* 链接地址 */
      defaultValue: 'aaaaaaaaaaaaaaaaaaa',
      /* 区号 */
      countryCode: 86,
      /* 部门树返回的数据 */
      rData: [],
      /* 调整部门 */
      adjusDepVisible: false,
      /* 调整部门树 */
      adjusDepTreeData: [],
      adjusReplaceFields: { children: 'children', title: 'label', key: 'id', value: 'id' },
      /* 调整部门返回数据 */
      adjus_rData: []
      /* 单选 */
    }
  },
  methods: {
    async checkModuleAction(check) {
      if (!this.moduleActionGuard) {
        this.$message.warning(check.label + '暂不可用：成员模块未配置，请联系管理员。')
        return false
      }
      const allowed = await this.moduleActionGuard(check)
      await this.$nextTick()
      return allowed
    },
    /* 表格多选 */
    onSelectChange(selectedRowKeys, selectedRows) {
      this.selectedRowKeys = selectedRowKeys
      this.selectedRows = selectedRows
    },
    /* 搜索成员 */
    onSearch(val) {
      if (val) {
        this.UserListParams.nickName = val
      } else {
        delete this.UserListParams.nickName
      }
      this.$refs.table.refresh(true)
    },
    handleDeptFilterChange(value) {
      if (value) {
        this.UserListParams.deptId = value
      } else {
        delete this.UserListParams.deptId
      }
      this.$refs.table.refresh(true)
    },
    handleAddedDateChange(dates, dateStrings) {
      if (dates && dates.length === 2) {
        this.UserListParams.beginTime = dateStrings[0]
        this.UserListParams.endTime = dateStrings[1]
      } else {
        delete this.UserListParams.beginTime
        delete this.UserListParams.endTime
      }
      this.$refs.table.refresh(true)
    },
    loadFilterDepartments() {
      getTreeMenu().then(res => {
        this.treeData = res.data || []
      })
    },
    /* 修改部门名称 */
    editDepName(v) {
      if (v === 1) {
        if (!this.editNameVal) {
          return this.$message.error('输入要修改的名称')
        } else {
          editDeptName({ deptId: this.selectTreeNodeId, deptName: this.editNameVal })
            .then(res => {
              if (res.code === 200) {
                this.visible = false
                this.$emit('saveSuccess')
                this.updateUserTitle(this.editNameVal, this.selectTreeNodeId)
                return this.$message.success('修改成功')
              }
            })
            .catch(err => {
              this.visible = false
              return this.$message.error(err)
            })
        }
      } else {
        this.editNameVal = ''
        this.visible = false
      }
    },
    isDepartmentPrincipal(record) {
      return Number(record.deptPrincipal) === 1
    },
    setDepartmentPrincipal(record) {
      const deptId = Number(record.deptId || record.dept?.deptId || this.deptId)
      if (!deptId) {
        return this.$message.warning('请先选择具体部门')
      }
      this.$antdconfirm({
        title: '设置部门主管',
        content: '确定将“' + record.nickName + '”设为当前部门主管吗？原主管将自动取消。',
        onOk: () =>
          setDeptPrincipal(deptId, record.userId).then(res => {
            if (res.code === 200) {
              if (!this.verifyPrincipalResponse(res, deptId, record.userId, 1)) {
                return
              }
              this.$refs.table.refresh()
              return this.$message.success('部门主管设置成功')
            }
          })
      })
    },
    clearDepartmentPrincipal(record) {
      const deptId = Number(record.deptId || record.dept?.deptId || this.deptId)
      this.$antdconfirm({
        title: '取消部门主管',
        content: '确定取消“' + record.nickName + '”的部门主管身份吗？',
        onOk: () =>
          clearDeptPrincipal(deptId, record.userId).then(res => {
            if (res.code === 200) {
              if (!this.verifyPrincipalResponse(res, deptId, record.userId, 0)) {
                return
              }
              this.$refs.table.refresh()
              return this.$message.success('已取消部门主管')
            }
          })
      })
    },
    verifyPrincipalResponse(res, deptId, userId, principal) {
      const saved = res.data
      if (!saved || Number(saved.deptId) !== Number(deptId) || Number(saved.userId) !== Number(userId) || Number(saved.deptPrincipal) !== principal) {
        this.$message.error('未收到主管保存结果，请先重启后端服务后重试')
        return false
      }
      return true
    },
    /* 添加子部门 */
    addDepchild(v) {
      if (v === 1) {
        if (!this.addDepchildVal) {
          return this.$message.error('输入要添加的子部门')
        } else {
          addSystemDepchild({ parentId: this.selectTreeNodeId, deptName: this.addDepchildVal })
            .then(res => {
              if (res.code === 200) {
                this.DepVisible = false
                this.$emit('saveSuccess')
                return this.$message.success('添加成功')
              }
            })
            .catch(err => {
              this.DepVisible = false
              return this.$message.error(err)
            })
        }
      } else {
        this.addDepchildVal = ''
        this.DepVisible = false
      }
    },
    /* 删除部门 */
    deleteDept(isDelete) {
      if (isDelete) {
        if (this.selectTreeNodeId === null) {
          return this.$message.warning('请选择部门后删除')
        }
        delDept(this.selectTreeNodeId)
          .then(res => {
            if (res.code === 200) {
              this.DepVisible = false
              this.$emit('saveSuccess')
              return this.$message.success('删除成功')
            }
          })
          .catch(err => {
            this.DepVisible = false
            return this.$message.error(err)
          })
      } else {
        this.DepVisible = false
      }
    },
    updateUserTitle(deptName, deptId) {
      this.userTitle = deptName
    },
    /* 邀请成员 */
    inviteMembers() {
      this.isIMmodal = true
      /* 部门 */
      getTreeMenu().then(res => {
        this.treeData = res.data
      })
    },
    /* 邀请成员提交 */
    onSubmit() {
      this.$refs.ruleForm.validate(valid => {
        if (valid) {
          const params = { ...this.form, countryCode: this.countryCode, deptId: this.rData }
          this.submitConfiguredAction('invite', params, '邀请成员').then(res => {
            if (res && res.code === 200) {
              this.form = {}
              this.countryCode = 86
              this.isIMmodal = false
              this.$refs.table.refresh()
              return this.$message.success(this.actionSuccessMessage(res, '新增成功'))
            }
          })
        } else {
          return false
        }
      })
    },
    /* 重置表单 */
    resetForm() {
      this.$refs.ruleForm.resetFields()
    },
    /* 调整部门 */
    adjusting(v) {
      if (v === 1) {
        if (this.selectedRowKeys.length === 0) {
          return this.$message.warning('选择需要调整的部门成员')
        }
        this.submitConfiguredAction(
          'transfer',
          {
            deptId: this.adjus_rData,
            userIds: this.selectedRowKeys
          },
          '调整部门'
        ).then(res => {
          if (res && res.code === 200) {
            this.$message.success(this.actionSuccessMessage(res, '调整成功'))
            this.adjusDepVisible = false
            this.selectedRowKeys = []
            this.$refs.table.refresh()
          }
        })
      } else {
        this.adjusDepTreeData = []
        this.adjusDepVisible = false
      }
    },
    /* 超级管理员直接删除员工 */
    deleteMembers(record) {
      if (!this.canDeleteMembers) return
      const userIds = record ? [record.userId] : this.selectedRowKeys.slice()
      if (!userIds.length) return this.$message.warning('请选择需要删除的员工')
      if (userIds.some(id => Number(id) === 1)) {
        return this.$message.warning('固定超级管理员不能被删除')
      }
      const name = record ? '“' + record.nickName + '”' : '选中的 ' + userIds.length + ' 名员工'
      this.$antdconfirm({
        title: '删除员工',
        content: '确定删除' + name + '吗？删除后员工无法登录，也不会出现在在职或离职列表中，历史业务记录保留。',
        okText: '确认删除',
        okType: 'danger',
        onOk: () =>
          delUser(userIds.join(',')).then(res => {
            if (res.code === 200) {
              this.selectedRowKeys = []
              this.selectedRows = []
              this.$refs.table.refresh()
              this.$emit('saveSuccess')
              this.$message.success('员工删除成功')
            }
          })
      })
    },
    /* 转为离职 */
    mitosis() {
      if (this.selectedRowKeys.length === 0) {
        return this.$message.warning('选择需要转为离职的成员')
      }
      this.submitConfiguredAction('dismiss', { userIds: this.selectedRowKeys }, '转为离职').then(res => {
        if (res && res.code === 200) {
          this.selectedRowKeys = []
          this.$refs.table.refresh()
          return this.$message.success(this.actionSuccessMessage(res, '设置成功'))
        }
      })
    },
    submitConfiguredAction(actionCode, payload, label) {
      if (!this.configuredActionSubmitter) {
        this.$message.warning('成员操作暂不可用：所属模块未就绪，请刷新后重试')
        return Promise.resolve(null)
      }
      return this.configuredActionSubmitter(actionCode, payload, label)
    },
    actionSuccessMessage(response, completedMessage) {
      return response.data?.status === 'pendingApproval' ? '已提交审批，请等待审批人处理' : completedMessage
    },
    /* 再次邀请 */
    makeInvite(text, record, index) {},
    async readMemberImport(file) {
      this.importError = ''
      this.importRows = []
      this.importPreviewVisible = false
      try {
        if (file.size > 10 * 1024 * 1024) throw new Error('文件不能超过10MB')
        const schema = await getMemberTemplateSchema()
        const rows = parseMemberImport(await file.arrayBuffer(), schema)
        const response = await previewMemberImport(this.moduleMeta?.moduleId, rows)
        this.importRows = response.data.rows
        this.importFileName = file.name
        this.importPreviewVisible = true
      } catch (error) {
        this.importError = error.message || String(error)
      }
      return false
    },
    async submitMemberImport() {
      if (!this.importRows.length) return
      try {
        const response = await this.submitConfiguredAction('invite', { rows: this.importRows, memberImport: true, fileName: this.importFileName, title: '批量邀请成员（' + this.importRows.length + '人）' }, '批量邀请成员')
        if (response?.code !== 200) return
        this.importPreviewVisible = false
        this.isIMmodal = false
        this.importRows = []
        this.$refs.table.refresh()
        this.$message.success(this.actionSuccessMessage(response, '成员导入成功'))
      } catch (error) {
        this.importPreviewVisible = false
        this.importError = error.message || String(error)
      }
    },
    /* 下载模板 */
    async downloadTem() {
      try {
        await downloadFixedTemplate('members/address-book', '通讯录批量导入模板')
      } catch (error) {
        this.$message.error(error.message || '模板下载失败')
      }
    },
    // 查看图片
    async handlePreview(file) {
      previewDoc(file.name, file.url)
    },
    /* 复制链接 */
    copyInputVal() {
      this.$refs.inputRef.select()
      document.execCommand('Copy') // 执行浏览器复制命令
      return this.$message.success('复制成功')
    },
    /* 部门树选中返回的数据 */
    returnData(d) {
      this.rData = d
    },
    /* 初始化调整部门树 */
    initAdjusDepTreeData() {
      return getTreeMenu().then(res => {
        this.adjusDepTreeData = res.data
        this.adjusDepVisible = true
      })
    },
    /* 调整部门返回数据回调 */
    returnAdjusData(d) {
      this.adjus_rData = d
    },
    /* 编辑 */
    rowClick(record) {
      this.$createAntdDrawer({
        stopPropagation: true,
        drawerProps: {
          title: record.nickName,
          width: '25%',
          maskClosable: true
        },
        content: {
          template: Details,
          props: {
            row: record
          },
          on: {
            ok: () => {
              this.$refs.table.refresh(true)
            }
          }
        }
      })
    },
    customRow(record, index) {
      return {
        style: {
          cursor: 'pointer'
        },
        on: {
          click: e => this.rowClick(record)
        }
      }
    },
    resetListFilters() {
      this.filterDeptId = null
      this.addedDateRange = []
      delete this.UserListParams.nickName
      delete this.UserListParams.deptId
      delete this.UserListParams.beginTime
      delete this.UserListParams.endTime
    }
  },
  watch: {
    title(data) {
      if (data === '离职成员' || data === '全部成员') {
        this.userTitle = data
        this.$emit('clearDeptId') // 清除部门ID
        this.resetListFilters()
        this.$refs.table.refresh()
      } else {
        this.updateUserTitle(data, this.deptId)
      }
    },
    deptId(data) {
      if (this.title !== '全部成员' && this.title !== '离职成员') {
        this.UserListParams.deptId = data
        this.$refs.table.refresh()
      }
    },
    /* 树节点Data */
    treeNodeData: {
      handler(v) {
        if (!v || !v.eventKey) return
        this.selectTreeNodeId = v.eventKey
        this.editNameVal = v.title
      },
      immediate: true
    }
  },
  computed: {
    showQueryFilters() {
      return this.title === '全部成员' || this.title === '离职成员'
    },
    visibleColumns() {
      const columns = this.showRoleInfo ? this.columns : this.columns.filter(column => !column.roleRelated)
      if (!this.showDepartmentActions && !this.canDeleteMembers) {
        return columns
      }
      return columns.concat({
        title: '操作',
        key: 'deptAction',
        width: this.showDepartmentActions && this.canDeleteMembers ? 180 : 140,
        fixed: 'right',
        align: 'center',
        scopedSlots: { customRender: 'deptAction' }
      })
    },
    showDepartmentActions() {
      return Boolean(this.deptId) && this.title !== '全部成员' && this.title !== '离职成员' && this.canManageDepartment
    },
    tableScroll() {
      return this.showDepartmentActions || this.canDeleteMembers ? { x: 1230 } : {}
    },
    rowSelection() {
      if (!this.canManageMembers) {
        return null
      }
      return {
        selectedRowKeys: this.selectedRowKeys,
        onChange: this.onSelectChange,
        getCheckboxProps: record => ({ props: { disabled: Number(record.userId) === 1 } })
      }
    },
    canDeleteMembers() {
      return (this.$store.getters.roles || []).includes('admin') || (this.$store.getters.permissions || []).includes('*:*:*')
    },
    canManageMembers() {
      const permissions = this.$store.getters.permissions || []
      const meta = this.moduleMeta
      if (this.moduleActionVisible) return this.canDeleteMembers || this.moduleActionVisible('members', 'transfer') || this.moduleActionVisible('members', 'dismiss')
      return this.canDeleteMembers || hasModuleAction(permissions, meta, 'transfer') || hasModuleAction(permissions, meta, 'dismiss')
    },
    canManageDepartment() {
      if (this.readOnlyDepartments) return false
      if (this.moduleActionVisible) return this.moduleActionVisible('members', 'manage')
      const permissions = this.$store.getters.permissions || []
      return hasModuleAction(permissions, this.moduleMeta, 'manage')
    }
  },
  created() {},
  mounted() {
    this.userTitle = this.title
    this.loadFilterDepartments()
  }
}
</script>

<style lang="less" scoped>
@import './index.less';
.delete-member {
  color: #f5222d;
}
</style>
