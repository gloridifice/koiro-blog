# 内容迁移与英文 snake_case 命名

## Scope and current contracts

本规格描述迁移 [data](../../../../../../data/) 的目标和验证方式；用户已授权执行，实际结果与备份位置记录在 tasks 及迁移报告中。当前内容仍使用独立 Markdown 和邻接资源目录；读取入口在 [KoiroCatCafe.kt](../../../../../../src/main/kotlin/KoiroCatCafe.kt)，图片相对路径处理在 [Image.kt](../../../../../../src/main/kotlin/htmlgen/Image.kt)，图片预处理在 [prepare_images.py](../../../../../../scripts/prepare_images.py)。

迁移覆盖博文、开发日志、动态及项目，包括未发布内容和模板。只将存储名称转换为英文 snake_case，不翻译标题、摘要、正文、图片说明，不生成英文译本，也不把原文标记为 AI 翻译。

关联契约：[内容包](content-localization.md)、[多语言输出与兼容](localized-output.md)。

## Requirements

### D1. 目录和稳定标识

- 内容包末级目录使用有语义的英文 snake_case，符合 `[a-z][a-z0-9]*(?:_[a-z0-9]+)*`，不使用中文、空格或连字符作为内容包名；Windows 保留名禁止使用。
- 迁移时 `meta.toml.id` 等于末级目录名。未来目录可在同样命名规则下改名，但不能自动改 ID。
- 语言文件必须是 zh-CN.md、en-US.md 等规范标识；语言文件名不使用 snake_case。元数据文件固定为 `meta.toml`。
- 博文分类目录统一为 `art`、`chore`、`game_dev`、`graphics`、`life`、`programming`。它们只是存储分组，不能替代元数据的 `class` 分类。
- 项目移除历史 Table 层，放到 data/portfolio/<id> 包目录。其他内容类型不增加分类层。
- 图片文件不强制改名；保留包内相对子目录，避免无意义的批量资源更名。可用 images 子目录 收纳原先散放在类型根目录的封面。

### D2. 源文件到内容包的映射

以下路径均相对 data 根目录；目标列表示包目录，旧 Markdown 内容移入其中的 zh-CN.md。这是基线 41 个文件的拟执行映射；执行前必须重新核对源清单，新增文件须补入映射，不能忽略或自动猜名。

| 原 Markdown | 目标内容包 |
|---|---|
| blogs/Art/王国之泪 的平面设计.md | blogs/art/tears_of_the_kingdom_graphic_design |
| blogs/Art/面向平面设计的色彩知识入门.md | blogs/art/color_theory_for_graphic_design |
| blogs/Chore/Blog 页面、样式测试.md | blogs/chore/blog_style_demo |
| blogs/GameDev/GameJam 中的时间管理.md | blogs/game_dev/game_jam_time_management |
| blogs/GameDev/GameJam 中的灵感发现.md | blogs/game_dev/finding_game_jam_inspiration |
| blogs/GameDev/UI 设计与开发经验 [Unity] 2023 2 8 版.md | blogs/game_dev/unity_ui_design_notes_2023_02_08 |
| blogs/GameDev/Unreal 5 C++ 多人游戏入门资料.md | blogs/game_dev/unreal_5_cpp_multiplayer_resources |
| blogs/GameDev/Unreal 5 垃圾回收源码梳理.md | blogs/game_dev/unreal_5_garbage_collection |
| blogs/GameDev/Unreal 5 多人网络同步源码梳理（基础）.md | blogs/game_dev/unreal_5_network_replication_basics |
| blogs/GameDev/《怪兽远征》的编辑器工具｜随记 ThinkyCon 2024.md | blogs/game_dev/monsters_expedition_editor_thinkycon_2024 |
| blogs/GameDev/使用 Bevy 反射序列化组件.md | blogs/game_dev/bevy_reflection_component_serialization |
| blogs/GameDev/八向自动连接瓦片 Tile 理论与 Bevy 实践.md | blogs/game_dev/eight_way_autotiling_in_bevy |
| blogs/Graphics/Bevy 引擎渲染架构设计概览.md | blogs/graphics/bevy_rendering_architecture |
| blogs/Graphics/在 wgpu 进行 HDRI 到 Cubemap 的转换与 IBL 预滤波环境贴图.md | blogs/graphics/wgpu_hdri_cubemap_and_ibl_prefiltering |
| blogs/Graphics/面向相机的渲染系统设计.md | blogs/graphics/camera_oriented_rendering |
| blogs/Life/2023 的秋季做了些什么.md | blogs/life/autumn_2023 |
| blogs/Life/我在 2025 年遇到的好的漫画、动画与音乐专辑 EP.md | blogs/life/favorite_manga_anime_and_music_2025 |
| blogs/Programming/Helix 治好了我的 Vim 配置焦虑.md | blogs/programming/helix_and_vim_configuration |
| blogs/Programming/Kotlin jvm 实现静态博客生成器随记.md | blogs/programming/kotlin_jvm_static_blog |
| blogs/Programming/Notion API 小记.md | blogs/programming/notion_api_notes |
| blogs/Programming/优化键盘布局，改善生活福祉.md | blogs/programming/keyboard_layout_optimization |
| blogs/Programming/游戏开发中的编程理论基础.md | blogs/programming/programming_theory_for_game_development |
| blogs/Programming/编程语言交互设计.md | blogs/programming/programming_language_interaction_design |
| blogs/template.md | blogs/blog_template |
| devlogs/UI Framework egui + wgpu + winit Environment Setup.md | devlogs/egui_wgpu_winit_setup |
| devlogs/hecs、3D 渲染系统搭建和探索更好的项目结构.md | devlogs/hecs_rendering_and_project_structure |
| devlogs/rust wgpu app 运行环境和 Phong 渲染管线搭建.md | devlogs/wgpu_app_and_phong_pipeline |
| devlogs/图像照明（IBL）与透明渲染.md | devlogs/ibl_and_transparency |
| devlogs/实现类 Bevy ECS 的 System.md | devlogs/bevy_style_ecs_systems |
| devlogs/延迟渲染管线、微表面 BRDF、法线变换、物体继承树 UI.md | devlogs/deferred_rendering_brdf_and_scene_tree |
| devlogs/渲染管线、Depth、Camera、Light、Transform、bevy_ecs.md | devlogs/render_pipeline_camera_and_bevy_ecs |
| devlogs/管线重构、Input、Egui Tiles、Shadow Mapping.md | devlogs/pipeline_input_egui_tiles_and_shadows |
| actives/GMTK GJ 2025.md | actives/gmtk_game_jam_2025 |
| actives/migrate_from_notion_to_markdown.md | actives/migrate_from_notion_to_markdown |
| actives/动态的功能.md | actives/activity_feature_design |
| actives/动态的功能完成啦.md | actives/activity_feature_complete |
| actives/最近的事 2025-6-8.md | actives/recent_updates_2025_06_08 |
| portfolio/Table/cat_with_blocks.md | portfolio/cat_with_blocks |
| portfolio/Table/koiros_cat_cafe.md | portfolio/koiros_cat_cafe |
| portfolio/Table/water_source.md | portfolio/water_source |
| portfolio/Table/wgpu_pbr.md | portfolio/wgpu_pbr |

