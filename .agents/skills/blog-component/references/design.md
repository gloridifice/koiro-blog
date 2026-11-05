# 项目风格与组件组合

## 样式来源

以下是博客现有样式的近似采样，不是动态继承或自动生成的主题。正式资源位于 `assets/component/`，由 Kotlin 显式注册后提供给博客和 Obsidian 组件 iframe。相对路径从本文件所在目录解析。

| 来源 | 采用的规则 |
| --- | --- |
| [颜色](../../../../src/main/stylesheet/color_scheme_v2.dark_mode.scss) | 背景 `#0D0D0D` / `#171717` / `#212121`，文字 `#eae7e7` / `#b7b3b0`，强调色 `#ce4623` |
| [字体与按钮](../../../../src/main/stylesheet/root.scss) | 系统无衬线、JetBrains Mono 字体族，5px 按钮圆角、底部 4px 内阴影，边框交互反馈 |
| [正文内容](../../../../src/main/stylesheet/page_content.scss) | 6px 内容边框、灰色分隔、表格边线 |
| [文章布局](../../../../src/main/stylesheet/post.scss) | 近黑内容底色、15px 正文基准 |

本 skill 不搬运文章布局：组件外无标题、导语、说明栏或页脚。输入框、滑杆、分段导航和状态反馈是按项目配色补充的组件样式，不声称博客原先已有完整控件库。

图片滑动对比按普通图片展示，使用独立的无边框样式，不套用本节的面板外观；详见 [图片对比](image-compare.md)。

## 可复用部分

| 部分 | 类 | 使用条件 |
| --- | --- | --- |
| 面板样式作用域 | `kc-root` | 面板组件使用；提供变量、字体与局部样式，不加外层留白 |
| 图片对比 | `kc-compare` | 独立样式；左 A、右 B、中央滑杆与下方 caption，无边框或卡片 |
| 面板 | `kc-panel` | 需要统一边界时；近黑背景、细边框、6px 圆角 |
| 顶栏 | `kc-panel-head` | 需要导航或模式标记时；无额外组件标题 |
| 分段导航 | `kc-tabs` | 两个及以上视图；颜色和下划线都标明选中状态 |
| 模式标签 | `kc-badge` | 显示当前模式、介质、数量等实际信息 |
| 图示区 | `kc-figure`、`kc-caption` | SVG / Canvas 及单位、图例；坐标和绘图逻辑不属于共享样式 |
| 参数区 | `kc-controls`、`kc-field` | 一组输入；需要较宽滑杆时加 `kc-controls--weighted` |
| 输入控件 | `kc-range`、`kc-input`、`kc-select` | 原生输入；滑杆通过 `--value` 设置已选比例 |
| 开关 | `kc-switch` | 单一布尔选项；原生 checkbox，带 label |
| 操作区 | `kc-toolbar`、`kc-actions` | 控件与按钮组合，允许换行 |
| 按钮 | `kc-button`、`kc-button--primary` | 每组最多一个主操作；保留 hover、active、focus、disabled |
| 结果栏 | `kc-metrics`、`kc-metric` | 有多个需要对照的数值时；名称、值、单位分开 |
| 状态与错误 | `kc-status`、`kc-error` | 计算状态、输入错误或操作结果；不以颜色代替文字 |
| 数据与空状态 | `kc-table-region`、`kc-table`、`kc-empty` | 确有采样/列表需求；原生表头、数字对齐、清楚的空状态 |

这些是预设的 HTML/CSS 组合约定，不是 UI 框架。图片对比脚本自动补齐滑杆并初始化；计算公式、数据结构、绘图及其他业务事件仍由每个组件实现。新增公共资源须按 [注册流程](registration.md) 手动加入 Kotlin 清单，不能扫描目录自动注入。

## 变量与尺寸

- `--kc-canvas` / `--kc-surface` / `--kc-input-bg`：近黑面板、顶栏/结果栏、输入背景。
- `--kc-ink` / `--kc-muted`：主文字、次级文字。不要用过暗的灰色显示必读字段。
- `--kc-line` / `--kc-control-line`：内容边线、控件边界。
- `--kc-accent` / `--kc-accent-border` / `--kc-accent-shadow`：路径/选中标记、主操作边框、内阴影。
- `--kc-danger` / `--kc-focus`：补充的错误色 `#f19b91` 与焦点色 `#93c5fd`。状态文字和焦点形状仍须明确。
- `--kc-font` / `--kc-mono`：系统无衬线和等宽字体。未嵌入字体文件，JetBrains Mono 缺失时回退 monospace。
- 边框 1px，面板圆角 6px，控件圆角 4–5px；浏览器支持时使用 squircle。
- 内部间距通常 8 / 12 / 16 / 20 / 24px；不要用大面积外层留白模仿独立页面。
- 标签和按钮 13px 左右；读数用等宽数字。手机端点击目标至少 40px，必要时继续放大。
- 过渡时长 200ms；减少动态时关闭非必要过渡。

朱红优先用于图线、滑杆、边框和选中标记，不用于铺满所有按钮或小字号正文。正常状态可用灰白文字加明确符号，不要求绿色提示。

## 默认结构

```html
<main class="kc-root">
  <section class="kc-panel" aria-label="具体组件名称">
    <!-- 可选：视图切换和当前模式 -->
    <!-- 必需：实际图示或操作内容 -->
    <!-- 按需：参数、操作、结果和状态 -->
  </section>
</main>
```

根元素不提供可见标题，也不预留被删除内容的空间。单图解或单滑杆可以进一步省略顶栏、结果栏和状态栏；不要强制套满截图中的所有区块。

图片对比使用 `<figure class="kc-compare">`，提供两个带 `data-side="a"` / `"b"` 的 img 和 figcaption；滑杆由预设脚本生成，不使用上面的 `.kc-root` / `.kc-panel` 结构。

## 响应式与状态

- 图形自身可以缩放，但控件不能随整页缩小。窄屏将滑杆独占一行，其余字段按空间排列。
- 动态记录用 `textContent` 创建单元格；表格在必要时局部滚动，不能撑宽组件。
- 无效输入显示错误原因，保留最近有效的图示与结果，并停用依赖有效输入的操作。有效后清除错误与 `aria-invalid`。
- 不要每次拖动都用 live region 播报所有数值；实时值放在相应 output 中，状态文字只说明有效/错误或操作结果。
- 结果不持久化，除非用户明确要求并且运行环境支持；不能假设沙箱能访问存储。

## 示例与交付

[refraction.html](../examples/refraction.html) 可在仓库内直接打开，只有面板本体。[image-compare.html](../examples/image-compare.html) 只有 A/B 图片、滑杆和 caption。

示例的共享 CSS/JS 链接仅供直接浏览器预览。交付到文章时省略已注册资源的 link/script 和复制的公共代码；宿主会提供这些预设。保留组件特有的样式、公式和绘图代码，图片改为相对于目标文章的路径。不复制风格比较页或额外说明区域。

示例中 10m 深度、40° 入射角、水的折射率 1.333 是业务参数，不是通用设计要求。示例使用均匀介质、水平界面和底面的假设，不模拟反射光强；更换主题任务时删除这些内容。
