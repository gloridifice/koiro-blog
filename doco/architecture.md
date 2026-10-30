# 当前架构

## 构建与运行边界

这是 Kotlin/JVM 静态站点生成器。Node.js 24 的 `scripts/site.mjs` 通过 `npm run build` 编排完整构建：校验静态输入及 LFS 对象、重建 dist、运行 Kotlin 测试和生成器、编译 Dart Sass 与 Rust/Wasm、检查发布产物。浏览器不调用翻译服务，不在访问时选择正文回退。

入口为 `src/main/kotlin/Main.kt`，可接收 data、output、i18n 三个目录参数；默认分别为 data、dist、i18n。`KoiroCatCafe` 编排一次构建，不保存全局可变的当前语言或导航状态。

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

- `content/`：公共 metadata 的四类类型、语言 Markdown 加载、ID/发布基线校验、译本选择。与 HTML DSL 无依赖。
- `i18n/`：语言注册、TOML 文案、命名参数模板及中文回退；缺失文案警告按构建去重。
- `routing/`：稳定内容身份、统一分页、语言化 URL、站内文章互链及旧地址映射。PageContext 显式传给页面与组件。
- `htmlgen/`：kotlinx.html 静态渲染。ArticleContent 使用 JetBrains Markdown AST；受信任原始 HTML 的本地 URL 用 Jsoup 解析后重写，不将 Jsoup 当作内容安全过滤器。元数据和文案普通文本由 DSL 转义。
- `ImageAssets`：相对正文/元数据解析附件，限制在内容根内，复制到共享 images/content；images 下其他路径保留给历史别名，避免 Windows 大小写折叠破坏 Linux 托管路径；PNG/JPEG 保留原有压缩行为，GIF 与其他附件按字节复制；尺寸缓存在一次构建内共享。
- `src/main/rs-wasm-script/`：原有头像、滚动显现和图片栈等浏览器交互。语言选择使用独立静态 `site-language.js`，根入口不依赖 Wasm 初始化。
- `src/main/stylesheet/`：Dart Sass 编译的全局样式；language.scss 维护语言控件及翻译提示，颜色使用 CSS 自定义属性。

生成器先加载并验证输入，构建统一发布集合和页面计划，再按语言选择译本并渲染。HTML/RSS 在全部页面渲染成功后写出；附件复制可能先发生。生成文件清单限制增量删除范围，不删除无关手写文件。完整 npm 构建则清空并重建 dist。

## 输入与输出

内容包为英文 snake_case 目录，meta.toml 保存公共字段，zh-CN.md 等保存译本。中文是发布基线，英文为默认访问语言；页面字典和正文的回退规则分别管理。

每个已注册语言有独立目录及 RSS；assets、images、pkg 共享。index.html 由生成器输出，根入口按保存偏好、浏览器语言、英文默认值选择首页。直接语言 URL 不自动重定向。旧根页面和旧标题地址跳转中文稳定地址，根 rss.xml 为中文 XML 副本，历史图片 URL 由迁移清单维持。

## 工具与持久化

- `npm ci` 安装锁定的 Node 依赖；构建不重复安装。`npm run check` 独立校验现有 dist，构建与发布校验不依赖 Python。
- `scripts/dev.mjs` 为 `npm run dev` 提供 dist 本地 HTTP 服务与 Sass 监听；缺少入口、生成清单或 Wasm 时先完整构建。默认监听本机 8000 端口，占用时递增查找空闲端口；支持 `--port`/`PORT` 与 `--host`，终端打印实际地址。响应不缓存，修改样式后手动刷新；正文、Kotlin 和 Rust 不自动重建。Ctrl+C 同时停止服务和监听。
- `scripts/migrate_content.py` 默认 dry-run，显式 apply 才写内容；使用临时树、哈希校验与独立备份，生成持久兼容清单，不翻译正文。
- `scripts/prepare_images.py` 独立于部署构建，处理图像隐私元数据和 PNG/JPEG 转换；同步修改 Markdown、meta.toml 封面及兼容清单中的新源路径。
- 浏览器偏好只存于 localStorage 的 `koiro.site.locale`；构建不读取浏览器偏好，也不回写 data。
- `.generated-site-files.json` 属于可丢弃输出，不是内容源或迁移历史。

详细契约见 [内容](specs/content-localization.md)、[文案](specs/page-messages.md)、[输出](specs/localized-output.md)、[迁移兼容](specs/data-migration.md)。当前已提供完整英文字典，现有内容包均含标记 `ai-translation = true` 的英文译本；草稿保持原有公共发布状态。未来缺失的文案或译本仍按上述规则回退，构建不自动翻译。
