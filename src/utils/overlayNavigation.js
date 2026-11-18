// Suspend presentation only: closing the owning page or saving still follows its original handlers.
export function createOverlayNavigation() {
  const stack = []
  const publish = () => {
    let layer = 1000
    stack.forEach((entry, index) => {
      layer = Math.max(layer, Number(entry.zIndex) || 0)
      entry.update({ suspended: index !== stack.length - 1, layer })
      layer += 10
    })
  }
  return {
    open(entry) {
      if (!stack.includes(entry)) stack.push(entry)
      publish()
    },
    close(entry) {
      const index = stack.indexOf(entry)
      if (index < 0) return
      stack.splice(index, 1)
      publish()
    }
  }
}

export function installOverlayNavigation(Vue, components) {
  const navigation = createOverlayNavigation()
  for (const component of components) {
    const options = component.options || component
    if (options.ummOverlayNavigation) continue
    options.ummOverlayNavigation = true
    const drawer = options.name === 'ADrawer'
    const render = options.render
    options.mixins = [...(options.mixins || []), {
      data: () => ({ overlaySuspended: false, overlayLayer: 1000 }),
      created() {
        const entry = {
          zIndex: this.zIndex,
          update: ({ suspended, layer }) => {
            this.overlaySuspended = suspended
            this.overlayLayer = layer
          }
        }
        this._overlayEntry = entry
        this._overlayInactive = false
        this.$watch(() => [this.visible, this.zIndex], () => {
          entry.zIndex = this.zIndex
          if (this.visible && !this._overlayInactive) navigation.open(entry)
          else navigation.close(entry)
        }, { immediate: true, sync: true })
      },
      activated() {
        this._overlayInactive = false
        if (this.visible) navigation.open(this._overlayEntry)
      },
      deactivated() {
        this._overlayInactive = true
        this.overlaySuspended = true
        navigation.close(this._overlayEntry)
      },
      beforeDestroy() {
        navigation.close(this._overlayEntry)
      }
    }]
    options.render = function(h) {
      const vnode = render.call(this, h)
      if (!vnode?.componentOptions) return vnode
      const props = { ...vnode.componentOptions.propsData, zIndex: this.overlayLayer }
      if (drawer) props.wrapStyle = { ...props.wrapStyle, zIndex: this.overlayLayer }
      const className = drawer ? 'className' : 'wrapClassName'
      if (this.overlaySuspended) {
        props[className] = [props[className], 'umm-overlay-suspended'].filter(Boolean).join(' ')
        props.maskStyle = { ...props.maskStyle, display: 'none' }
        props.keyboard = false
        props.maskClosable = false
      }
      vnode.componentOptions.propsData = props
      return vnode
    }
  }
}
