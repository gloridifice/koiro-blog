# Implementation design

## 1. Baseline and goals

基线定位：`ee616ed`（`fix(gallery): size image stack from CSS, not JS`）。本次补齐设计时，工作区的正式源码无修改，未跟踪项为 [.agents](../../../../../.agents/)、[AGENTS.md](../../../../../AGENTS.md)、[doco](../../../../)；另有被忽略的 dist-only UI 预览。执行前重新检查 Git 状态，不覆盖其他工作。

本文件记录执行所依据的设计接口与模块。用户随后已授权开始执行；实际交付与验证见 tasks.md，文中设计签名不要求与局部类名完全一致。

| 现有入口 | 现状与本次影响 |
|---|---|
| `Main.kt` / `KoiroCatCafe.kt` | 递归逐 md 解析，立即写页面，companion 保存可变导航对象；改为一次加载、预检后按语言渲染 |
| `Models.kt` | 四类 Header 混合公共与译本字段，slug 实为摘要；改为内容包及选中译本模型 |
| `ServerPath.kt` / `StringExt.kt` | 根路径拼装与旧标题安全化；保留 ServerPath，旧算法仅用于迁移兼容映射 |
| [htmlgen/page](../../../../../src/main/kotlin/htmlgen/page/)、[component](../../../../../src/main/kotlin/htmlgen/component/) | 页面、导航、分页和卡片使用硬编码文案与全局导航；显式传入语言上下文 |
| [ArticleContent.kt](../../../../../src/main/kotlin/htmlgen/page/ArticleContent.kt) | Markdown 渲染、自定义 HTML、标题锚点、图片栈；保留解析行为，增加站内文章目标解析入口 |
| [Image.kt](../../../../../src/main/kotlin/htmlgen/Image.kt) | 内容根相对图片复制及尺寸缓存；复用，同一构建各语言共享实例 |
| [rs-wasm-script](../../../../../src/main/rs-wasm-script/) | 图片栈、头像、滚动等现有交互；此次语言入口不依赖 Wasm，现有模块不必重写 |
| 旧 assets/index.html / `UniversalUtils.kt` | 根跳转和 RSS 入口固定；执行时移除旧静态 index，由生成器输出；head 按页面上下文输出 |
| [build_site.py](../../../../../scripts/build_site.py) | 清 dist、复制 assets、Gradle 测试与运行、样式/Wasm、固定根文件校验；增加语言目录校验 |
| [prepare_images.py](../../../../../scripts/prepare_images.py) | 仅修改 Markdown 图片/preview 引用；扩展处理 meta.toml 公共封面 |

目标契约按职责分为：[内容与声明](specs/content-localization.md)、[文案](specs/page-messages.md)、[输出与选择](specs/localized-output.md)、[迁移](specs/data-migration.md)。本设计补齐此前开放的实现选择；未声称这些选择已在正式站点验证。

## 2. Overall approach

依赖方向：

```text
Main / KoiroCatCafe（编排）
  ├─ i18n（注册、文案模板、格式化）
  ├─ content（文件加载、元数据校验、译本选择）
  ├─ routing（内容身份 → 语言化 URL、旧地址映射）
  └─ htmlgen（上下文 + 已选内容 → HTML/RSS）
       ├─ ArticleContent（正文、站内目标转换）
       └─ ImageAssets（共享资源复制与尺寸）
```

i18n/content/routing 不依赖 HTML DSL 或浏览器。HTML 层不自行扫描磁盘或选择回退。KoiroCatCafe 只编排，不保存跨构建的全局语言状态。先做完整内容和路由预检，再写输出；失败不把已有部分 HTML 当成成功构建。

一次构建：读取语言配置与字典 → 按 meta.toml 发现内容包 → 校验内容身份、中文基线和项目关联 → 建立发布集合、统一排序与分页、旧地址映射 → 为每种语言选择译本 → 渲染页面与 RSS → 写兼容入口与资源别名 → 输出清单供校验/清理。

不做并行渲染；注册表、内容集合和路由表在一次构建内不可变。ImageAssets 缓存和已发出的警告只在当前构建共享，不进入 companion 或进程全局。以后若引入并发需单独处理输出与缓存，不靠可变全局 locale。

