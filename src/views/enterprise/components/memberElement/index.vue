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
      <div class="depart-operate" v-if="deptId && title !== '全部成员' && title !== '离职成员'">
        <a-popover trigger="click" v-model="visible">
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
        <a-divider type="vertical" />
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
        <a-popover trigger="click">
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
        <a-button ghost type="primary" @click="inviteMembers" v-hasModuleAction="'invite'"> 邀请成员 </a-button>
        <a-popover placement="bottom" trigger="click" v-model="adjusDepVisible">
          <template slot="content">
            <div class="setDepTree">
              <SelectDepartmentElement :multiple="false" :treeData="adjusDepTreeData" :replaceFields="adjusReplaceFields" @returnData="returnAdjusData" />
            </div>
            <div class="editNameBtn">
              <a-button @click="adjusting(0)"> 取消 </a-button>
              <a-button type="primary" @click="adjusting(1)"> 确认 </a-button>
            </div>
          </template>
          <a-button ghost type="primary" @click="initAdjusDepTreeData" v-hasModuleAction="'transfer'">调整部门</a-button>
        </a-popover>
        <a-button ghost type="primary" @click="mitosis" v-hasModuleAction="'dismiss'"> 转为离职 </a-button>
      </div>
      <div class="member-filter-pane" v-if="showQueryFilters">
        <a-tree-select
          v-model="filterDeptId"
          :tree-data="treeData"
          :replace-fields="deptFilterFields"
          placeholder="全部部门"
          allow-clear
          show-search
          tree-default-expand-all
          style="width: 190px"
          @change="handleDeptFilterChange"
        />
        <a-range-picker
          v-model="addedDateRange"
          format="YYYY-MM-DD"
          :placeholder="['新增开始日期', '新增结束日期']"
          style="width: 260px"
          @change="handleAddedDateChange"
        />
      </div>
      <div class="earch-input">
        <a-input-search placeholder="搜索成员" size="small" style="width: 200px" allow-clear @search="onSearch" />
      </div>
    </div>
    <div class="member-table-content">
      <!-- <div class="member-prompt-container">
        <div class="member-prompt-item">
          <span>有<span class="prompt-num">0</span>名成员未加入</span>
          <span class="invite-member-action">全部再次邀请</span>
        </div>
      </div> -->
      <div class="member-table">
        <!-- 数据表格 -->
        <s-table ref="table" size="default" rowKey="userId" :columns="visibleColumns" :data="loadData" :rowSelection="rowSelection" :customRow="customRow" :scroll="tableScroll">
          <span slot="deptAction" slot-scope="text, record" class="dept-action" @click.stop>
            <a-button v-if="isDepartmentPrincipal(record)" type="link" class="cancel-principal" @click.stop="clearDepartmentPrincipal(record)">取消主管</a-button>
            <a-button v-else type="link" @click.stop="setDepartmentPrincipal(record)">设为主管</a-button>
          </span>
        </s-table>
      </div>
    </div>
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
              <!-- <a-form-model-item label="编号" prop="numberID">
                <a-input v-model="form.numberID" />
              </a-form-model-item> -->
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
            <a-button type="primary" @click="onSubmit"> 邀请 </a-button>
            <a-button @click="resetForm">清空</a-button>
          </div>
        </div>
        <div v-if="current[0] === '批量导入成员'">
          <div class="manual-tip">
            <li class="tip-item">请下载<span class="Download_tem" @click="downloadTem">通讯录模板</span>，按格式修改后导入</li>
            <li class="tip-item">每次最多导入200名成员,已经在通讯录内或信息有误的成员不导入</li>
          </div>
          <div class="upload-wrapper">
            <SUpload :uploadData="uploadData" btn="批量导入成员" />
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
import { STable, SUpload } from '/src/components/index.js'
import downloadUtil from '/src/utils/common/download.js'
import { previewDoc } from '/src/components/Office/office.js'
import {
  getUserList,
  getDismissList,
  editDeptName,
  getTreeMenu,
  addSystemDepchild,
  setDeptPrincipal,
  clearDeptPrincipal
} from '/src/api/enterprise/index.js'
import { executeWorkflowAction } from '/src/api/workflow/action.js'
import { delDept } from '/src/api/system/dept.js'
import { excelImport } from '/src/api/system/user.js'
import SelectDepartmentElement from '../selectDepartmentElement/index.vue'
import countryCodeSelector from '/src/components/CountryCodeSelector/index.vue'
import Details from './details.vue'
import { hasModuleAction } from '/src/utils/modulePermission.js'

