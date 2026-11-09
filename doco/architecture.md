# 当前架构

## 构建与运行边界

这是 Kotlin/JVM 静态站点生成器。Node.js 24 的 `scripts/site.mjs` 通过 `npm run build` 编排完整构建：校验静态输入及 LFS 对象、准备 dist 并保留增量资源、运行 Kotlin 测试和生成器、编译 Dart Sass 与 Rust/Wasm、检查产物。默认构建包含公共 `published = false` 的草稿；`npm run build -- --release` 只生成已发布内容，CI 使用此发布模式。浏览器不调用翻译服务，不在访问时选择正文回退。

入口为 `src/main/kotlin/Main.kt`，可接收 `--release` 选项及 data、output、i18n 三个目录参数；默认分别为 data、dist、i18n。`KoiroCatCafe` 编排一次构建，不保存全局可变的当前语言或导航状态。

## 模块与数据流

```text
i18n/ 配置和字典 ──→ i18n/SiteConfig、Messages ──┐
data/ 内容包 ──────→ content/loadContent ──────┼→ KoiroCatCafe
                                             │     ↓
data/migration-map.toml ─→ routing/LocalizedRoutes   PageContext + ResolvedContent
                                                   ↓
                                              htmlgen 页面与组件
                                                   ↓
                                      各语言 HTML、RSS、兼容入口
```