## 3. APIs and data model

### 3.1 语言与文案（计划新增 i18n 包）

以下签名定义职责，具体文件拆分允许局部调整：

```kotlin
data class SiteLocale(val tag: String, val name: String,
    val language: String, val script: String?, val matchDefault: Boolean)
data class SiteConfig(val defaultLocale: SiteLocale, val baseLocale: SiteLocale,
    val locales: List<SiteLocale>, val contentFallbacks: List<SiteLocale>)
data class MessageText(val text: String, val actualLocale: SiteLocale)
class Messages {
    fun format(locale: SiteLocale, key: String,
        args: Map<String, String> = emptyMap()): MessageText
}
```

配置示例中的 language/script/match-default 作为注册字段，初始为 en/无 script/true、zh/Hans/true。同 language/script 不允许多个 match-default。tag 规范化后必须唯一且与字典文件名一致；default/base/fallbacks 都必须指向注册项。注册顺序显式保留但不作为模糊匹配的隐式胜出规则。

中文定义全部键和命名参数，英文文件可为空。先解析模板为文本与参数片段；校验花括号、参数集合，再替换一次。返回 actualLocale，让生成器在可能的文本容器标明真实语言；aria-label、title 等属性不嵌标签。完整句子用一个键；链接由组件创建，字典不包含任意 HTML。

### 3.2 内容模型（计划新增 content 包）

```kotlin
enum class ContentKind { BLOG, DEVLOG, ACTIVE, PORTFOLIO }
data class ContentKey(val kind: ContentKind, val id: String)
data class ContentVariant(val localeTag: String, val published: Boolean,
    val aiTranslation: Boolean, val title: String?, val summary: String,
    val description: String?, val preview: String?, val markdown: String,
    val sourcePath: Path)
data class ContentBundle(val key: ContentKey, val directory: Path,
    val metadata: CommonMetadata, val variants: Map<String, ContentVariant>)
data class ResolvedContent(val bundle: ContentBundle, val requested: SiteLocale,
    val variant: ContentVariant) {
    val fallback: Boolean get() = requested.tag != variant.localeTag
}
fun loadContent(root: Path): List<ContentBundle>
fun resolveContent(bundle: ContentBundle, locale: SiteLocale,
    config: SiteConfig): ResolvedContent
```

CommonMetadata 用四类明确的 metadata 结构，不用一大组无约束 nullable 字段；字段及必填规则见 C2/C3。序列化的 `ai-translation` 通过 `@SerialName` 映射；严格布尔值，不接受字符串或整数。

加载器只把含 meta.toml 的目录识别为内容包；不继续将其子资源目录当独立包。包内语言 md 按规范 tag 识别，额外非语言 Markdown 只作为附件，不进入列表。未注册但格式合法的语言文件可保留、校验而不输出；输出集合由配置决定。文件名含下划线的语言形式报错，不与规范文件混用。

模型内保存来源路径用于报错和图片解析；中文为发布基线而非“由 AI 与否”的推断。公共 published=false 的包不参与发布，但其元数据仍校验。项目 work/project 维持原值，缺 portfolio 的 devlog 可独立发布。

### 3.3 路由和渲染上下文（计划新增 routing 包）

```kotlin
sealed interface PageKey // Home(number), Blogs, Portfolio, About, Article(ContentKey)
class LocalizedRoutes {
    fun page(locale: SiteLocale, key: PageKey): ServerPath
    fun feed(locale: SiteLocale): ServerPath
    fun content(locale: SiteLocale, key: ContentKey): ServerPath
}
data class PageContext(val locale: SiteLocale, val page: PageKey,
    val messages: Messages, val routes: LocalizedRoutes, val config: SiteConfig)
```

content 对文章返回详情地址；对动态返回计算后的主页页码及 `#active-<id>`，对项目返回项目页及 `#project-<id>`。不能给无详情页内容拼造 不存在的 /pages/actives/ URL。导航项在每次生成时由上下文创建为不可变值，不再复用会赋值 page 的全局 `homeSubPageInfos`。