映射只确定存储名称，不改动 `work` / `project` 的已有稳定关联值。现有开发日志包含未在作品列表中展示的项目（如 Terre），保留关联值但允许没有对应 portfolio 条目，不因迁移强行补造项目。

### D3. 元数据及资源保真

- 首个合法 fenced TOML 头部按公共与译本字段拆分。`slug` 转为 `summary`，公共 published 保留原值，中文译本 published 默认 true，AI 标记默认 false。
- 原日期、分类、标签、图标、项目关联和排序语义保持。项目日期字符串保留，不转 ISO。额外历史字段保留：作者信息放公共元数据，`last_edited_date` 留在译本头部；其他未知字段保存在原语义最接近的一侧并记录报告，禁止静默丢弃。
- 正文除必要的本地目标路径替换外保持原样，包括代码块内示例 TOML、HTML、公式和所有文字。不能用全文字符串替换重写文章中展示的代码。
- 在移动前解析每个资源引用的真实源路径，记录源文件到目标文件的映射；覆盖 Markdown 图片、参考式图片、已存在的本地 HTML 媒体属性及 TOML 封面。URL 编码和 `+` 字符按既有路径规则处理，片段与查询字符串保留。
- 图片迁移不压缩、不重编码；源与目标文件哈希必须相同。构建输出仍由现有图片压缩流程负责。
- 一张图片被多个内容包引用时，可按包复制，各副本哈希一致；多个译本共享本包图片。资源重名、大小写冲突不能静默覆盖，先分配不同子路径并更新引用。
- 对未引用资源保留原位置并列入报告，不自动删除。草稿中的空 preview 转为省略字段并记录；其他已存在但失效的路径须报告并在执行阶段明确处理，不能用空白图片掩盖。
- 本地 Markdown 文章链接迁移为目标包的对应语言 Markdown 路径，生成时解析为当前页面语言的 HTML URL。显式已带语言前缀的站内链接保持其语言选择；外部链接不改。旧站点标题 URL 可经迁移映射转为稳定 ID；无法解析的内部文章目标构建报错。旧标题锚点保留，不承诺翻译后标题锚点一一对应。

### D4. 可检查、可恢复的执行

