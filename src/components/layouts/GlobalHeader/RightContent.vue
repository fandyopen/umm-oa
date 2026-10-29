<template>
  <div class="header-right-box" :class="wrpCls">
    <div v-if="layout === 'sidemenu' && showFirstLevelMenu" class="first-level-menu" :class="{ 'menu-line-height': !menuIconHorizontal }">
      <a-menu v-model="current" mode="horizontal">
        <a-menu-item v-for="item in rightContentMenus" :key="item.meta.moduleId || item.name" @click="changeCurrentFirstLevelMenu(item)">
          <IconFont :type="item.meta.icon !== '#' ? item.meta.icon : 'iconfont-caidanguanli'" :class="{ 'icon-upon': !menuIconHorizontal }" />
          <span :class="{ 'menu-name-upon': !menuIconHorizontal }">{{ item.meta.title }}</span>
        </a-menu-item>
      </a-menu>
    </div>
    <div class="header-right-content" :class="{ 'header-msg-bkc': layout !== 'sidemenu' }">
      <a-tooltip v-if="canOpenDocs" :title="docsTitle">
        <a class="docs-entry" @click="openDocs"><a-icon type="book" /><span v-if="!isMobile">{{ docsTitle }}</span></a>
      </a-tooltip>
      <a-badge :count="todoTotal" class="badge" @click="gotodo"><a-icon type="bell" class="icon" /></a-badge>
      <div class="selectBox">
        <a-dropdown placement="bottomCenter" :trigger="['click']" @visibleChange="handleCompanyDropdown">
          <a class="ant-dropdown-link" @click="e => e.preventDefault()">
            <Ellipsis :length="12" tooltip>{{ defaultCompany || '当前企业' }}</Ellipsis><a-icon type="down" />
          </a>
          <a-menu slot="overlay">
            <a-menu-item v-if="companyLoading" disabled>加载中...</a-menu-item>
            <a-menu-item v-for="item in items" :key="item.tenantId || item.id" @click="handleChange(item.tenantId || item.id)">
              <Ellipsis :length="12" tooltip>{{ item.name }}</Ellipsis>
            </a-menu-item>
            <a-menu-item><div class="addItem" @click="addItem"><a-icon type="plus" class="addicon" />新建企业</div></a-menu-item>
          </a-menu>
        </a-dropdown>
      </div>
      <avatar-dropdown :menu="showMenu" :current-user="currentUser" :class="prefixCls" />
    </div>
    <a-modal :visible="visible" @ok="handleOk" @cancel="handleCancel">
      <div slot="title"><a-icon type="usergroup-add" style="margin-right: 20px" /><span>新建企业</span></div>
      <a-textarea v-model="EnterpriseName" placeholder="请输入企业名称" :autoSize="{ minRows: 2, maxRows: 6 }" />
    </a-modal>
  </div>
</template>

<script>
import AvatarDropdown from './AvatarDropdown.vue'
import Ellipsis from '/src/components/Ellipsis/Ellipsis.vue'
import { mapState, mapGetters, mapActions } from 'vuex'
import { IconFont } from '/src/components/IconFont/index.js'

export default {
  name: 'RightContent',
  components: { Ellipsis, AvatarDropdown, IconFont },
  props: {
    prefixCls: { type: String, default: 'ant-pro-global-header-index-action' },
    isMobile: { type: Boolean, default: false },
    topMenu: { type: Boolean, required: true },
    theme: { type: String, required: true }
  },
  data() {
    return {
      showMenu: true,
      currentUser: {},
      items: [],
      visible: false,
      EnterpriseName: '',
      current: ['mail'],
      companyLoaded: false,
      companyLoading: false
    }
  },
  computed: {
    ...mapGetters(['todoTotal', 'addRouters', 'layout', 'permissions', 'roles', 'defaultCompany', 'docsEnabled', 'docsTitle', 'nickname']),
    ...mapState({
      showFirstLevelMenu: state => state.app.showFirstLevelMenu,
      menuIconHorizontal: state => state.app.menuIconHorizontal
    }),
    wrpCls() {
      return {
        'ant-pro-global-header-index-right': true,
        'ant-pro-global-header-index-left': false,
        [`ant-pro-global-header-index-${this.isMobile || !this.topMenu ? 'light' : this.theme}`]: true,
        'self-info-flex': !this.showFirstLevelMenu
      }
    },
    rightContentMenus() {
      const root = this.addRouters[0]
      return ((root && root.children) || []).filter(item => item.hidden !== true)
    },
    canOpenDocs() {
      return this.docsEnabled && (
        this.roles.includes('admin') ||
        this.permissions.includes('*:*:*') ||
        this.permissions.includes('system:docs:list')
      )
    }
  },
  mounted() {
    this.currentUser = { name: this.nickname || 'Seal' }
  },
  methods: {
    ...mapActions(['_enterprise', '_addEnterprise', '_toggleEnterprise', 'SetCurrentLevelOneMenu']),
    gotodo() { this.$router.push('/todoItem/todo') },
    openDocs() { this.$router.push('/system/docs').catch(() => {}) },
    handleCompanyDropdown(visible) {
      if (!visible || this.companyLoaded || this.companyLoading) return
      this.companyLoading = true
      this._enterprise()
        .then(res => {
          this.items = res.data || []
          this.companyLoaded = true
        })
        .finally(() => { this.companyLoading = false })
    },
    addItem() { this.visible = true },
    handleOk() {
      this._addEnterprise({ name: this.EnterpriseName }).then(res => {
        if (res.code === 200) this.$router.go(0)
      })
    },
    handleCancel() {
      this.EnterpriseName = ''
      this.visible = false
    },
    handleChange(id) {
      this._toggleEnterprise(id).then(res => {
        if (res.code === 200) this.$router.go(0)
      })
    },
    changeCurrentFirstLevelMenu(item) {
      this.SetCurrentLevelOneMenu([])
      if (!item.children) {
        this.$router.push({ name: item.name })
        return
      }
      this.SetCurrentLevelOneMenu([item])
      let child = item.children.find(route => route.hidden === false) || item.children[0]
      while (child && child.children && child.children.length) child = child.children[0]
      if (child) this.$router.push({ name: child.name })
    }
  }
}
</script>

<style lang="less" scoped>
@import './RightContent.less';
.header-msg-bkc {
  .selectBox { /deep/ span, i { color: rgba(0, 0, 0, 0.65); } }
  /deep/ svg { color: rgba(0, 0, 0, 0.65); }
}
</style>