`KoiroCatCafe` 保留 data/output/homePageSize 构建入口语义，新增实际使用的 i18nPath 配置输入（默认项目的 i18n），测试可用临时配置目录。所有 htmlgen 入口接受 PageContext，卡片/详情接受 ResolvedContent。API 变更属于生成器内部重构，不维护仅为旧测试服务的过渡 wrapper。

### 3.4 错误边界

配置、元数据、ID、路径冲突、失效内部链接以及占位符错误均携带来源文件与字段/键，由生成器汇总或抛出明确错误并以非零状态退出；不可静默跳过已发布坏内容。缺非基础文案是警告；缺目标译本是正常状态；无可用中文基线是错误。预检可收集多个独立错误，但不写半套“成功”结果。

## 4. Algorithms and rules

### 4.1 发布集合、选择与分页

1. 先过滤公共发布开关。校验每个发布包有可用中文版本。
2. 按公共日期倒序排序；同一时刻以 kind 再以 ID 升序作稳定 tie-break，不依赖文件遍历顺序。迁移报告说明同日期旧排序如受遍历顺序影响可能改变。
3. 统一分页，再按语言给同一位置的内容选择目标/英文/中文译本；不在各语言单独过滤内容。
4. 当前日期格式继续复用现有 formatter；不同时引入日期国际化或复数框架。新增译本不能改变原发布日期。
5. 图片路径使用实际选中 Markdown 为锚点；译本未提供封面时用公共封面，公共封面相对 meta.toml 所在目录。

### 4.2 页面与声明

正文只渲染所选译本，不把回退页面的界面语言误写成正文 lang。字典回退与内容回退完全独立。HTML title 使用真实选中标题，而非现有 ArticlePage 的 `hello`。

正式组件复用一份 Languages SVG。实现已确认的侧边栏位置、4px 间距、描边粗细，以及 C5 的弱灰/黄色声明。文章声明在完整 page_description 之后，不在 h1 内；动态声明在正文之后；卡片只有右下角图标与 title/可访问说明。AI 与回退并存时黄色卡片图标优先，行声明保留两行。项目说明复用普通行，改为“本项目简介”。

正式渲染依据 ResolvedContent 的状态，不读取 `translation-preview` 查询参数，不复制 dist 里的模拟内容注入逻辑。SCSS 负责布局，Kotlin 负责语义 HTML。预览仅作视觉参照，重建后可删除。

### 4.3 语言入口和持久化

计划新增一个小型原生 JS 静态资源 assets/assets/js/site-language.js（计划新增），独立于 Rust/Wasm。根 index 和切换器都使用它，但根 index 不加载全文站点的 Wasm 来做跳转。

生成器把支持语言、默认值和当前页面各语言链接放入 application/json 配置节点，JSON 序列化并转义 `<`、脚本结束序列，禁止手工拼未转义字符串。浏览器只消费这份配置，不另维护语言枚举。

- 根 index：安全读取 localStorage → 按 O3 匹配浏览器语言 → `location.replace` 对应首页。入口提供普通语言链接，JS 失败/禁用仍可使用。不用无条件 meta refresh 抢先覆盖偏好。
- 其他页面：不自动重定向。语言选项为真实 `<a>`，点击时尝试保存已注册 tag，不拦截正常导航；键盘和触控都可用。
- 弹层使用原生 popover，打开/关闭同步 aria-expanded，Escape/外部点击关闭，按按钮位置及视口定位。检查 API 支持；无脚本或无 API 时保留可用普通语言链接列表，避免不可访问的隐藏选项。
- 更新定位响应 resize 和滚动；关闭后恢复合理焦点。不嵌套按钮与链接。未知 localStorage 值忽略，不用于拼任意 URL。
- 精确与同 language/script 匹配算法按 O3；规则服务于已有语言注册，不引入自动翻译或任意地区互换。

### 4.4 输出、RSS 与旧链接

每种语言生成完整主页、列表、详情和 RSS，默认输出两种语言；共享 assets/images/pkg 不复制到语言目录。RSS scope 保持 blog；使用 XML 安全转义并正确处理 CDATA 终止符，链接取同一 LocalizedRoutes。RSS 日期按合法 RFC 822/1123 输出：原无时区日期按 Asia/Shanghai 解释，不改变页面显示日期。根 rss.xml 复制中文 feed。

