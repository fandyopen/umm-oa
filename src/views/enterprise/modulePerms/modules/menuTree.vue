<!-- 菜单树 -->
<template>
  <a-drawer width="42%" :label-col="4" :wrapper-col="14" :visible="open" @close="onClose">
    <a-divider orientation="left">
      <b>{{ formTitle }}</b>
    </a-divider>
    <a-form-model ref="form" :model="form">
      <a-form-model-item label="模块权限">
        <a-alert class="tw-mb-3" type="info" show-icon message="页面权限与按钮权限分别授权，以各项勾选为准。父子联动仅在点击时批量选择，不会自动授权新增按钮。" />
        <a-checkbox @change="handleCheckedTreeExpand($event)"> 展开/折叠 </a-checkbox>
        <a-checkbox @change="handleCheckedTreeNodeAll($event)"> 全选/全不选 </a-checkbox>
        <a-checkbox @change="handleCheckedTreeConnect($event)" :checked="form.menuCheckStrictly"> 父子联动 </a-checkbox>
        <a-tree
          :checked-keys="treeCheckedKeys"
          checkable
          checkStrictly
          :expanded-keys="menuExpandedKeys"
          :auto-expand-parent="autoExpandParent"
          :tree-data="menuOptions"
          @check="onCheck"
          @expand="onExpandMenu"
          :replaceFields="defaultProps"
        />
      </a-form-model-item>
      <div class="bottom-control">
        <a-space>
          <a-button type="primary" @click="submitForm"> 保存 </a-button>
          <a-button type="dashed" @click="cancel"> 取消 </a-button>
        </a-space>
      </div>
    </a-form-model>
  </a-drawer>
</template>

<script>
import { addMenuPerm } from '/src/api/enterprise/index.js'
import { roleMenuTree } from '/src/api/system/menu.js'
import { allPermissionKeys, changePermissionSelection, permissionTreeState } from '/src/utils/modulePermissionTree.js'

export default {
  name: 'MenuTree',
  data() {
    return {
      loading: false,
      menuExpandedKeys: [],
      autoExpandParent: false,
      menuCheckedKeys: [],
      menuOptions: [],
      formTitle: '',
      // 表单参数
      form: {
        roleId: undefined,
        tenantModuleIds: [],
        menuCheckStrictly: true
      },
      open: false,
      menuExpand: false,
      menuNodeAll: false,
      defaultProps: {
        children: 'children',
        title: 'label',
        key: 'id'
      }
    }
  },
  computed: {
    treeCheckedKeys() {
      return permissionTreeState(this.menuOptions, this.menuCheckedKeys)
    }
  },
  methods: {
    onExpandMenu(expandedKeys) {
      this.menuExpandedKeys = expandedKeys
      this.autoExpandParent = false
    },
    // 所有菜单节点数据
    getMenuAllCheckedKeys() {
      // Half-checked ancestors are display state, not additional grants.
      return permissionTreeState(this.menuOptions, this.menuCheckedKeys).checked
    },
    handleCheckedTreeNodeAll(value) {
      if (value.target.checked) {
        this.menuCheckedKeys = allPermissionKeys(this.menuOptions)
      } else {
        this.menuCheckedKeys = []
      }
    },
    handleCheckedTreeExpand(value) {
      if (value.target.checked) {
        const treeList = this.menuOptions
        for (let i = 0; i < treeList.length; i++) {
          this.menuExpandedKeys.push(treeList[i].id)
        }
      } else {
        this.menuExpandedKeys = []
      }
    },
    // 树权限（父子联动）
    handleCheckedTreeConnect(value) {
      this.form.menuCheckStrictly = value.target.checked
    },
    /** 根据角色ID查询菜单树结构 */
    getRoleMenuTreeselect(roleId) {
      return roleMenuTree({ roleId: roleId, tenantAppId: this.$store.getters.tenantAppId }).then(response => {
        this.menuOptions = response.menus
        return response
      })
    },
    onCheck(checkedKeys, info) {
      this.menuCheckedKeys = changePermissionSelection(
        this.menuOptions, this.menuCheckedKeys, info.node.eventKey,
        info.checked, this.form.menuCheckStrictly
      )
    },
    onClose() {
      this.open = false
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      if (this.$refs.menu !== undefined) {
        this.menuCheckedKeys = []
      }
      this.menuExpand = false
      this.menuNodeAll = false
      this.menuExpandedKeys = []
      this.autoExpandParent = false
      this.menuCheckedKeys = []
      this.form = {
        roleId: undefined,
        tenantModuleIds: [],
        menuCheckStrictly: true
      }
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      this.form.roleId = row.roleId
      this.formTitle = '角色模块'
      return this.getRoleMenuTreeselect(row.roleId).then(res => {
        this.menuCheckedKeys = permissionTreeState(this.menuOptions, res.checkedKeys).checked
        this.open = true
      })
    },
    /** 提交按钮 */
    submitForm: function () {
      this.$refs.form.validate(valid => {
        if (valid) {
          this.form.tenantModuleIds = this.getMenuAllCheckedKeys()
          addMenuPerm(this.form).then(response => {
            this.$message.success('修改成功', 3)
            this.open = false
            this.$emit('ok')
          })
        }
      })
    }
  }
}
</script>
