# 内容迁移与兼容清单

## 命名和保真

迁移工具 scripts/migrate_content.py 使用显式 scripts/content_migration_plan.json。内容包目录及初始 ID 为英文 snake_case，语言 Markdown 保持 zh-CN.md 等连字符形式；分类父目录按映射规范化，项目移除历史 Table 层。包内图片不强制更名。

迁移不翻译原文。公共字段进入 meta.toml，语言字段保留在中文头部，slug 改为 summary，AI 默认 false。除解析到的本地链接目标外，正文与代码示例保持原样。历史字段保留；重复 date 按原 ktoml 实际使用的最后一个值规范化，较早值保存为 legacy_dates，不能改变原有显示日期。

资源复制不重编码，源和新路径哈希一致；处理 Markdown 图片、参考定义、嵌套图片说明、HTML 媒体及封面路径，保留 URL 查询和片段。未引用资源保留并报告，冲突不静默覆盖。旧资源可以复制到多个包，但各译本共享本包附件。

## 执行边界

默认 dry-run 不写 data，--apply 才发布。先完整构造临时树、验证字段/链接/哈希，再校验源目录未在过程中变化，备份并核对备份哈希，最后替换。失败保留原 data 或恢复备份。备份放仓库外，保留至人工确认，不自动提交或修改 Git/LFS 历史。

拒绝符号链接、LFS 指针、源/目标越界、目标和 ID 碰撞。已迁移且与清单一致时重复 apply 无修改；混合或不匹配状态失败。

## 持久兼容格式

data/migration-map.toml 固定 version=1：

- contents 数组：kind、old-source、directory、id；迁移前已发布且有详情页的内容另含 old-url。
- assets 数组：old-url 为旧共享图片 URL，source 为相对 data 的迁移目标文件系统路径，不是 URL 编码的字符串。生成器可结合 contents 的 kind/id 重定位后续改名的内容包，保持旧别名可用。

旧文章地址按旧标题安全化算法生成，不从文件名猜测。清单是兼容输入，不是实现历史，不参与正文扫描；构建不回写。内容包标题或目录后续调整时保留身份与必要映射；运行图片维护工具前同步清单中的目录/source 路径，不改 ID 或 old-url。

图片预处理若将 PNG 转为 JPG，要同步更新 meta.toml preview、语言 Markdown 及 assets.source，保留 old-url。旧 .png 别名输出实际 PNG，而非只给 JPEG 改扩展名；GIF 不跨格式转换。完整构建不自动运行图片预处理。
