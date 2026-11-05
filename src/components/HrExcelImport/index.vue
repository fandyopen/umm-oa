<template>
  <a-modal
    :visible="visible"
    :title="schema.name"
    :width="1000"
    :confirm-loading="busy"
    :ok-text="previewed ? '确认导入' : '校验并预览'"
    :ok-button-props="{ props: { disabled: !raw.length || !!errors.length } }"
    @ok="submit"
    @cancel="$emit('close')"
    destroy-on-close
  >
    <a-alert :message="schema.note" type="info" show-icon style="margin-bottom: 16px" />
    <div class="excel-tools">
      <a-button icon="download" @click="download">下载模板</a-button><a-upload :before-upload="read" :show-upload-list="false" accept=".xlsx,.xls,.csv"><a-button icon="upload">选择钉钉导出表或模板</a-button></a-upload
      ><span>{{ fileName }}</span>
    </div>
    <template v-if="matrix.length">
      <div class="excel-tools">
        <span>工作表</span
        ><a-select v-model="sheetName" style="width: 180px" @change="selectSheet"
          ><a-select-option v-for="name in sheetNames" :key="name">{{ name }}</a-select-option></a-select
        ><span>表头所在行</span><a-input-number v-model="headerRow" :min="1" :max="Math.min(matrix.length, 100)" @change="remap" />
      </div>
      <div class="excel-mapping">
        <div v-for="f in schema.fields" :key="f.key">
          <label>{{ f.label }}{{ f.required ? ' *' : '' }}</label
          ><a-select v-model="mapping[f.key]" allow-clear show-search style="width: 100%" @change="resetPreview"
            ><a-select-option v-for="(column, index) in headers" :key="index" :value="String(index)">{{ column || '未命名列' }}（{{ index + 1 }}）</a-select-option
            ><a-select-option v-if="f.type === 'number' && !['batchId', 'revision'].includes(f.key)" value="__ZERO__">该项目未启用，明确按0导入</a-select-option></a-select
          >
        </div>
      </div>
      <a-alert v-if="errors.length" type="error" :message="errors.slice(0, 8).join('；')" style="margin: 12px 0" />
      <a-alert v-if="previewed" type="success" :message="'校验通过，共 ' + rows.length + ' 行。请核对预览后确认导入。'" style="margin: 12px 0" />
      <a-table v-if="previewed" :columns="previewColumns" :data-source="rows" :row-key="(_, index) => index" :pagination="{ pageSize: 8 }" size="small" :scroll="{ x: 1200 }" />
      <div v-if="previewed && rows.some(row => row.changes && Object.keys(row.changes).length)" class="diff">
        <div v-for="(row, index) in rows" :key="index">
          <template v-if="row.changes && Object.keys(row.changes).length"
            ><strong>{{ row.employeeNo }} {{ row.employeeName }}</strong
            ><span v-for="(change, key) in row.changes" :key="key"> {{ fieldLabel(key) }}：{{ change.before }} → {{ change.after }}；</span></template
          >
        </div>
      </div>
    </template>
  </a-modal>
