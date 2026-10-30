# 多语言输出与浏览器选择

## 目录与路由

index.html 为生成的根入口。每个已启用语言目录包含 home.html、按需的 home-N.html、blogs.html、portfolio.html、about.html、rss.xml，以及 pages/blogs/<id>.html、pages/devlogs/<id>.html。assets、images、pkg 等无语言差异资源在根目录共享。新附件位于 images/content/<data 相对路径>，历史图片别名保留原 images 路径；分开命名空间，避免 Art/art 等目录在 Windows 被合并而导致 Linux 托管断链。

所有语言共享发布集合、排序及分页。相同日期按内容类型、ID 作确定性排序。动态没有详情页，使用所属主页页码与 `active-<id>` 锚点；项目使用 portfolio.html 的 `project-<id>` 锚点。

站内导航、分页、文章链接和 feed 入口属于当前语言。相对 Markdown 文章链接按内容包索引转为当前语言地址，显式语言 URL 和外链不改。无效文章目标失败，不发布断链。

## 入口与偏好

根入口用静态 site-language.js 选择语言：有效 localStorage 偏好 `koiro.site.locale` → navigator.languages（不可用时 navigator.language）→ en-US。直接语言地址不自动重定向，也不因访问而隐式改写主动偏好。

按浏览器候选顺序逐项精确匹配，再匹配同 language/script 的默认项；没有默认项时只采用唯一候选。Intl.Locale 推导 script，en-GB 可选 en-US，zh-SG 可选 zh-CN，Hant 不匹配 Hans。无 Intl.Locale 时保守匹配，仅保留精确匹配及英文同语言匹配。

语言选项为普通链接；手动点击尝试保存偏好，存储失败仍能导航。切换保留页面类型、主页页码及稳定动态/项目锚点；不承诺不同译本的标题段落锚点相同。禁用 JS 时根入口仍有普通语言链接，页面正文与回退声明可直接阅读。

## 侧边栏

头像和简介下方居中排列 GitHub、itch.io、语言图标。简介外边距为 `24px 0 10px`，按钮 gap 为 4px，命中区 34px、SVG 19px，图标行底部留白 16px。默认弱色、无底框；itch 为描边店铺/手柄，GitHub 描边 2.2，其余 1.7。键盘焦点可见。

侧边栏的游戏作品和博客仓库外链按钮内部文字保持单行；两按钮总宽度超出容器时，上下排列并分别居中。换排依据实际内容与容器宽度，不以固定视口断点强制切换。

语言按钮就近展开 popover，不挤动布局；显示名称、语言代码、当前标记。支持键盘、Escape、外部点击、滚动/缩放重新定位；窄屏不溢出。无 JS 或无 popover 支持时显示普通语言链接。

## 回退与索引

回退页面保留目标界面语言和路径，正文标记实际语言。详情真实译本 self-canonical 并互相声明已发布真实译本的 hreflang；缺译详情 noindex,follow，canonical 指向实际译本，不伪造目标语言的 hreflang。混合列表 self-canonical，不因条目缺译而整页 noindex。

每语言 RSS 仍只收录博文，链接指向该语言目录，摘要与页面使用相同译本并注明状态。XML 字符正确转义，feed 日期为 RFC 1123；无时区源日期按 Asia/Shanghai 解释。根 rss.xml 为中文 feed 的实际 XML 副本。

## 兼容与增量构建

旧根入口与旧标题文章地址保留中文静态跳转及普通链接，保留中文片段，不按偏好改跳英文；跳转页 noindex。旧图片由 data/migration-map.toml 维持。别名暂不自动过期。

生成器维护自身输出清单，只删除清单内不再生成的文件，拒绝越界路径或输出符号链接，不批量删除手写文件。`npm run build` 清空并重建 dist，校验全部语言页面、RSS、站内链接、资源和兼容入口，并逐段核对生成清单路径的实际大小写；不读取 dist-only 模拟状态。