export default {
  components: { STable, SelectDepartmentElement, SUpload, countryCodeSelector },
  props: {
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
      default: () => {}
    },
    showRoleInfo: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      selectedRowKeys: [],
      selectedRows: [],
      /* 查询参数 */
      UserListParams: {
        orderByColumn: 'create_time',
        isAsc: 'desc'
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
          customRender: text => Number(text) === 1 ? '部门主管' : '成员'
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
      /* 上传 */
      uploadData: {
        compProps: {
          props: {
            multiple: false,
            // name只能叫file Excelfile
            name: 'file',
            accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel',
            // 上传之前回调，判断上传类型
            beforeUpload: file => {
              return new Promise((resolve, reject) => {
                if (file.type !== 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' && file.type !== 'sheet,application/vnd.ms-excel') {
                  this.$message.warning('请选择xlsx类型文件')
                  return reject
                }
                return resolve()
              })
            },
            remove: file => {}
          },
          on: {
            // 上传文件改变时的状态
            change: ({ file, fileList, event }) => {
              if (file.status === 'done') {
                // 导入成员
                const url = file.response.url
                excelImport({ url: url }).then(res => {
                  if (res.code === 200) {
                    this.isIMmodal = false
                    this.$message.success('导入成功')
                    this.$refs.table.refresh()
                  }
                })
              }
            },
            // 点击图片预览回调
            preview: this.handlePreview
          }
        }
      },
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
        onOk: () => setDeptPrincipal(deptId, record.userId).then(res => {
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
        onOk: () => clearDeptPrincipal(deptId, record.userId).then(res => {
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
      if (!saved
        || Number(saved.deptId) !== Number(deptId)
        || Number(saved.userId) !== Number(userId)
        || Number(saved.deptPrincipal) !== principal) {
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
          this.executeAction('invite', params).then(res => {
            if (res.code === 200) {
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
        this.executeAction('transfer', {
          deptId: this.adjus_rData,
          userIds: this.selectedRowKeys
        }).then(res => {
          if (res.code === 200) {
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
    /* 转为离职 */
    mitosis() {
      if (this.selectedRowKeys.length === 0) {
        return this.$message.warning('选择需要转为离职的成员')
      }
      this.executeAction('dismiss', { userIds: this.selectedRowKeys }).then(res => {
        if (res.code === 200) {
          this.selectedRowKeys = []
          this.$refs.table.refresh()
          return this.$message.success(this.actionSuccessMessage(res, '设置成功'))
        }
      })
    },
    executeAction(actionCode, payload) {
      return executeWorkflowAction({
        moduleId: Number(this.$route.meta.moduleId),
        actionCode,
        payload
      })
    },
    actionSuccessMessage(response, completedMessage) {
      return response.data?.status === 'pendingApproval'
        ? '已提交审批，请等待审批人处理'
        : completedMessage
    },
    /* 再次邀请 */
    makeInvite(text, record, index) {},
    /* 下载模板 */
    downloadTem() {
      downloadUtil.resource('/profile/template/通讯录批量导入模板.xlsx')
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
      getTreeMenu().then(res => {
        this.adjusDepTreeData = res.data
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
      const columns = this.showRoleInfo
        ? this.columns
        : this.columns.filter(column => !column.roleRelated)
      if (!this.showDepartmentActions) {
        return columns
      }
      return columns.concat({
        title: '操作',
        key: 'deptAction',
        width: 110,
        fixed: 'right',
        align: 'center',
        scopedSlots: { customRender: 'deptAction' }
      })
    },
    showDepartmentActions() {
      return Boolean(this.deptId)
        && this.title !== '全部成员'
        && this.title !== '离职成员'
        && this.canManageDepartment
    },
    tableScroll() {
      return this.showDepartmentActions ? { x: 1160 } : {}
    },
    rowSelection() {
      if (!this.canManageMembers) {
        return null
      }
      return {
        selectedRowKeys: this.selectedRowKeys,
        onChange: this.onSelectChange
      }
    },
    canManageMembers() {
      const permissions = this.$store.getters.permissions || []
      const meta = this.$route.meta
      return hasModuleAction(permissions, meta, 'transfer')
        || hasModuleAction(permissions, meta, 'dismiss')
    },
    canManageDepartment() {
      const permissions = this.$store.getters.permissions || []
      return hasModuleAction(permissions, this.$route.meta, 'manage')
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
</style>