- `content/`：公共 metadata 的四类类型、语言 Markdown 加载、ID/发布基线校验、译本选择；另由 FriendLinks 加载和校验 `data/friends.toml`，在构建期解析各语言友链。与 HTML DSL 无依赖。
- `i18n/`：语言注册、TOML 文案、命名参数模板及中文回退；缺失文案警告按构建去重。
- `routing/`：稳定内容身份、统一分页、语言化 URL、站内文章互链及旧地址映射。PageContext 显式传给页面与组件。
- `htmlgen/`：kotlinx.html 静态渲染。ArticleContent 使用 JetBrains GFM Markdown AST，将行内及块级公式作为不参与 Markdown 格式解析的 TeX 输出；独立的 `article-math.js` 在浏览器中调用 KaTeX，不依赖 Wasm。受信任原始 HTML 的本地 URL 用 Jsoup 解析后重写，不将 Jsoup 当作内容安全过滤器。元数据和文案普通文本由 DSL 转义。
- 文章目录由 ArticleContent 收集正文 Markdown 标题及锚点，Catalogue 在构建期生成最多三级的导航列表；独立的 `article-toc.js` 管理桌面端透明 Preview Rail 的逐级预览、键盘操作和阅读位置，不依赖 Wasm。样式通过 post.scss 引入 `_article_toc.scss`，手机端不显示目录，详见[输出契约](specs/localized-output.md#文章目录)。
- `buildsystem/SiteResources`：统一登记输出、校验路径及大小写冲突，缓存原子资源并在生成成功后清理过期产物。文章全部语言 HTML 为一个原子，图片/附件与历史图片别名独立为原子，单篇博文的各语言 RSS 条目为一个无文件输出的原子；首页、列表页、兼容跳转和完整 feed 始终全量生成。缓存命中的文章恢复附件依赖，不依赖重新渲染发现图片。
- `ImageAssets`：相对正文/元数据解析附件，限制在内容根内，复制到共享 images/content；images 下其他路径保留给历史别名，避免 Windows 大小写折叠破坏 Linux 托管路径；PNG/JPEG 保留原有压缩行为，GIF 与其他附件按字节复制；尺寸缓存在一次构建内共享；源修改时间、大小及处理代码未变且产物存在、状态未变时复用图片，不重复压缩或写入。
- `src/main/rs-wasm-script/`：头像、滚动显现、图片栈及文章类型筛选等浏览器交互。站点固定使用已发布的深色主题，不按系统偏好或旧 Cookie 加载其他主题。语言选择使用独立静态 `site-language.js`，根入口不依赖 Wasm 初始化。
- Markdown 的 `html component` 围栏由 ArticleContent 生成仅允许脚本的沙箱 iframe/srcdoc；独立的 `article-components.js` 与 `article-component-frame.js` 通过校验来源的 postMessage 同步高度，不依赖 Wasm。公共 CSS/JS 位于 `assets/component/`，由 `htmlgen/component/ComponentAssets.kt` 显式注册后注入，禁止扫描目录自动加载；预设只作用于标记组件，作者可覆盖样式或编写自己的逻辑。组件 HTML 资源沿用内容包附件解析，CSS/JS 字符串中的依赖不自动重写。
- `src/main/stylesheet/`：Dart Sass 编译的全局样式；language.scss 维护语言控件及翻译提示，颜色使用 CSS 自定义属性。正文 Markdown 文字链接在构建期标记图标类型，page_content.scss 用空伪元素和本地 SVG mask 显示前置图标，不插入可复制字符或图片节点；默认链条图标，仅 HTTP/HTTPS（含协议相对地址）的 github.com、www.github.com 使用 GitHub 图标，域名不区分大小写。普通、引用式及自动链接一致处理，纯图片链接、原始 HTML 链接、导航及组件 iframe 不参与。

生成器先加载并验证输入，按构建模式确定统一内容集合和页面计划，再按语言选择译本、复用原子资源并渲染其余页面。文章指纹覆盖全部译本、公共 metadata、字典、路由计划及生成代码，保守处理共享依赖变化；RSS 条目指纹仅覆盖其实际字段及生成配置。HTML/RSS 在全部页面生成成功后写出；附件复制可能先发生。生成文件清单包含附件并限制增量删除范围，不删除无关手写文件。npm 构建保留登记产物和缓存，静态图片按源/输出时间与大小复用，其余静态文件重拷；清除未登记的旧文件，Kotlin 成功生成后清理不再需要的登记产物。

## 输入与输出

内容包为英文 snake_case 目录，meta.toml 保存公共字段，zh-CN.md 等保存译本。中文是发布基线，英文为默认访问语言；页面字典和正文的回退规则分别管理。

友链是独立的结构化内容，集中保存在 `data/friends.toml`，不参与文章路由、时间线或 RSS。公共名称和 URL 与各语言名称覆盖、简介放在同一条目内；按目标语言、en-US、zh-CN 选择完整语言块。解析后的列表显式传给关于页，不扩充 PageContext。关于页每次全量生成，友链配置不进入文章或 RSS 的缓存指纹。

每个已注册语言有独立目录及 RSS；assets、images、pkg 共享。index.html 由生成器输出，根入口按保存偏好、浏览器语言、英文默认值选择首页。直接语言 URL 不自动重定向。旧根页面和旧标题地址跳转中文稳定地址，根 rss.xml 为中文 XML 副本，历史图片 URL 由迁移清单维持。

## 工具与持久化

- `npm ci` 安装锁定的 Node 依赖；构建不重复安装。`npm run check` 独立校验现有 dist，构建与发布校验不依赖 Python。
- `scripts/dev.mjs` 为 `npm run dev` 提供 dist 本地 HTTP 服务与 Sass 监听；缺少入口、生成清单或 Wasm 时先完整构建。默认监听本机 8000 端口，占用时递增查找空闲端口；支持 `--port`/`PORT` 与 `--host`，终端打印实际地址。响应不缓存，修改样式后手动刷新；正文、Kotlin 和 Rust 不自动重建。Ctrl+C 同时停止服务和监听。
- `scripts/migrate_content.py` 默认 dry-run，显式 apply 才写内容；使用临时树、哈希校验与独立备份，生成持久兼容清单，不翻译正文。
- `scripts/prepare_images.py` 独立于部署构建，处理图像隐私元数据和 PNG/JPEG 转换；同步修改 Markdown、meta.toml 封面及兼容清单中的新源路径。
- `data/.obsidian/plugins/koiro_blog/` 为独立 Obsidian 插件工程，在阅读视图及 CodeMirror Live Preview 中预览同样的组件标记，并注册 TOML 纯文本编辑视图和文件树右键创建入口；改名、移动沿用 Obsidian 原生操作。构建时内联博客高度脚本，并调用 Gradle 导出唯一的 Kotlin 组件注册表，将登记的 CSS/JS 打包供离线加载；不维护第二份清单或扫描资源目录。运行时通过 Vault API 读取明确引用的本地资源，不参与站点构建；源码与构建产物位于同一目录，`data/` 库直接加载，无需复制。仓库跟踪该插件工程及三个发布文件，忽略依赖、测试临时产物、插件运行配置和其他 Obsidian 文件；Node 输入校验和 Kotlin 内容发现跳过整个 `data/.obsidian/` 子树，避免把编辑器配置、插件源码和依赖当作内容。详见[插件契约](specs/obsidian-koiro-blog.md)。
- 浏览器偏好只存于 localStorage 的 `koiro.site.locale`；构建不读取浏览器偏好，也不回写 data。
- `.generated-site-files.json` 记录当前生成产物；`.incremental-site.json` 保存 Kotlin 原子资源指纹、输出状态、附件依赖及 RSS 条目；`.static-image-cache.json` 保存 Node 静态图片状态。均为 dist 内可丢弃输出，不是内容源或迁移历史；删除 dist 可执行冷构建。

详细契约见 [内容](specs/content-localization.md)、[友链](specs/friend-links.md)、[交互组件](specs/article-components.md)、[文案](specs/page-messages.md)、[输出](specs/localized-output.md)、[迁移兼容](specs/data-migration.md)。当前已提供完整英文字典，历史迁移内容包均含标记 `ai-translation = true` 的英文译本；新增内容包可仅含中文，草稿由公共 `published = false` 控制，仅在 release 构建中排除。缺失的文案或译本仍按上述规则回退，构建不自动翻译。
