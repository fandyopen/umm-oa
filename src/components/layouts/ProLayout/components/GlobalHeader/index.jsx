import './index.less'

import debounce from 'lodash/debounce'
import PropTypes from 'ant-design-vue/es/_util/vue-types'
import { triggerEvent, inBrowser, isFun } from '../../utils/util'
import 'ant-design-vue/es/icon/style'
import { defaultRenderLogo } from '../SiderMenu/SiderMenu'

export const GlobalHeaderProps = {
  collapsed: PropTypes.bool,
  handleCollapse: PropTypes.func,
  isMobile: PropTypes.bool.def(false),
  fixedHeader: PropTypes.bool.def(false),
  logo: PropTypes.any,
  menuRender: PropTypes.any,
  collapsedButtonRender: PropTypes.any,
  headerContentRender: PropTypes.any,
  rightContentRender: PropTypes.any
}

const GlobalHeader = {
  name: 'GlobalHeader',
  props: GlobalHeaderProps,
  render(h) {
    const { isMobile, logo, rightContentRender, headerContentRender } = this.$props

    const headerCls = 'ant-pro-global-header'

    return (
      <div class={headerCls}>
        {isMobile && (
          <a class={`${headerCls}-logo`} key="logo" href={'/'}>
            {defaultRenderLogo(h, logo)}
          </a>
        )}
        {/* {renderCollapsedButton()} */}
        {headerContentRender && <div class={`${headerCls}-content`}>{(isFun(headerContentRender) && headerContentRender(h, this.$props)) || headerContentRender}</div>}
        {(isFun(rightContentRender) && rightContentRender(h, this.$props)) || rightContentRender}
      </div>
    )
  },
  methods: {
    triggerResizeEvent: debounce(() => {
      inBrowser && triggerEvent(window, 'resize')
    })
  },
  beforeDestroy() {
    this.triggerResizeEvent.cancel && this.triggerResizeEvent.cancel()
  }
}

export default GlobalHeader
