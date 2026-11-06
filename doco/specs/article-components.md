# 文章交互组件

## Markdown 写法

围栏信息按空白拆分，区分大小写，同时含完整的 `html` 和 `component` 标记时，将代码块渲染为交互组件。标记顺序不限，其他标记不影响识别；单独 `html`、`htmlcomponent`、`html mycomponent` 仍是普通代码展示。适用于文章和动态正文。

````markdown
```html component
<style>
  button { padding: 8px 16px; cursor: pointer; }
</style>

<button id="counter">点击了 0 次</button>

<script>
  let count = 0;
  const button = document.querySelector('#counter');
  button.addEventListener('click', () => {
    button.textContent = `点击了 ${++count} 次`;
  });
</script>
```
````

接受 HTML 片段或完整 HTML 文档。脚本、样式内的空行和制表符保留；CRLF/CR 统一为 LF，围栏的 Markdown 缩进按解析器规则去除。HTML 经 Jsoup 解析、资源重写和序列化，不保证整个 HTML 的逐字节一致。

## 运行与隔离

每个组件输出独立的 `iframe srcdoc`，由 HTML DSL 转义属性值。沙箱只开放 `allow-scripts`，不开放 `allow-same-origin`、弹窗、表单提交、下载或顶层导航。组件之间的变量、DOM ID 和 CSS 互不干扰，组件不能直接访问主页面 DOM、Cookie 或 localStorage。

作者的 `<title>` 用作 iframe 的无障碍名称；未提供时使用当前页面字典的 `article.componentTitle`。未声明 doctype 的片段补 HTML doctype。默认 iframe 及内部 html/body 背景透明，透出宿主页面背景；iframe 不继承宿主的 color-scheme，避免深色宿主与默认 srcdoc 配色不一致时出现不透明画布。body 无外边距，使用 flow-root 包含浮动及子元素外边距；作者可以覆盖内部背景和布局。

沙箱不是网络防火墙，也不限制脚本 CPU/内存消耗。这里只接受仓库作者维护的内容，不将此机制作为公开接收不可信代码的服务。原有正文原始 HTML 的受信任策略不变。

## 尺寸与消息

父页面加载 `/assets/js/article-components.js`；组件内注入 `/assets/js/article-component-frame.js`，不依赖 Wasm 或第三方框架。

iframe 默认宽度 100%、高度 150px、无边框。内部 ResizeObserver、资源 load 与字体就绪事件触发测量，使用 requestAnimationFrame 合并请求，通过 postMessage 上报。父页面校验消息类型、opaque origin `null` 和对应 iframe 的 contentWindow，只接受有限正数高度，向上取整并限制为 10000px；超出部分仍可在 iframe 内滚动。父页面在初始化和 iframe load 时请求测量，避免加载先后顺序导致丢失初始高度。

自动高度支持内容增长和收缩。组件应使用内容驱动布局，避免 `100vh`、依赖 iframe 高度的百分比高度或随高度持续增长的布局；这些写法会与自动尺寸反馈。绝对定位、变换及其他不影响 body 尺寸的内容需由作者提供正常文档流容器。

## 公共预设

正式资源位于仓库 `assets/component/`，发布到 `/component/`。`htmlgen/component/ComponentAssets.kt` 的 `registered.css` / `registered.js` 是唯一自动注入清单；只有人工登记的文件进入组件 iframe，禁止目录扫描、glob 或按扩展名自动注入。未登记文件仍可能随静态目录发布，但不自动加载或执行；这不是资源访问控制，也不限制作者显式引用脚本。

CSS 按清单顺序放在作者样式之前，作者可正常覆盖；经典 JS 按清单顺序以 defer 加载。公共样式限定在 `.kc-*` 内，不给任意 HTML 加面板或主题。脚本只初始化明确标记的组件，不开放额外沙箱权限。普通代码块、正文原始 HTML 和外站 iframe 不注入预设。

当前预设包括 `.kc-root` 面板样式，以及无边框的图片对比：

````markdown
```html component
<figure class="kc-compare">
  <img data-side="a" src="./a.png" alt="A 图">
  <img data-side="b" src="./b.png" alt="B 图">
  <figcaption>图片对比</figcaption>
</figure>
```
````

图片对比在文档就绪后补齐滑杆，左 A、右 B、初始 50%，支持鼠标、触摸和键盘。各实例独立、初始化幂等；不自动发现 DOMContentLoaded 后插入的实例。两侧按完整尺寸裁剪，不缩放单侧或透出另一图的透明区域；按 A 的自然比例展示，B 居中 cover。只有图片、滑杆和 caption，无边框、卡片或标题。原始图片和 caption 在禁用 JS 时保留。

新增预设先创建文件，再手动登记 Kotlin 清单并验证两个宿主。`exportComponentAssets` 导出并验证清单为 `build/generated/component-assets.json`，供插件打包；不维护第二份注册配置。作者自己的公式、数据和绘图逻辑仍可写在围栏里，不必全部注册为公共脚本。用法和维护步骤见 `.agents/skills/blog-component/`。

## 资源与边界

- 博客主题变量和字体不动态继承；已注册样式提供明确的项目风格预设，不提供主题同步和源码查看控件。
- 已注册 CSS/JS 不需要在文章重复引用或内联；局部样式与业务逻辑仍可使用内联 `<style>`、经典 `<script>`。
- HTML 的 href 和媒体 src/poster 沿用正文的内容包相对路径解析；组件额外支持 script src。附件复制仍限制在内容根内，站内文章链接按当前语言改写。
- 不解析 CSS 的 `url()`、JS 的 import/fetch 字符串、srcset 或外部 CSS/JS 的传递依赖。它们不会自动相对于 Markdown 文件定位或复制；需要自包含资源或已发布的绝对 URL。
- opaque origin 下的模块脚本、fetch、字体等受浏览器 CORS 约束；沙箱不承诺任意第三方库都可直接运行。
- 构建产物校验递归检查 srcdoc 内 HTML 资源引用，缺失的本地运行时或附件使校验失败。
- RSS 保持摘要和文章链接，不嵌入正文、iframe 或可执行源码；读者通过条目链接访问交互组件。禁用 JS 时 iframe 内静态 HTML 仍可见，但没有交互和自动高度。

## 验证

- `./gradlew test`：标记识别、源码空白、嵌套围栏、资源复制和既有正文渲染。
- `npm test`：静态产物校验，包括 srcdoc 内资源。
- `npx playwright install chromium` 后运行 `npm run test:components`：真实生成器输出的浏览器点击、多实例隔离、CSS/DOM 沙箱、双向高度变化、非法消息和 RSS 降级。也可通过 `BROWSER_CHANNEL=chrome` 或 `msedge` 使用已安装浏览器。

浏览器测试使用临时内容包和独立输出目录，不改写正式文章或 dist；该测试单独运行，不加入普通静态构建的浏览器依赖。