旧 root HTML 与旧标题地址依据 migration-map 生成中文跳转别名：meta refresh 加普通链接；可用小脚本保留 fragment，不能从偏好选择另一种语言。旧图片路径由兼容清单定位新的共享资源，不能用 HTML 代替图片。同格式时复制发布后字节；如预处理将原 PNG 转为 JPEG，旧 .png 别名输出合法 PNG（从发布图像解码后按旧扩展重新编码），不能仅把 JPEG 重命名为 PNG。动画 GIF 不跨格式转换。所有别名目标必须预检存在。别名长期保留，本次不设自动过期。

真实详情的 canonical/hreflang 和回退详情 noindex 规则按 O2；动态/项目混合列表仍是自 canonical，不因含回退条目把全页当译文详情。

生成器维护自己的输出文件清单（如 output 根 `.generated-site-files.json`），仅记录实际生成的 HTML/feed/别名，不把手写文件纳入。重跑时删除旧清单中已不生成的文件，并验证相对路径处于 output 根内；拒绝畸形清单，不按宽泛通配符删除整个语言目录。完整 Python build 仍清 dist；增量 Gradle build 不应留下被撤下译本、旧分页或废弃语言文件。

Python 校验从 i18n/locales.toml 获取已启用语言，检查各语言必需入口及 feed 的 XML、共享资源、内部生成链接和旧别名目标；图片/src 等现有边界校验保留。不能把现有 required 根页面列表简单删除而不校验新目录。

### 4.5 迁移与 Markdown 互链

迁移映射和安全要求见 D1–D5；一次性迁移器默认 dry-run、显式 apply，使用独立临时工作树完成转换，备份旧 data 后才替换。旧地址安全化与 Kotlin 算法逐例对齐，包括 title/fileName 差异、非法 Windows 字符及 120 字符截断。执行前把 D2 表转为可读的显式映射配置，不调用在线翻译推断目录名。

只改解析到的链接目标范围，避免把代码块中的旧路径当链接替换。迁移保留 source hash 和未消费资源清单，写入兼容清单后不能遗留损坏引用。扫描 raw HTML 媒体需按标签/属性解析；不支持的本地引用形态先报告、人工修正，不声称任意 HTML 能无损自动改写。

计划给 ArticleContent 注入实际生产需要的站内目标解析函数/对象：`resolveLink(source: Path, href: String, locale: SiteLocale): String`。它使用内容包索引和旧地址映射，保持外链、显式语言链接不变，把包内互链转为当前语言目标。Markdown 标题片段保留原值，中文原文兼容不依赖译文的标题结构。

图片预处理同时更新 meta.toml preview 和兼容清单 assets.source 的边界必须单独验证；保留 old-url 并校验别名输出的实际文件格式；不在内容迁移中自动执行图片压缩。保留现有 ImageAssets 的压缩尺寸、GIF 字节、首屏 eager/其余 lazy 行为及 CSS 图片栈布局。

## 5. Fixed decisions and discretion

### 用户已确认

- 默认访问英文、中文基础字典；内容目标/英文/中文回退；中文发布基线。
- meta.toml 与逐语言 Markdown 拆分；语言标识用连字符；目录改为英文 snake_case。
- AI 布尔字段默认 false；卡片、动态、详情的已确认视觉及位置。
- 只补齐变更文档；英文正文和页面文案暂不翻译。

### 本次设计补全采用的选择

稳定 ID 唯一性与命名、D2 显式映射、旧链接长期保留、中文根 RSS、Intl.Locale 保守匹配、localStorage 键、真实译本 SEO、双状态优先级、未知历史字段保留、草稿校验边界、统一分页与增量清理，均已写入目标 specs。它们是供执行审阅的明确方案，不伪称已实现或逐项经过用户体验验证。

### 留给实现者的局部裁量

文件拆分、内部类命名、错误汇总的格式、模板解析的内部结构及具体浏览器检查工具可调整，但不得改已定义行为。不要因为测试便利新增仅测试使用的 production seam。

