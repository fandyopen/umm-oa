<template>
  <a-drawer width="560px" :visible="open" @close="cancel">
    <a-divider orientation="left">
      <b>{{ formTitle }}</b>
    </a-divider>
    <a-form-model ref="form" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-model-item label="上级模块" prop="parentId">
        <a-tree-select
          v-model="form.parentId"
          class="tw-w-full"
          :dropdown-style="{ maxHeight: '400px', overflow: 'auto' }"
          :tree-data="parentOptions"
          placeholder="请选择上级模块"
          :replace-fields="{ children: 'children', title: 'moduleName', key: 'moduleId', value: 'moduleId' }"
          tree-default-expand-all
          allow-clear
        />
      </a-form-model-item>
      <a-form-model-item label="类型" prop="menuType">
        <a-radio-group v-model="form.menuType" button-style="solid">
          <a-radio-button value="M">目录</a-radio-button>
          <a-radio-button value="C">页面</a-radio-button>
        </a-radio-group>
      </a-form-model-item>
      <a-form-model-item label="名称" prop="moduleName">
        <a-input v-model="form.moduleName" placeholder="请输入模块名称" />
      </a-form-model-item>
      <a-form-model-item label="路由名称" prop="routeName">
        <a-input v-model="form.routeName" placeholder="例如 CustomerList" />
      </a-form-model-item>
      <a-form-model-item label="路由地址" prop="path">
        <a-input v-model="form.path" placeholder="例如 customers" />
      </a-form-model-item>
      <a-form-model-item v-if="form.menuType === 'C'" label="组件路径" prop="component">
        <a-input v-model="form.component" placeholder="例如 eos/crm/customer/index" />
      </a-form-model-item>
      <a-form-model-item label="权限标识" prop="perms">
        <a-input v-model="form.perms" placeholder="例如 crm:customer:list" />
      </a-form-model-item>
      <a-form-model-item label="图标" prop="icon">
        <a-input v-model="form.icon" placeholder="例如 iconfont-kehuguanli" />
      </a-form-model-item>
      <a-form-model-item label="排序" prop="orderNum">
        <a-input-number v-model="form.orderNum" :min="0" class="tw-w-full" />
      </a-form-model-item>
      <a-form-model-item label="显示" prop="visible">
        <a-radio-group v-model="form.visible" button-style="solid">
          <a-radio-button value="0">显示</a-radio-button>
          <a-radio-button value="1">隐藏</a-radio-button>
        </a-radio-group>
      </a-form-model-item>
      <a-form-model-item label="状态" prop="status">
        <a-radio-group v-model="form.status" button-style="solid">
          <a-radio-button value="0">启用</a-radio-button>
          <a-radio-button value="1">停用</a-radio-button>
        </a-radio-group>
      </a-form-model-item>
      <div class="module-form__footer">
        <a-space>
          <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
          <a-button @click="cancel">取消</a-button>
        </a-space>
      </div>
    </a-form-model>
  </a-drawer>
</template>

<script>
import { addMenu, getMenu, updateMenu } from '/src/api/system/menu.js'

export default {
  name: 'ModuleForm',
  props: {
    treeData: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      open: false,
      saving: false,
      formTitle: '新增模块',
      form: this.emptyForm(),
      rules: {
        moduleName: [{ required: true, message: '请输入模块名称', trigger: 'blur' }],
        routeName: [{ required: true, message: '请输入路由名称', trigger: 'blur' }],
        path: [{ required: true, message: '请输入路由地址', trigger: 'blur' }],
        component: [{ required: true, message: '请输入组件路径', trigger: 'blur' }]
      }
    }
  },
  computed: {
    parentOptions() {
      const convert = nodes =>
        (nodes || [])
          .filter(node => node.nodeType === 'module' && node.moduleId !== this.form.moduleId)
          .map(node => ({
            moduleId: node.moduleId,
            moduleName: node.moduleName,
            children: convert(node.children)
          }))
      return convert(this.treeData)
    }
  },
  methods: {
    emptyForm() {
      return {
        moduleId: undefined,
        parentId: 0,
        moduleName: '',
        routeName: '',
        path: '',
        component: '',
        perms: '',
        icon: 'iconfont-mokuai',
        orderNum: 0,
        menuType: 'C',
        hasFlow: 1,
        flowConditions: '',
        isFrame: '1',
        isCache: '0',
        isApp: '0',
        visible: '0',
        status: '0'
      }
    },
    handleAdd(parent) {
      this.form = this.emptyForm()
      this.form.parentId = parent && parent.moduleId ? parent.moduleId : 0
      this.form.menuType = parent ? 'C' : 'M'
      this.formTitle = parent ? `在“${parent.moduleName}”下新增` : '新增模块'
      this.open = true
    },
    async handleUpdate(row) {
      const response = await getMenu(row.moduleId)
      const module = response.data || {}
      this.form = {
        ...this.emptyForm(),
        ...module,
        moduleId: module.moduleId,
        parentId: Number(module.parentId || 0),
        moduleName: module.moduleName || module.menuName || '',
        routeName: module.routeName || module.name || '',
        menuType: module.menuType === 'M' ? 'M' : 'C',
        orderNum: Number(module.moduleSort ?? module.orderNum ?? 0),
        visible: String(module.visible ?? (Number(module.hidden) === 1 ? '1' : '0')),
        status: String(module.status || '0')
      }
      this.formTitle = '修改模块'
      this.open = true
    },
    cancel() {
      this.open = false
      this.$refs.form && this.$refs.form.clearValidate()
    },
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) {
          return
        }
        this.saving = true
        try {
          const data = {
            ...this.form,
            menuId: this.form.moduleId,
            component: this.form.menuType === 'M' ? (Number(this.form.parentId) === 0 ? 'Layout' : 'ParentView') : this.form.component,
            isFrame: this.form.isFrame || '1',
            isCache: this.form.isCache || '0',
            isApp: this.form.isApp || (this.form.menuType === 'M' ? '1' : '0')
          }
          if (this.form.moduleId) {
            await updateMenu(data)
            this.$message.success('模块修改成功')
          } else {
            await addMenu(data)
            this.$message.success('模块新增成功')
          }
          this.open = false
          this.$emit('ok')
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.module-form__footer {
  padding: 18px 0 0 24%;
}
</style>
