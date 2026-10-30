/* 通讯录 */
<template>
  <div>
    <!-- 在组织架构 角色 管理员都没有的时候弹窗提示没有权限 -->
    <a-modal :visible="optionList.length === 0" :closable="false">
      <template slot="title">
        <a-icon type="smile" theme="twoTone" />
        温馨提醒
      </template>
      <template slot="footer">
        <a-button type="primary" @click="goIndex"> 返回首页 </a-button>
      </template>
      <p style="font-size: 25px; margin: 0 auto; text-align: center">您没有相关权限,请联系管理员授权</p>
    </a-modal>
    <div class="contacts-internal" :class="{ 'menu-is-collapsed': menuCollapsed }">
      <div class="layout-menu">
        <button
          type="button"
          class="menu-collapse-trigger"
          :title="menuCollapsed ? '展开左侧栏' : '收起左侧栏'"
          :aria-label="menuCollapsed ? '展开左侧栏' : '收起左侧栏'"
          @click="toggleMenuCollapsed"
        >
          <a-icon :type="menuCollapsed ? 'right' : 'left'" />
        </button>
        <div class="menu-header">
          <!-- <a-radio-group button-style="solid" v-model="gvalue">
              <a-radio-button value="组织架构"> 组织架构 </a-radio-button>
              <a-radio-button value="角色"> 角色 </a-radio-button>
              <a-radio-button value="管理员"> 管理员 </a-radio-button>
            </a-radio-group> -->
          <a-radio-group button-style="solid" v-model="gvalue">
            <a-radio-button v-for="(item, index) in optionList" :key="index" :value="item">{{ item }}</a-radio-button>
          </a-radio-group>
        </div>
        <div class="menu-content" v-if="gvalue === '组织架构'">
          <div class="menu-type">成员</div>
          <div class="member-all">
            <a-menu class="member-menu" mode="inline" :selectedKeys="selectedKeys" @click="handleMemberMenu">
              <a-menu-item key="全部成员">
                <a-icon type="user" />
                全部成员
              </a-menu-item>
              <a-menu-item key="离职成员">
                <a-icon type="user" />
                离职成员
              </a-menu-item>
            </a-menu>
          </div>
          <div class="menu-type">部门</div>
          <div class="department-tree">
            <!-- <a-tree ref="treeRef" :tree-data="treeData" :selectedKeys="selectedTreeKeys" :replaceFields="replaceFields" @select="treeNodeSelect"> </a-tree> -->
            <a-tree ref="treeRef" :selectedKeys="selectedTreeKeys" :tree-data="treeData" :replaceFields="replaceFields" @dragenter="onDragEnter" @drop="onDrop" @select="treeNodeSelect"> </a-tree>
          </div>
        </div>
        <div class="menu-content" v-if="gvalue === '角色'">
          <div v-userPerms="{ bindingPerms: 'roleManage' }" class="menu-type">
            创建的角色
            <a-popover placement="bottom" v-model="popoverVisible" trigger="click">
              <template slot="content">
                <div class="operation">
                  <div class="operation-item" @click="createRoleGroup">创建角色组</div>
                  <div class="operation-item" @click="createRole">创建角色</div>
                </div>
              </template>
              <a-icon type="plus" />
            </a-popover>
            <!-- 创建角色组弹窗 -->
            <a-modal width="450px" :visible="crg_Visible" @ok="crg_handleOk" @cancel="crg_handleCancel">
              <p>角色组名称</p>
              <a-input placeholder="输入角色组名称" allowClear v-model="roleGroupName" />
            </a-modal>
            <!-- 创建角色弹窗 -->
            <a-modal width="450px" :visible="cr_Visible" @ok="cr_handleOk" @cancel="cr_handleCancel">
              <p>角色名称</p>
              <a-input placeholder="输入角色名称" allowClear v-model="roleName" />
              <p>目标分组</p>
              <a-select placeholder="选择角色组" allowClear style="width: 100%" @change="handleChange">
                <a-select-option v-for="item in RoleGroupMenu" :value="item.id" :key="item.id"> {{ item.value }} </a-select-option>
              </a-select>
            </a-modal>
            <!-- 修改角色名称弹窗 -->
            <a-modal title="修改角色名称" width="450px" :visible="editRoleVisible" @ok="editRoleHandleOk" @cancel="editRoleHandleCancel">
              <a-input placeholder="输入新的角色名称" allowClear v-model="editRoleName" @pressEnter="editRoleHandleOk" />
            </a-modal>
            <!-- 修改角色组名称弹窗 -->
            <a-modal title="修改角色组名称" width="450px" :visible="editRoleGroupVisible" @ok="editRoleGroupHandleOk" @cancel="editRoleGroupHandleCancel">
              <a-input placeholder="输入新的角色组名称" allowClear v-model="editRoleGroupName" @pressEnter="editRoleGroupHandleOk" />
            </a-modal>
          </div>
          <div class="roleMenu" v-if="RoleMenuTree.length > 0">
            <a-tree :defaultExpandedKeys="selectedKeysRole" @select="onSelectRole" :selectedKeys="selectedKeysRole">
              <a-tree-node :selectable="false" v-for="item in RoleMenuTree" :key="item.groupId">
                <span slot="title" class="role-tree-title role-tree-title-group">
                  <span class="role-tree-label" :title="item.groupName">{{ item.groupName }}</span>
                  <span class="role-tree-actions">
                    <a-icon v-userPerms="{ bindingPerms: 'roleManage' }" type="edit" title="修改角色组名称" @click.stop="openEditRoleGroup(item)" />
                    <a-icon v-userPerms="{ bindingPerms: 'roleManage' }" type="delete" title="删除角色组" @click.stop="deleteRoleGroup(item.groupId, item.groupName)" />
                  </span>
                </span>
                <a-tree-node :key="ritem.roleId" is-leaf v-for="ritem in item.roles">
                  <span slot="title" class="role-tree-title role-tree-title-role">
                    <span class="role-tree-label" :title="ritem.roleName">{{ ritem.roleName }}</span>
                    <span class="role-tree-actions">
                      <a-icon v-userPerms="{ bindingPerms: 'roleManage' }" type="edit" title="修改角色名称" @click.stop="openEditRole(ritem)" />
                      <a-icon v-userPerms="{ bindingPerms: 'roleManage' }" type="delete" title="删除角色" @click.stop="deleteRole(ritem.roleId, ritem.roleName)" />
                    </span>
                  </span>
                </a-tree-node>
              </a-tree-node>
            </a-tree>
          </div>
        </div>
        <div class="menu-content" v-if="gvalue === '管理员'">
          <div class="menu-type">超级管理员</div>
          <div class="member-all">
            <a-menu class="member-menu" mode="inline" :selectedKeys="selectedKeys_AdminMenu">
              <a-menu-item key="超级管理员" title="超级管理员">
                <a-icon type="user" />
                超级管理员
              </a-menu-item>
            </a-menu>
          </div>
        </div>
      </div>
      <!-- 成员 -->
      <div class="layout-content" v-if="gvalue === '组织架构'">
        <MemberElement v-if="selectedKeys.includes('全部成员') || selectedKeys.includes('离职成员') || deptId" :title="mTitle" :deptId="deptId" @clearDeptId="clearDeptId" :treeNodeData="treeNodeData" @saveSuccess="saveSuccess" />
      </div>
      <!-- 角色 -->
      <div class="layout-content" v-if="gvalue === '角色'">
        <RoleElement :title="rtitle" :roleMenuId="roleMenuId" @modifySuccess="modifySuccess" />
      </div>
      <!-- 管理员 -->
      <div class="layout-content" v-if="gvalue === '管理员'">
        <AdminElement :title="atitle" />
      </div>
    </div>
  </div>
