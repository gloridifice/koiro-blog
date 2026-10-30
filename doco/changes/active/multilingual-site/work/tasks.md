# Execution tasks

用户已授权开始执行。按依赖实施并记录验证；仅在实际验证后勾选。未获授权自动 complete/archive 或提交 Git。

Blocked: none.

- [x] 1.1 固定迁移映射及执行前基线
  - Design: [迁移算法](implement.md#45-迁移与-markdown-互链)
  - Acceptance: 重新检查源码、Git 状态和全部 data 文件；将 [D2 映射](specs/data-migration.md#d2-源文件到内容包的映射)转为显式迁移输入，补齐新出现内容；确认无 ID/目标/旧地址碰撞。该任务只生成计划，不 apply。覆盖 D-A1、D-A4。

- [x] 1.2 实现语言注册、中文字典及参数格式化
  - Design: [语言与文案](implement.md#31-语言与文案计划新增-i18n-包)
  - Acceptance: 新增配置与中文字典、空英文字典；严格校验基础键、命名参数、语言引用，非中文缺键去重警告。按 M-A1–M-A6、M-A8–M-A10 验证；不编写英文译文。

- [x] 1.3 实现内容包加载、校验和译本选择
  - Design: [内容模型](implement.md#32-内容模型计划新增-content-包)
  - Acceptance: 使用临时内容包验证 [C-A1–C-A9、C-A13–C-A14](specs/content-localization.md#acceptance-scenarios)；严格区分公共发布与译本发布、目标语言与实际语言，草稿坏元数据有来源错误。不得为测试保留只由测试调用的旧模型适配器。

- [x] 2.1 实现统一路由、排序和分页计划
  - Dependencies: 1.2, 1.3
  - Design: [路由上下文](implement.md#33-路由和渲染上下文计划新增-routing-包)、[集合与分页](implement.md#41-发布集合选择与分页)
  - Acceptance: 相同内容 ID 生成各语言地址；各语言拥有相同发布集合和页码；动态/项目使用稳定锚点；同日期排序确定。保护 [C-A1、C-A10](specs/content-localization.md#acceptance-scenarios)、[O-A1、O-A2、O-A8](specs/localized-output.md#acceptance-scenarios) 及现有分页边界。

- [x] 2.2 将页面组件接入显式上下文
  - Dependencies: 2.1
  - Design: [总体依赖](implement.md#2-overall-approach)、[页面与声明](implement.md#42-页面与声明)
  - Acceptance: 替换导航、分页、主页/关于、分类/项目标签及 head/RSS 入口硬编码；移除全局可变当前导航页状态，文章 title 使用实际标题，正文 lang 正确。验证 M-A7 和 O-A2、O-A3，不回归原 Markdown、图片尺寸、GIF 与懒加载行为。

- [x] 2.3 实现正式翻译声明和已确认侧边栏视觉
  - Dependencies: 2.2
  - Design: [页面与声明](implement.md#42-页面与声明)
  - Acceptance: 按 [C5](specs/content-localization.md#c5-缺译与-ai-翻译提示)、[O5](specs/localized-output.md#o5-侧边栏语言入口视觉定稿) 输出 HTML/SCSS；满足 C-A8、C-A9、C-A11、C-A12、C-A14、O-A11。卡片右下角图标、动态末尾行、详情信息框外下方行位置准确；黄色同时作用于缺译图标和文字；不复制 dist 模拟查询参数和硬编码样例状态。

- [x] 2.4 实现根入口与语言浮窗交互
  - Dependencies: 2.3
  - Design: [入口和持久化](implement.md#43-语言入口和持久化)
  - Acceptance: 小型原生 JS 使用生成配置，支持有效偏好优先、保守浏览器匹配、普通链接及无脚本降级。按 [O-A4–O-A9、O-A14](specs/localized-output.md#acceptance-scenarios) 在真实浏览器验证，存储失败不阻止导航；直接语言 URL 不重定向，浮窗支持键盘及边界定位。

- [x] 3.1 实现内容迁移器和资源引用迁移
  - Dependencies: 1.1, 1.3
  - Design: [迁移算法](implement.md#45-迁移与-markdown-互链)
  - Acceptance: 默认 dry-run，临时目录构造及备份恢复机制明确；处理公共/语言元数据拆分、snake_case 包、编码图片路径与封面、未知历史字段；输出持久兼容映射。用临时目录覆盖 [D-A1–D-A5](specs/data-migration.md#acceptance-scenarios)，证明 dry-run 无写入、冲突前失败、重复 apply 无变化。暂不对仓库 data apply。

- [x] 3.2 接入本地文章互链解析与封面预处理
  - Dependencies: 2.1, 3.1
  - Design: [互链与预处理](implement.md#45-迁移与-markdown-互链)
  - Acceptance: Markdown 真实链接目标按索引转换，代码块示例和外链不改；显式语言链接保留。prepare_images 同时更新 meta.toml preview，保持资源发布/引用更新/旧图删除顺序。验证 [D-A6、D-A7](specs/data-migration.md#acceptance-scenarios) 和既有图片准备回归。

- [x] 3.3 输出多语言 RSS、SEO 与兼容入口
  - Dependencies: 2.2, 3.1
  - Design: [输出与兼容](implement.md#44-输出rss-与旧链接)
  - Acceptance: 每语言 feed 为合法 XML，标题、说明、链接与选中译本一致；根 feed 为中文 XML，旧文章及首页链接有中文跳转、旧图片可访问；真实译本与回退副本 SEO 不混淆。验证 [O-A10、O-A12、O-A13](specs/localized-output.md#acceptance-scenarios)，并覆盖 XML 特殊字符和合法 feed 日期。

- [x] 4.1 更新构建产物校验与安全增量清理
  - Dependencies: 2.4, 3.2, 3.3
  - Design: [输出清单](implement.md#44-输出rss-与旧链接)
  - Acceptance: Python 校验遍历语言入口、feed 和本地链接；生成器只清理自身清单内过期产物，保留无关文件并拒绝越界清单。新增/撤下译本、缩短分页及移除语言后，无过期生成页面残留。保留现有 LFS、符号链接、输入输出重叠和资源越界保护。

- [x] 4.2 完成边界测试与回归适配
  - Dependencies: 4.1
  - Design: [验证归属](implement.md#6-verification-and-documentation-impact)
  - Acceptance: 各实现任务同步添加的测试通过；统一审查覆盖缺口和重复用例。生成输出/DOM 由 Kotlin 测试承担，文件迁移和构建验证由 Python 承担，浏览器仅补交互和布局风险；每个新增测试有独立契约，不用源码 grep 或测试专用生产接口。不得通过删掉断言来掩盖图片、分页或 Markdown 回归。

- [x] 5.1 审核 dry-run 并迁移仓库真实 data
  - Dependencies: 4.2
  - Design: [迁移规格](specs/data-migration.md)
  - Acceptance: 获得执行授权后先备份并审阅 dry-run；应用前确认映射、未引用资源和失效引用报告。应用后所有旧内容有对应中文包，发布日期和发布状态不变，媒体哈希一致；只修改必要引用，不新增英文译文，不自动设 AI=true，不提交或删除备份。满足 D-A1–D-A5。

- [x] 5.2 更新已交付的当前文档
  - Dependencies: 5.1
  - Design: [当前文档影响](implement.md#当前文档影响)
  - Acceptance: 按实际交付边界填写 [当前架构](../../../../architecture.md)，同步当前内容/字典/输出及兼容 specs，更新 README、部署和图片准备说明；不把计划或 dist-only 模拟行为写成事实，不把一次性工作清单复制到永久规格。

- [x] 6.1 完成全量验证并整理交付结果
  - Dependencies: 5.2
  - Design: [必要验证](implement.md#必要验证)
  - Acceptance: 下列执行阶段命令、桌面/手机预览和核心验收场景均有实际结果；完整重建能复现确认 UI 且无 preview 脚本，Git diff 不包含未授权翻译。更新 proposal Result 为实际交付摘要；只在另获授权时执行 doco complete/archive，不因测试通过自动切换生命周期。

## Verification

### 本次文档补齐实际执行

- 读取源码、现有生成/分页/图标测试及图片预处理入口；未运行正式实现测试。
- 核对 41 个 data Markdown 与映射：无遗漏、无过期源、无重复源/ID，全部目标内容包名符合 snake_case；7 份变更文档的相对链接、标题锚点、模板残留和行末空白检查通过。
- `doco check multilingual-site`：机械通过，仅提示 15 项任务尚未执行；无缺失引用或模板残留错误。机械通过不代表实现验收通过。

### 执行阶段 Verification

- `gradlew.bat --no-daemon test`：18 个 Kotlin 测试通过（新内容模型、译本与双状态、文案校验、互链、旧地址、输出清单防越界，以及原分页/图片/Markdown/图标回归）。
- Python `unittest discover -s scripts -p 'test_*.py'`：18 个测试通过，无跳过；ExifTool 13.59 从官方 tag 源码临时解包，以系统 Perl 启动，仅在测试进程 PATH 中可用，未安装到项目或修改全局 PATH。初次缺工具时的跳过/失败不计为通过。
- 对完整 data 临时副本执行迁移和实际 Gradle 生成：41 个内容包、142 个附件副本、136 个旧图片别名；生成 230 个语言/兼容文件，`validate_site` 包括页面、RSS、站内链接和本地资源检查通过；真实 data 此时尚未 apply。
- Chromium 实测根入口语言匹配、已保存偏好、无效偏好、存储拒绝、语言切换、Escape 关闭，以及禁用 JS 时的入口和正文可读性。桌面和 390px 视口无横向溢出；三个 SVG 最终 computed color 均为 rgb(110,108,108)，gap=4px；详情提示位于信息框外。修复原固定脚本的转义后 pageerror 为零。
- 源数据发现 10 篇旧 TOML 重复 date，逐一对照当前 dist 的 24 篇已发布详情日期，最后一个 date 与全部现有显示一致。迁移保留有效值、将较早值保存为 legacy_dates；非翻译或发布日期变更。

- 真实迁移：运行 python scripts/migrate_content.py data --apply 已执行，报告为 41 包、142 附件副本、136 旧图片别名，未引用资源为零；再次 --apply 返回 already_migrated=true、changed=false。备份为 `C:\Users\linyifan05\AppData\Local\Temp\koiro-data-backup-xis_1xrc\data`，未删除。
- 完整构建：python scripts/build_site.py 已通过（Kotlin 测试与生成、Sass、Rust/Wasm、最终产物校验）；静态根 index 输入已移除，由生成器生成。正式输出不含此前 preview 注入脚本。
- 当前 architecture、4 份当前 specs、README 的结构说明、部署与图片准备文档已按实际实现更新；CI 工具测试补入迁移器用例。执行环境为 Windows/JDK 21 和 Chromium；尚未运行 Linux/macOS CI、Safari 或 Firefox。

### 最终 Verification

- 最终完整 build 成功，18 个 Kotlin 测试通过；独立 Python 全套 19 个测试通过，无跳过，随后单独产物 check 再次通过。
- 新附件命名空间 images/content 与历史别名隔离；新增跨平台大小写清单测试。Windows 产物的 230 个清单路径逐段匹配实际文件名大小写，避免 Linux 发布断链。
- 迁移后再次对照仓库外备份：41 个正文（仅允许本地 URL 目标变化）、标题、日期、发布标记不变；包含草稿的 144 个附件引用逐项哈希一致，136 个公开附件别名源哈希一致；没有 en-US.md，没有将任何原文 ai-translation 设为 true。
- 最终真实 dist 的 Chromium 检查：键盘 ArrowDown 进入当前语言、Escape 和点击外部关闭、手动切换后 URL/偏好正确；首页与文章 390px 无横向溢出。缺译文字及图标均为 rgb(197,163,91)，图标按钮为弱灰、透明背景、0px 边框；项目说明同色。详情提示在完整标题框外下方，pageerror 为零。
- 稳定 ID 回归覆盖标题和存储目录改名：语言详情地址保持原 ID，历史附件通过内容身份重定位后仍保留原别名；输出目录越界和重复 ID 不会静默覆盖或删除外部文件。
- UI 提示无状态查询参数或预览注入代码；英文站使用真实缺译回退，不是假定内容已经译为英文。
- 当前文档已同步；不执行 complete/archive、Git 提交或远程发布。仍有原有 Rust unused import 等非阻塞编译警告，未借机修改无关代码。非 Windows/Chromium 平台未实测。

### 命令清单（实际结果以上述记录为准）

```sh
# 按生产行为扩展测试后执行
./gradlew --no-daemon test
python -m unittest discover -s scripts -p 'test_*.py'

# 迁移器为计划新增；先 dry-run 并审阅，再在获授权后 apply
python scripts/migrate_content.py data
python scripts/migrate_content.py data --apply

# 构建和产物校验（完整 build 会覆盖 dist-only 预览）
python scripts/build_site.py
python scripts/build_site.py check

git diff --check
doco check multilingual-site
```

Windows 使用 `gradlew.bat`。图片工具测试如需 ExifTool，先满足依赖，不能把依赖缺失当功能通过。最终核对源文件集合、元数据和媒体哈希；在 390px 与桌面视口检查弱色/黄色提示和浮窗；实际试验缺英文、空英文字典、JS 禁用、存储不可用、旧链接、RSS 订阅及回退副本索引标记。
