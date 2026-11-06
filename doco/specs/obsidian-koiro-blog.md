# Obsidian koiro_blog 插件

## 交付与边界

插件位于 `data/component/plugins/koiro_blog/`，ID、显示名和 npm 包名均为 `koiro_blog`，源码入口为 `src/koiro_blog.ts`。它是独立的 npm 工程，不进入博客构建和发布产物；Node 输入校验和 Kotlin 内容发现跳过 `data/component/plugins/` 子树，其他目录仍执行原有内容检查。构建输出 `main.js`，安装只需该文件、`manifest.json` 和 `styles.css`，这三个文件名由 Obsidian 固定要求。仓库跟踪 `data/.obsidian/plugins/koiro_blog/` 中的这三个发布文件，其余 Obsidian 配置、插件和运行状态仍忽略。复制到 `<库>/.obsidian/plugins/koiro_blog/` 后启用；原 Component Renderer 安装需先停用，再移除旧插件目录，避免重复渲染。

插件构建将博客的 `assets/assets/js/article-component-frame.js` 内联到 bundle，保证测量协议复用同一实现。同时通过 Gradle 的 `exportComponentAssets` 执行 Kotlin 注册表，读取 `build/generated/component-assets.json`，只将其中登记的 `assets/component/` CSS/JS 编码为 data URL 后打包。不扫描目录、不维护第二份清单；导出或文件读取失败即终止构建，不回退旧清单。

公共 CSS 顺序与博客一致，放在作者样式之前；公共脚本按注册顺序加载并在文档就绪后初始化标记组件。图片对比等预设的最小 HTML 与博客一致，不需要在 Vault 中安装共享资源，也不请求博客域名或本地服务器。修改注册表、公共资源或高度脚本后需重建插件，已安装版本不会自动同步。

## TOML 文件

插件通过 Obsidian 官方扩展名注册 API 将 `.toml` 关联到独立的 `TextFileView`，无需启用“显示所有文件类型”即可在文件树中显示。改名、拖动和菜单移动沿用原生文件操作，不覆盖 Markdown 编辑器或修改全局配置。

TOML 编辑器为纯文本输入，支持原生选择、复制粘贴与撤销；输入触发 Obsidian 的防抖自动保存，切换或关闭文件由 `TextFileView` 保存。外部文件更新由该基类同步。不解析、格式化或校验 TOML 语法，不执行文件内容。

文件树右键菜单提供 `New TOML file`：右键目录（含库根）时在该目录创建；右键文件时在其父目录创建；文件树空白区域的右键菜单在库根创建。创建空的 `Untitled.toml` 并打开，重名时使用 `Untitled 1.toml` 等递增文件名，不覆盖原文件。命令面板提供同名命令，在活动文件父目录或库根创建。失败以通知提示。停用插件时解除扩展名、视图和菜单注册。

## 识别与视图

按[文章组件契约](article-components.md)使用精确、区分大小写的 `html` 和 `component` 标记，顺序不限。由 markdown-it 解析围栏和行范围，保留脚本空行、制表符；跳过 YAML frontmatter、Obsidian `%%` 注释，以及外层普通代码块里的示例围栏。

- **阅读视图**：Markdown postprocessor 对照当前 section 的源码范围和代码内容判断标记，不注册或覆盖普通 HTML 语言处理器。缺少 section 信息或同一范围中存在无法区分的普通/组件重复代码时保留原代码，不从全库猜测匹配。已有阅读缓存可能不因标记参数变化而刷新，提供 `Refresh reading-view components` 命令强制刷新当前阅读预览。
- **Live Preview**：CodeMirror 6 StateField 提供块级 replacement decoration。光标或选择范围接触围栏时显示源码，离开后运行组件；修改正文或标记会重新计算。重新挂载会重置组件内部状态。
- **源码模式**：不创建组件。

阅读组件通过 MarkdownRenderChild 清理；编辑器组件通过 WidgetType.destroy 清理。卸载插件会停止已挂载组件并恢复阅读视图的代码。尚未完成的附件加载不会在卸载后重新挂载 iframe。

## 运行与资源

iframe 只设置 `sandbox="allow-scripts"`，不开放同源权限、弹窗、下载、表单提交或顶层导航。父窗口只接受该 iframe 的 contentWindow、`origin=null`、正确消息类型及有限正数高度；高度向上取整并限制为 10000px。默认宽度 100%、高度 150px，iframe 及内部 html/body 背景透明，body 使用无外边距的 flow-root；iframe 不继承宿主的 color-scheme，避免深色宿主导致默认画布不透明。作者仍可覆盖内部背景。标题来自组件 `<title>`，缺省为 `HTML component`。

HTML 先在脱离主文档的 DOMParser 中解析，再写入 srcdoc；不把作者源码插入 Obsidian 主 DOM。已注册预设提供明确的组件样式，不动态继承博客主题变量或字体；CSS 限定在组件作用域，普通自定义 HTML 不被强加面板或主题。作者的 base 标签被替换为 `about:blank`，未处理的相对地址不能意外继承 Obsidian 的应用地址。

HTML 媒体 src/poster、script src 和 stylesheet href 支持相对于当前笔记的精确 Vault 路径；以 `/` 开始的地址表示 Vault 根。仅通过 Obsidian Vault API 读取已有文件，拒绝 `..` 越过库根，不使用 Node 文件系统或模糊附件搜索。资源转换为带 MIME 类型的 data URL，附带的查询参数不保留，片段保留。缺失资源以错误文本呈现，不执行部分加载的组件。

外部 HTTP(S)、data、blob URL 保留；协议相对地址转成 HTTPS。其他显式资源协议拒绝。CSS url()、JS import/fetch、srcset 及外部文件的传递依赖不自动处理。普通超链接不映射到博客路由或 Vault 笔记导航。推荐自包含的 HTML/CSS/经典 JS；模块、字体和网络请求仍受 CORS 与 Obsidian CSP 限制。

组件没有 Obsidian API、Vault 操作或任意文件读取的消息桥。插件会读取作者明确引用的库内资源，因此只用于可信的作者代码和 Vault。沙箱不是网络防火墙，也不限制 CPU/内存；不承诺与博客浏览器环境完全等价。

## 验证

源码构建需要 Node.js 24+、JDK 21 与仓库 Gradle wrapper；安装后的插件仍只需三个发布文件。在插件目录执行 `npm ci`、`npm run build`。构建和浏览器测试均从真实 Kotlin 注册表导出预设。首次使用测试浏览器时执行 `npx playwright install chromium`，随后 `npm test`；也可设置 BROWSER_CHANNEL 为 chrome 或 msedge。

`npm test` 覆盖标记、嵌套围栏、注释/frontmatter、真实 Chromium 沙箱与高度、真实 CodeMirror 编辑和销毁，以及离线预设加载、作者样式覆盖和未注册 CSS/JS 不自动生效。CodeMirror 测试仅替代 Obsidian 提供的两个状态字段。

设置 `OBSIDIAN_EXECUTABLE` 为桌面 Obsidian 可执行文件路径后执行 `npm run test:obsidian`，会构建插件并启动隔离的临时库和应用配置，不操作日常库。真实客户端测试覆盖隐藏不支持文件时的 TOML 显示、自动保存、原生改名和移动后的继续编辑、外部更新、文件/目录/空白区域右键创建、重名保护、关闭前保存及停用清理。已在 Windows Obsidian 1.12.4 验证；其他版本和移动客户端仍需单独验收。