提供 scripts/migrate_content.py（计划新增），默认 dry-run，只输出计划与冲突；`--apply` 才写源目录。生成持久兼容清单 data/migration-map.toml（计划新增），包含源 Markdown、目标包/ID、迁移前已发布的旧页面地址、旧图片输出路径到新资源的映射。清单不是内容包，不参与正文扫描，也不是任务执行日志。

兼容清单固定为 `version = 1`，包含以下数组：

```toml
version = 1

[[contents]]
kind = "blog"
old-source = "blogs/Programming/Notion API 小记.md"
directory = "blogs/programming/notion_api_notes"
id = "notion_api_notes"
# 只有迁移前已发布且具有详情页的内容记录 old-url
old-url = "/pages/blogs/Notion API 小记.html"

[[assets]]
old-url = "/images/blogs/old_image.png"
# target 相对 data 根，构建后映射至共享 images 输出；构建器不读取已不存在的旧源路径
source = "blogs/programming/notion_api_notes/images/image.png"
```

清单不记录正文、AI 模拟状态或用户偏好。contents 为全部源内容，assets 为需要兼容的旧发布资源；相同 old-url 不得映射不同目标。示例路径只是说明字段，不要求真实内容具备该图片。

执行顺序：清点与哈希 → 校验目标/冲突 → 在临时目录完整构造 → 校验新格式和引用 → 备份旧 data → 发布新 data → 验证后保留备份至人工确认。失败必须保留原 data 或可恢复备份；不删除未知文件，不自动 git add/commit，不修改 Git 历史或 LFS 策略。已迁移内容重复运行应无修改；新旧混杂或不匹配清单时停止，不能重复套目录。

旧地址按实际旧生成算法 `toFileSystemSafe(title)` 和原发布状态记录，不靠 Markdown 文件名猜测。别名冲突或与新资源路径冲突时停止。过往线上早于当前源码的地址不在可推导范围内，可后续显式追加别名。

### D5. 图片预处理的兼容

[prepare_images.py](../../../../../../scripts/prepare_images.py) 目前仅扫描 Markdown 替换 preview 和 PNG 引用；新增公共封面后必须同时处理 `meta.toml` 中的 preview；若转换图片出现在迁移清单 assets 中，还需更新其 source 路径为 JPEG，但保留 old-url，使旧 PNG 地址仍能输出合法 PNG。构建器按旧 URL 扩展生成正确格式的兼容图像，不能只给 JPEG 改扩展名。资源先发布、Markdown/TOML 引用随后更新、旧 PNG 最后删除的顺序保持，dry-run 不写源文件。该调整只保障新格式可用，不新增压缩策略。

## Boundaries, errors and compatibility

- 迁移不依赖网络翻译，不修改文章内容语言；英文 snake_case 是命名操作，不等于用户授权英文正文翻译。
- 拒绝符号链接、内容根之外的本地源引用、目标冲突、大小写碰撞及未拉取的 LFS 指针，输出具体文件路径。
- 迁移清单在 Git 中保留以生成旧链接兼容输出；构建不回写清单或 data。
- 全站构建继续以可丢弃 dist 目录 为输出，不把预览脚本或测试模拟标记当作源数据。

## Acceptance scenarios

- **D-A1 完整覆盖：** 当前 41 个 Markdown 均有明确目标；模板及草稿仍保留且不意外发布；所有内容包目录与初始 ID 为英文 snake_case，语言文件仍为 zh-CN.md。
- **D-A2 无翻译：** 迁移后未产生 en-US.md，中文正文除本地引用目标外不变，代码块内示例文本不变，AI 标记未被自动设为 true。
- **D-A3 资源保真：** 含 URL 编码中文目录、共享封面和同名图片的样例迁移后引用可解析且哈希一致，包内无相互覆盖。
- **D-A4 旧地址：** 标题与文件名不同或含斜杠时，兼容别名仍与旧生成器实际输出一致；根 RSS 为 XML，旧图片 URL 仍可访问。
- **D-A5 幂等和失败：** dry-run 不写数据；重复 apply 无变化；冲突和失效引用在替换 data 前失败，备份及未引用文件保留。
- **D-A6 工具链：** 图片预处理转换某 PNG 后，同时更新 meta.toml 封面、各语言 Markdown 引用及兼容清单的 source，不留下指向已删除 PNG 的源路径；旧 URL 字段保留且仍能提供与扩展名相符的有效图像。
- **D-A7 文章互链：** 相对 Markdown 文章链接在各语言页面中解析为当前语言的稳定 ID 地址；外部 URL 与显式语言 URL 保持不变。

## Current-document impact

交付后更新当前内容格式、迁移兼容规格以及 README、[图片准备](../../../../../../docs/image-preparation.md)、[部署](../../../../../../docs/deployment.md)。迁移清单作为持久兼容输入保留；不将临时备份或执行日志写入当前 specs。执行交付契约见 [当前迁移规格](../../../../../specs/data-migration.md)。