</template>

<script>
import MemberElement from '../components/memberElement/index.vue'
import RoleElement from '../components/roleElement/index.vue'
import AdminElement from '../components/adminElement/index.vue'
import { getTreeMenu, deptsort, insertRole, updateRole, updateRoleGroup, getRoleByAdmin } from '/src/api/enterprise/index.js'
import { delSaasRole, delRoleGroup } from '/src/api/system/role.js'

export default {
  name: 'ContactsOp',
  components: { MemberElement, RoleElement, AdminElement },
  data() {
    return {
      // 岗位
      /* 岗位选中节点 */
      selectedPostTreeKeys: [],
      /* 岗位 */
      treePostData: [],
      /* 岗位 tree 字段 */
      replacePostFields: { title: 'postName', key: 'postId' },
      selectedPostKeys: ['全部成员'],
      /* 岗位ID */
      localPostId: null,
      /* 岗位树节点数据 */
      treeNodePostData: {},
      /* 左侧栏收起状态 */
      menuCollapsed: false,
      gvalue: '',
      optionList: ['组织架构', '角色', '管理员'],
      memberTitle: '',
      /* 部门 */
      treeData: [],
      /* tree 字段 */
      replaceFields: { children: 'children', title: 'label', key: 'id' },
      mTitle: '',
      /* 部门ID */
      deptId: null,
      selectedKeys: ['全部成员'],
      /* 部门选中节点 */
      selectedTreeKeys: [],
      /* 树节点数据 */
      treeNodeData: {},
      /* 创建角色组弹窗 */
      crg_Visible: false,
      /* 创建角色弹窗 */
      cr_Visible: false,
      /* 修改角色名称弹窗 */
      editRoleVisible: false,
      editRoleId: null,
      editRoleName: '',
      /* 修改角色组名称弹窗 */
      editRoleGroupVisible: false,
      editRoleGroupId: null,
      editRoleGroupName: '',
      /* 气泡弹窗 */
      popoverVisible: false,
      /* 角色组树菜单 */
      RoleMenuTree: [],
      /* 角色树选中 */
      selectedKeysRole: [],
      /* 新建角色组名称 */
      roleGroupName: null,
      /* 新建角色 */
      roleName: null,
      /* 角色组菜单 */
      RoleGroupMenu: [],
      rtitle: '',
      /* 选中角色ID */
      roleMenuId: null,
      /* 选中目标分组 */
      selectRoleGroup: null,
      selectedKeys_AdminMenu: ['超级管理员'],
      atitle: '超级管理员'
    }
  },
  methods: {
    toggleMenuCollapsed() {
      this.menuCollapsed = !this.menuCollapsed
      window.localStorage.setItem('enterprise-contacts-menu-collapsed', this.menuCollapsed ? '1' : '0')
    },
    handleOrgTab() {
      if (!this.isEmpty(this.localPostId)) {
        this.clearMemberSelectedKeys()
        this.clearDeptSelectedKeys()
      }
      if (!this.isEmpty(this.deptId)) {
        this.clearMemberSelectedKeys()
        this.clearPostSelectedKeys()
      }
    },
    // 返回首页
    goIndex() {
      // this.$router.push('/saas')
      this.$router.push('/index')
    },
    // 根据权限去掉相应模块 => 更新optionList里面的内容
    toshow() {
      const perms = this.$store.getters.userPermission || {}
      const globalPermissions = this.$store.getters.permissions || []
      const isSuperAdmin =
        (this.$store.getters.roles || []).includes('admin') ||
        globalPermissions.includes('*:*:*') ||
        perms.is_admin ||
        perms.is_owner
      const teamReadPerms = isSuperAdmin || perms.has_team_read_perms
      const roleReadPerms = isSuperAdmin || perms.has_role_read_perms
      // 先判断有没有管理员这个模块
      if (!isSuperAdmin) {
        this.optionList.splice(
          this.optionList.findIndex(item => item === '管理员'),
          1
        )
        if (!teamReadPerms) {
          this.optionList.splice(
            this.optionList.findIndex(item => item === '组织架构'),
            1
          )
          if (!roleReadPerms) {
            this.optionList.splice(
              this.optionList.findIndex(item => item === '角色'),
              1
            )
          }
        } else if (!roleReadPerms) {
          this.optionList.splice(
            this.optionList.findIndex(item => item === '角色'),
            1
          )
          if (!teamReadPerms) {
            this.optionList.splice(
              this.optionList.findIndex(item => item === '组织架构'),
              1
            )
          }
        }
      } else {
        if (!teamReadPerms) {
          this.optionList.splice(
            this.optionList.findIndex(item => item === '组织架构'),
            1
          )
          if (!roleReadPerms) {
            this.optionList.splice(
              this.optionList.findIndex(item => item === '角色'),
              1
            )
          }
        } else if (!roleReadPerms) {
          this.optionList.splice(
            this.optionList.findIndex(item => item === '角色'),
            1
          )
          if (!teamReadPerms) {
            this.optionList.splice(
              this.optionList.findIndex(item => item === '组织架构'),
              1
            )
          }
        }
      }
    },
    // 全部成员
    handleMemberMenu({ key }) {
      this.mTitle = key
      this.selectedKeys[0] = key
      this.selectedTreeKeys = []
      this.treeNodeData = {}
    },
    /* 点击树节点 */
    treeNodeSelect(expandedKeys, { selected, node }) {
      if (selected) {
        this.selectedTreeKeys = expandedKeys
        this.mTitle = node.title
        // 全部成员、离职成员
        this.clearMemberSelectedKeys()
        // 岗位树
        this.clearPostSelectedKeys()
        this.deptId = expandedKeys[0]
        /* 树节点 */
        this.treeNodeData = node
      }
    },
    /* 点击岗位树节点 */
    treeNodePostSelect(expandedKeys, { selected, node }) {
      if (selected) {
        this.selectedPostTreeKeys = expandedKeys
        this.mTitle = node.title
        // 全部成员、离职成员
        this.clearMemberSelectedKeys()
        // 部门树
        this.clearDeptSelectedKeys()
        this.localPostId = expandedKeys[0]
        /* 岗位树节点 */
        this.treeNodePostData = node
      }
    },
    // 清理全部成员、离职成员
    clearMemberSelectedKeys() {
      // 全部成员、离职成员
      this.selectedKeys = ['']
    },
    // 清理部门树选择
    clearDeptSelectedKeys() {
      // 部门树
      this.selectedTreeKeys = ['']
      // 清空部门树id
      this.deptId = null
    },
    // 清理岗位树选择
    clearPostSelectedKeys() {
      // 岗位树
      this.selectedPostTreeKeys = ['']
      // 清空岗位树id
      this.localPostId = null
    },
    /* 清除部门ID */
    clearDeptId() {
      this.deptId = null
    },
    onDragEnter(info) {
      // expandedKeys 需要受控时设置
      // this.expandedKeys = info.expandedKeys
    },
    onDrop(info) {
      const dropKey = info.node.dataRef.id
      const dragKey = info.dragNode.dataRef.id
      const dropPos = info.node.pos.split('-')
      const dropPosition = info.dropPosition - Number(dropPos[dropPos.length - 1])
      const loop = (data, key, callback) => {
        data.forEach((item, index, arr) => {
          if (item.id === key) {
            return callback(item, index, arr)
          }
          if (item.children) {
            return loop(item.children, key, callback)
          }
        })
      }
      const data = [...this.treeData]
      // 拖拽
      if (info.dropPosition === -1) {
        deptsort({ target: dragKey })
      } else {
        deptsort({ before: dropKey, target: dragKey })
      }
      // Find dragObject
      let dragObj
      loop(data, dragKey, (item, index, arr) => {
        arr.splice(index, 1)
        dragObj = item
      })
      if (!info.dropToGap) {
        // Drop on the content
        loop(data, dropKey, item => {
          item.children = item.children || []
          // where to insert 示例添加到尾部，可以是随意位置
          item.children.push(dragObj)
        })
      } else if (
        (info.node.children || []).length > 0 && // Has children
        info.node.expanded && // Is expanded
        dropPosition === 1 // On the bottom gap
      ) {
        loop(data, dropKey, item => {
          item.children = item.children || []
          // where to insert 示例添加到尾部，可以是随意位置
          item.children.unshift(dragObj)
        })
      } else {
        let ar
        let i
        loop(data, dropKey, (item, index, arr) => {
          ar = arr
          i = index
        })
        if (dropPosition === -1) {
          ar.splice(i, 0, dragObj)
        } else {
          ar.splice(i + 1, 0, dragObj)
        }
      }
      this.treeData = data
    },
    /* 请求部门菜单 */
    initTreeMenuList(refresh = false) {
      /* 部门 */
      getTreeMenu().then(res => {
        this.treeData = res.data
      })
      if (!refresh && this.gvalue === '组织架构') {
        this.handleMemberMenu({ key: '全部成员' })
      }
    },
    /* 添加子部门成功回调 */
    saveSuccess() {
      this.initTreeMenuList(true)
    },
    /* 新增角色组 */
    createRoleGroup() {
      this.crg_Visible = true
      this.popoverVisible = false
      this.roleGroupName = null
    },
    /* 新增角色 */
    createRole() {
      this.cr_Visible = true
      this.popoverVisible = false
      this.roleName = null
      this.selectRoleGroup = null
      getRoleByAdmin().then(res => {
        this.RoleGroupMenu = res.data.roleGroups.map(({ groupId, groupName }) => ({ id: groupId, value: groupName }))
      })
    },
    /* 角色树菜单选中回调 */
    onSelectRole(keys, { selected, node }) {
      if (!selected || keys.length === 0) {
        return
      }
      this.selectedKeysRole = keys
      this.rtitle = node.title
      this.roleMenuId = keys[0]
    },
    /* 打开角色名称修改 */
    openEditRole(role) {
      this.editRoleId = role.roleId
      this.editRoleName = role.roleName
      this.editRoleVisible = true
    },
    /* 提交角色名称修改 */
    editRoleHandleOk() {
      const roleName = (this.editRoleName || '').trim()
      if (!roleName) {
        return this.$message.warning('角色名称不能为空')
      }
      updateRole({ roleId: this.editRoleId, roleName }).then(res => {
        if (res.code === 200) {
          if (this.roleMenuId === this.editRoleId) {
            this.rtitle = roleName
          }
          this.editRoleVisible = false
          this.initRoleList(this.editRoleId)
          this.editRoleName = ''
          return this.$message.success('角色名称修改成功')
        }
      })
    },
    editRoleHandleCancel() {
      this.editRoleVisible = false
      this.editRoleId = null
      this.editRoleName = ''
    },
    /* 打开角色组名称修改 */
    openEditRoleGroup(group) {
      this.editRoleGroupId = group.groupId
      this.editRoleGroupName = group.groupName
      this.editRoleGroupVisible = true
    },
    /* 提交角色组名称修改 */
    editRoleGroupHandleOk() {
      const groupName = (this.editRoleGroupName || '').trim()
      if (!groupName) {
        return this.$message.warning('角色组名称不能为空')
      }
      updateRoleGroup({ groupId: this.editRoleGroupId, groupName }).then(res => {
        if (res.code === 200) {
          this.editRoleGroupVisible = false
          this.initRoleList(this.roleMenuId)
          this.editRoleGroupName = ''
          return this.$message.success('角色组名称修改成功')
        }
      })
    },
    editRoleGroupHandleCancel() {
      this.editRoleGroupVisible = false
      this.editRoleGroupId = null
      this.editRoleGroupName = ''
    },
    /* 删除角色 */
    deleteRole(id, name) {
      this.$antdconfirm({
        title: '删除确认',
        content: '是否删除：' + name,
        onOk: () => {
          if (id === null) {
            return this.$message.warning('请选择角色')
          }
          delSaasRole(id).then(res => {
            if (res.code === 200) {
              this.initRoleList()
              return this.$message.success('删除成功')
            }
          })
        }
      })
    },
    /* 删除角色组 */
    deleteRoleGroup(id, name) {
      this.$antdconfirm({
        title: '删除确认',
        content: '是否删除：' + name,
        onOk: () => {
          if (id === null) {
            return this.$message.warning('请选择角色组')
          }
          delRoleGroup(id).then(res => {
            if (res.code === 200) {
              this.initRoleList()
              return this.$message.success('删除成功')
            }
          })
        }
      })
    },
    /* 查询角色组和角色 */
    initRoleList(preferredRoleId = null) {
      getRoleByAdmin().then(res => {
        this.RoleMenuTree = res.data.roleGroups
        const roles = this.RoleMenuTree.reduce((items, group) => items.concat(group.roles || []), [])
        const selectedRole = roles.find(role => role.roleId === preferredRoleId) || roles[0]
        this.selectedKeysRole = selectedRole ? [selectedRole.roleId] : []
        this.rtitle = selectedRole ? selectedRole.roleName : ''
        this.roleMenuId = selectedRole ? selectedRole.roleId : null
      })
    },
    /* 创建角色组提交 */
    crg_handleOk() {
      if (this.isEmpty(this.roleGroupName)) {
        return this.$message.warning('角色组名称不能为空')
      } else {
        updateRoleGroup({ groupName: this.roleGroupName }).then(res => {
          if (res.code === 200) {
            this.initRoleList()
            this.crg_Visible = false
            return this.$message.success('新增成功')
          }
        })
      }
    },
    /* 关闭 */
    crg_handleCancel() {
      this.crg_Visible = false
    },
    /* 创建角色提交 */
    cr_handleOk() {
      if (!this.isEmpty(this.roleName) && !this.isEmpty(this.selectRoleGroup)) {
        insertRole({ roleName: this.roleName, groupId: this.selectRoleGroup }).then(res => {
          if (res.code === 200) {
            this.initRoleList(res.data)
            this.cr_Visible = false
            this.roleName = null
            this.selectRoleGroup = null
            return this.$message.success('新增成功')
          }
        })
      } else {
        return this.$message.warning('角色和分组不能为空')
      }
    },
    /* 修改角色名称回调 */
    modifySuccess() {
      this.initRoleList()
    },
    /* 目标分组回调 */
    handleChange(value) {
      this.selectRoleGroup = value
    },
    /* 关闭 */
    cr_handleCancel() {
      this.cr_Visible = false
      this.selectRoleGroup = null
    }
  },
  created() {
    this.menuCollapsed = window.localStorage.getItem('enterprise-contacts-menu-collapsed') === '1'
    this.toshow()
    this.gvalue = this.optionList[0]
    this.initTreeMenuList()
  },
  watch: {
    gvalue(data) {
      if (data === '组织架构') {
        this.handleOrgTab()
        this.initTreeMenuList(true)
      }
      if (data === '角色') {
        this.initRoleList()
      }
      if (data === '管理员') {
        /* 默认选中固定超级管理员 */
        this.selectedKeys_AdminMenu = ['超级管理员']
        this.atitle = '超级管理员'
      }
    }
  },
  beforeDestroy() {
    this.clearMemberSelectedKeys()
    this.clearDeptSelectedKeys()
    this.clearPostSelectedKeys()
  }
}
</script>

<style lang="less" scoped>
@import './index.less';
.role-tree-title {
  position: relative;
  display: block;
  width: 100%;
  height: 32px;
  padding-right: 54px;
  line-height: 32px;
  min-width: 0;
  box-sizing: border-box;

  .role-tree-label {
    display: block;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .role-tree-actions {
    position: absolute;
    top: 0;
    right: 2px;
    bottom: 0;
    display: inline-flex;
    align-items: center;
    gap: 12px;
    margin: 0;
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transition: opacity 0.15s ease;

    i {
      width: 18px;
      line-height: 32px;
      text-align: center;
    }

    i:hover {
      color: #1890ff;
    }
  }

  &:hover .role-tree-actions {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
  }
}

.roleMenu {
  overflow-x: hidden;

  ::v-deep .ant-tree,
  ::v-deep .ant-tree li {
    width: 100%;
    box-sizing: border-box;
  }

  ::v-deep .ant-tree li .ant-tree-node-content-wrapper {
    width: calc(100% - 28px);
    height: 32px;
    padding: 0 6px;
    line-height: 32px;
    overflow: hidden;
    vertical-align: top;
    box-sizing: border-box;
  }

  ::v-deep .ant-tree-title {
    display: block;
    width: 100%;
  }
}
</style>