### 明确延期，不阻塞本次实现设计

全量英文翻译、RTL 排版、复数规则、各语言日期格式、译文过期检测、人工审校标记、独立正文语言偏好、单独原文按钮均不在本次实现范围。相关问题不再作为实现时临时发明行为的理由。

本设计没有待实现者猜测的核心规则。用户已另行授权执行；若源数据或需求变化，先更新映射/规格，不自行扩大迁移范围。

## 6. Verification and documentation impact

### 现有覆盖与变更位置

- `KoiroCatCafeTest` 已保护指定 output、资源复制与 RSS 链接；迁移 fixtures 到内容包，在该真实生成边界扩展语言输出、回退及错误场景。
- `HomePaginationTest` 已保护混合时间线分区、分页边界、增量删除、图片尺寸/懒加载；扩展到多语言同一集合，不重复另写一套分页算法测试。
- `BlogPreviewTest` 已保护图标优先级、文本转义和草稿坏图标失败；适配模型，增加独立的声明 DOM 场景，不通过源码 grep 证明 UI。
- `ArticleContentTest` 保留 Markdown 回归；仅在新增站内链接解析边界扩展真实输入/输出用例，避免重复测试既有内联语法。
- Python 迁移/预处理/输出校验的文件与恢复语义由 Python 临时目录测试承担；DOM 声明、无脚本可读内容由 Kotlin 输出测试承担；浏览器只验证交互、位置及 preference 行为，不重复验证全部内容解析。

每个新增用例需说明可观察契约、可信回归、现有覆盖缺口和是否需要测试专用 seam；优先参数化现有 owner-boundary 测试，不以快照、私有方法调用或源码文本匹配代替行为验证。

### 必要验证

内容选择矩阵覆盖目标有/无、英文有/无/草稿、中文缺失、总开关、双状态；字典覆盖缺键、空英文、非法参数、转义；迁移覆盖全部映射与媒体哈希、幂等、冲突和备份；输出覆盖语言链接、RSS、canonical、旧别名、增量清理。浏览器检查桌面/390px 窄屏、键盘、Escape、外部点击、存储禁用及 JS 禁用。

完整构建与真实迁移只能在执行阶段进行。计划命令和验收映射见 [tasks](tasks.md)，本轮不把未来测试写成已通过。

### 执行中核实的兼容细节

- 旧数据有 10 篇博文重复 date。对照 24 篇既有发布详情，最后值与原显示全部一致。迁移保留最后值，较早值存入 legacy_dates，避免丢失历史元数据；这不是发布日期调整。
- 保留 kotlinx.html、Markdown、ktoml 版本。现有 Jsoup 1.21.2 由测试依赖提升为渲染依赖，解析受信任 HTML 的本地 URL；增加官方 kotlinx-serialization-json 1.9.0 序列化语言配置和输出清单，不手工拼接 JSON。
- 扩大站内链接校验后发现旧 Markdown 渲染器把未定义的方括号表达式错误输出为链接；按 AST 收集真实参考定义、未定义表达式保留文字，补回归测试，不放宽断链校验。
- 浏览器检查发现旧固定脚本经过 HTML 文本转义而产生语法错误；将固定应用代码按 kotlinx.html script 的正确 raw 内容输出，动态配置仍单独 JSON 序列化并转义脚本边界。

- Windows 实测发现新 data 分类 art 与旧图片别名 Art 若共用镜像路径，会被合并为同一物理目录，无法在大小写敏感托管中保留两个 URL。新共享附件使用 images/content 命名空间，旧 images 别名不变；这不改变内容包格式。发布校验逐段检查清单中的真实文件名大小写，防止本地通过而 Linux 断链。

### 当前文档影响

实现获验收后创建/更新当前 content-localization、page-messages、localized-output 和迁移兼容规格，填写实际架构，并更新 README、部署与图片准备文档。永久规格保留格式/行为，不复制 41 项一次性工作清单；持久旧地址映射留在 data/migration-map.toml。无需为每个内部类单独建立 ADR。执行交付后按实际实现同步当前文档；不自动完成或归档变更。
