# 前端样式约定

项目使用 Tailwind CSS 3，适配 Vue 2、Vite 2、Ant Design Vue 和 Element UI。

## 通用样式

宽高、间距、字号、对齐、显示方式等优先使用 `tw-` 前缀的工具类。前缀用于避免与旧组件的 `container`、`flex`、`text` 等类重名。

```vue
<p class="tw-text-[25px] tw-my-0 tw-mx-auto tw-text-center">提示内容</p>
<a-menu class="tw-max-h-[70vh] tw-overflow-y-auto" />
<a-select class="tw-w-full" />
<a-alert class="tw-mb-4" />
```

配置关闭了 Preflight，保留现有组件库的默认样式。间距刻度使用像素：`tw-mb-4` 为 16px；字号工具类只修改字号，不附带行高，避免改变表单和表格尺寸。工具类使用 `!important` 保留原内联样式对组件根元素的覆盖优先级，也适用于挂在 body 下的弹层。

非刻度尺寸使用完整、可扫描的类名，例如 `tw-w-[190px]`。不要拼接 `tw-w-${width}`，Tailwind 无法从动态拼接生成对应 CSS。

## 页面专用与动态样式

组件专用布局、复杂选择器、伪元素和交互状态继续使用已有的 scoped CSS/Less。反复出现的完整业务布局优先抽为组件，避免复制长串类名。

运行时计算的宽度、位置、图表配置、裁剪结果，以及低代码表单配置生成的样式保留 `:style` 或样式对象。组件库的 `body-style`、`dropdown-style` 等属性作用于组件内部元素，不能直接替换为根节点上的类。

同一个元素需要使用动态样式覆盖宽度等属性时，该属性继续保留内联写法，避免被带有 `!important` 的工具类覆盖。

行业、地区等复用组件的默认宽度使用 `app-control-full`，在 Tailwind components 层通过 `@apply` 维护。该默认类不带 `!important`，父页面的 `tw-w-[150px]` 等宽度和动态内联样式可以正常覆盖。

## 检查

运行 `npm run test:styles` 检查迁移工具的模板定位、动态样式保留和 Tailwind 类生成，并检查是否出现可直接复用工具类的新内联样式。运行 `npm run build` 验证正式构建。

`node tools/migrate-static-styles.mjs` 仅检查；添加 `--write` 执行迁移。工具在写入前备份源文件到忽略目录 `back/logs/tailwind/before-src`，并校验 Vue 模板编译结果。复杂或动态样式会跳过，留给人工判断。
