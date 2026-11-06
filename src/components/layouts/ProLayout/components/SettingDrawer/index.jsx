import './index.less'

import PropTypes from 'ant-design-vue/es/_util/vue-types'

import 'ant-design-vue/es/divider/style'
import Divider from 'ant-design-vue/es/divider'

import 'ant-design-vue/es/drawer/style'
import Drawer from 'ant-design-vue/es/drawer'

import 'ant-design-vue/es/icon/style'
import Icon from 'ant-design-vue/es/icon'

import 'ant-design-vue/es/alert/style'
import Alert from 'ant-design-vue/es/alert'

import antPortal from 'ant-design-vue/es/_util/portalDirective'

import BlockCheckbox from './BlockCheckbox'
import LayoutSetting from './LayoutChange'

const baseClassName = 'ant-pro-setting-drawer'

const BodyProps = {
  title: PropTypes.string.def('')
}

const Body = {
  props: BodyProps,
  render(h) {
    const { title } = this

    return (
      <div style={{ marginBottom: 24 }}>
        <h3 class={`${baseClassName}-title`}>{title}</h3>
        {this.$slots.default}
      </div>
    )
  }
}

const defaultI18nRender = t => t

const getThemeList = i18nRender => [
  {
    key: 'light',
    url: 'https://gw.alipayobjects.com/zos/antfincdn/NQ%24zoisaD2/jpRkZQMyYRryryPNtyIC.svg',
    title: i18nRender('app.setting.pagestyle.light')
  },
  {
    key: 'dark',
    url: 'https://gw.alipayobjects.com/zos/antfincdn/XwFOFbLkSM/LCkqqYNmvBEbokSDscrm.svg',
    title: i18nRender('app.setting.pagestyle.dark')
  }
]

export const settings = {
  theme: PropTypes.oneOf(['dark', 'light']),
  layout: PropTypes.oneOf(['sidemenu', 'topmenu']),
  contentWidth: PropTypes.oneOf(['Fluid', 'Fixed']).def('Fluid'),
  // 替换兼容 PropTypes.oneOf(['Fluid', 'Fixed']).def('Fluid')
  // contentWidth: PropTypes.oneOfType([PropTypes.string, PropTypes.bool]).def('Fluid'),
  fixedHeader: PropTypes.bool,
  fixSiderbar: PropTypes.bool,
  multiTab: PropTypes.bool,
  breadcrumb: PropTypes.bool,
  footerView: PropTypes.bool,
  showFirstLevelMenu: PropTypes.bool,
  menuIconHorizontal: PropTypes.bool,
  hideHintAlert: PropTypes.bool.def(false)
}

export const SettingDrawerProps = {
  getContainer: PropTypes.func,
  settings: PropTypes.objectOf(settings),

  i18nRender: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]).def(false)
}

const SettingDrawer = {
  name: 'SettingDrawer',
  props: SettingDrawerProps,
  inject: ['locale'],
  data() {
    return {
      show: false
    }
  },
  render(h) {
    const { setShow, getContainer, settings } = this

    const {
      theme = 'dark',
      layout = 'sidemenu',
      fixedHeader = false,
      fixSiderbar = true,
      multiTab = true,
      breadcrumb = false,
      footerView = false,
      showFirstLevelMenu = false,
      menuIconHorizontal = false,
      contentWidth,
      hideHintAlert
    } = settings

    const i18n = this.$props.i18nRender || this.locale || defaultI18nRender
    const themeList = getThemeList(i18n)
    const isTopMenu = layout === 'topmenu'

    const changeSetting = (type, value) => {
      this.$emit('change', { type, value })
    }

    return (
      <Drawer visible={this.show} width={300} onClose={() => setShow(false)} placement="right" getContainer={getContainer} class="tw-z-[999]">
        <div class={`${baseClassName}-content`}>
          <Body title={i18n('app.setting.pagestyle')}>
            <BlockCheckbox
              i18nRender={i18n}
              list={themeList}
              value={theme}
              onChange={val => {
                changeSetting('theme', val)
              }}
            />
          </Body>

          <Divider />

          <Body title={i18n('app.setting.navigationmode')}>
            <BlockCheckbox
              i18nRender={i18n}
              value={layout}
              onChange={value => {
                changeSetting('layout', value, null)
              }}
            />
          </Body>

          <LayoutSetting
            i18nRender={i18n}
            contentWidth={contentWidth}
            fixedHeader={fixedHeader}
            fixSiderbar={isTopMenu ? false : fixSiderbar}
            multiTab={multiTab}
            breadcrumb={breadcrumb}
            footerView={footerView}
            showFirstLevelMenu={showFirstLevelMenu}
            menuIconHorizontal={menuIconHorizontal}
            layout={layout}
            onChange={({ type, value }) => {
              changeSetting(type, value)
            }}
          />
          {hideHintAlert ? null : <Divider />}

          {hideHintAlert ? null : <Alert type="warning" message={i18n('app.setting.production.hint')} icon={<Icon type={'notification'} />} showIcon class="tw-mb-4" />}

          <div class={`${baseClassName}-content-footer`}>{this.$slots.default}</div>
        </div>
      </Drawer>
    )
  },
  methods: {
    setShow(flag) {
      this.show = flag
    }
  }
}

SettingDrawer.install = function (Vue) {
  Vue.use(antPortal)
  Vue.component(SettingDrawer.name, SettingDrawer)
}

export default SettingDrawer
