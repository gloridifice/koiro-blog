# 友情链接配置

## 输入格式

友情链接保存在内容根的 `friends.toml`（默认 `data/friends.toml`），使用 `[[friends]]` 表数组，文件顺序即所有语言的显示顺序。文件必需；空列表须明确写为 `friends = []`，关于页此时隐藏整个友链区块。

```toml
[[friends]]
id = "example"
name = "示例站点"
url = "https://example.com/"

[friends.translations.zh-CN]
description = "中文简介"

[friends.translations.en-US]
name = "Example site"
description = "English description"
```

- `id`、公共 `name`、公共 `url` 必填且为非空白字符串。ID 沿用内容的 snake_case 和 Windows 设备名限制，在友链列表内唯一。
- URL 必须为合法的绝对 HTTP/HTTPS 地址，含有效主机名；指定端口时须在 0–65535 范围内。各语言共用 URL，不按语言改写外链。
- `translations` 必填，语言标识必须规范且已在 `i18n/locales.toml` 注册。每条友链必须有 `zh-CN` 语言块。
- 每个语言块必须提供非空白的字符串 `description`；可选 `name` 也必须是非空白字符串。公共名称通常保留站点专名，语言块中的名称只覆盖该语言显示。
- 文件缺失、TOML 错误、缺字段、字段类型错误、未知字段、重复字段、重复显式表定义、重复 ID、坏 URL 或非法语言标识均使构建失败。错误包含源路径，并在可识别时包含条目 ID 和字段。

## 语言选择与渲染

构建按“目标语言 → en-US → zh-CN”去重选择第一个存在的完整语言块，复用 `i18n/locales.toml` 的 `content-fallbacks`。先选块，再读取该块的简介及名称；该块没有名称时使用公共名称，不从另一语言块拼接字段。所有语言共用条目集合和顺序，缺译不会隐藏条目。

缺译警告按友链 ID 和目标语言在一次构建内去重。简介容器的 `lang` 标记实际译文语言；译文名称标记相同语言，公共专名不声明语言。不展示文章式缺译提示、AI 标识或原文入口。浏览器不负责选择或替换文本。

名称和简介均为普通文本，由 HTML DSL 转义，不支持 HTML、Markdown 或页面字典的命名参数模板。外链在新标签页打开，并设置 `rel="noopener noreferrer"`。友链区块标题仍来自页面字典的 `site.friends`。

## 构建边界

生成器在写入 HTML/RSS 前加载并校验全部友链配置，按已注册语言解析后显式传给关于页。不新增全局可变语言状态，也不将友链加入 PageContext、文章 ContentKind、时间线、路由或 RSS。

关于页沿用每次全量生成的规则；友链配置变化不影响文章和 RSS 条目的缓存指纹。重排、修改、删除及清空配置会在下次构建更新全部语言的关于页，构建不会回写配置或自动翻译。