</template>
<script>
import * as XLSX from 'xlsx'
import { templates, suggestColumn, convertCell, exportTemplate } from '/src/views/enterprise/hr/templates.js'
export default {
  name: 'HrExcelImport',
  props: {
    visible: Boolean,
    type: { type: String, required: true },
    extra: { type: Object, default: () => ({}) },
    previewApi: { type: Function, default: null },
    commitApi: { type: Function, required: true },
    templateRows: { type: Array, default: () => [] }
  },
  data: () => ({ busy: false, matrix: [], sheetNames: [], sheetName: '', headerRow: 1, mapping: {}, raw: [], rows: [], errors: [], previewed: false, fileName: '', fileContent: '', book: null }),
  computed: {
    schema() {
      return templates[this.type]
    },
    headers() {
      return (this.matrix[this.headerRow - 1] || []).map(v => String(v || '').trim())
    },
    previewColumns() {
      return this.schema.fields.map(f => ({ title: f.label, dataIndex: f.key, width: 150, customRender: value => (value === undefined ? '—' : String(value)) }))
    }
  },
  methods: {
    fieldLabel(key) {
      return this.schema.fields.find(f => f.key === key)?.label || key
    },
    download() {
      exportTemplate(this.type, this.templateRows)
    },
    async read(file) {
      if (file.size > 10 * 1024 * 1024) {
        this.$message.warning('文件不能超过10MB')
        return false
      }
      try {
        const bytes = await file.arrayBuffer()
        this.book = XLSX.read(bytes, { type: 'array', raw: true })
        this.sheetNames = this.book.SheetNames.filter(name => name !== '填写说明')
        this.sheetName = this.sheetNames[0]
        this.fileName = file.name
        this.fileContent = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result).split(',')[1])
          reader.onerror = reject
          reader.readAsDataURL(file)
        })
        this.selectSheet()
      } catch (error) {
        this.$message.error('文件读取失败：' + error.message)
      }
      return false
    },
    selectSheet() {
      this.matrix = XLSX.utils.sheet_to_json(this.book.Sheets[this.sheetName], { header: 1, defval: '', raw: true })
      const found = this.matrix.slice(0, 30).findIndex(row => row.some(v => /员工编号|工号|钉钉人员ID/.test(String(v))))
      this.headerRow = found >= 0 ? found + 1 : 1
      this.remap()
    },
    remap() {
      this.mapping = Object.fromEntries(
        this.schema.fields.map(f => {
          const column = suggestColumn(this.headers, f)
          return [f.key, column ? String(this.headers.indexOf(column)) : '']
        })
      )
      this.raw = this.matrix.slice(this.headerRow).filter(row => row.some(v => v !== '' && v !== null))
      this.resetPreview()
    },
    resetPreview() {
      this.previewed = false
      this.errors = []
      this.rows = []
    },
    payload() {
      const rows = []
      const errors = []
      for (let i = 0; i < this.raw.length; i++) {
        const row = {}
        for (const f of this.schema.fields) {
          const column = this.mapping[f.key]
          const value = column === '__ZERO__' ? 0 : column === '' || column === undefined ? '' : this.raw[i][Number(column)]
          try {
            const converted = convertCell(value, f)
            if (converted === '' && f.required && !(f.key === 'employeeNo' && this.mapping.dingtalkUserId !== '')) errors.push('第' + (this.headerRow + i + 1) + '行缺少' + f.label)
            if (converted !== '') row[f.key] = converted
          } catch (error) {
            errors.push('第' + (this.headerRow + i + 1) + '行：' + error.message)
          }
        }
        rows.push(row)
      }
      if (!rows.length || rows.length > 5000) errors.push('每次导入1至5000行')
      this.errors = errors
      return { ...this.extra, rows, fileName: this.fileName, fileContent: this.fileContent }
    },
    async submit() {
      this.busy = true
      try {
        const payload = this.payload()
        if (this.errors.length) return
        if (!this.previewed) {
          const response = this.previewApi ? await this.previewApi(payload) : { data: { rows: payload.rows, errors: [] } }
          this.rows = response.data.rows || []
          this.errors = response.data.errors || []
          this.previewed = !this.errors.length
          return
        }
        await this.commitApi(payload)
        this.$message.success('导入成功')
        this.$emit('done')
        this.$emit('close')
      } catch (error) {
        this.$message.error(error.message || '校验或导入失败')
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
<style scoped>
.excel-tools {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.excel-mapping {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.excel-mapping label {
  display: block;
  color: #666;
  margin-bottom: 5px;
}
.diff {
  padding: 12px;
  background: #fffbe6;
  max-height: 180px;
  overflow: auto;
}
@media (max-width: 760px) {
  .excel-mapping {
    grid-template-columns: 1fr;
  }
}
</style>
