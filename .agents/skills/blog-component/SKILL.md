---
name: blog-component
description: 为本博客编写 html component 交互组件，复用宿主预设的项目风格样式及无边框 A/B 图片对比。用于博客组件、交互图、参数实验、图片滑动对比，以及新增或注册公共组件 CSS/JS。介绍 assets/component 资源和 Kotlin 显式注册方式；默认只交付组件本体，不生成展示站或外层说明页，不自动扫描或注入整个资源目录。
---

# 博客交互组件

## 先确定范围

1. 读取 [设计约定](references/design.md)，再按任务选择：
   - 面板、计算器、图解：读取 [公共样式](../../../assets/component/component.css)，参考 [折射示例](examples/refraction.html)。
   - 图片对比：读取 [用法](references/image-compare.md) 和 [示例](examples/image-compare.html)。通常只写 figure、两张图片和 caption，不再复制滑杆 CSS/JS。
   - 新增公共样式或行为：读取 [注册步骤](references/registration.md)，检查 [Kotlin 注册表](../../../src/main/kotlin/htmlgen/component/ComponentAssets.kt)。
2. 沿用用户的图片、caption、公式、变量名、语言、默认值和精度。不要把示意素材当作用户内容。
3. 无法确认的图片对齐条件、物理或算法条件先说明，不捏造计算结果。

路径相对于本 skill 目录；仓库根位于 `../../..`。不需要读取临时风格比较页。

## 交付格式与默认范围

使用区分大小写的完整 `html` 和 `component` token，仍然是普通 HTML，不引入新的围栏语法：

````markdown
```html component
<figure class="kc-compare">
  <img data-side="a" src="./a.png" alt="A 图">
  <img data-side="b" src="./b.png" alt="B 图">
  <figcaption>两种处理结果的对比</figcaption>
</figure>
```
````

图片对比只显示左 A、右 B、中央滑杆及下方 caption。无面板边框、圆角、阴影、标题或额外操作区。

参数实验等面板按需组合：

```text
.kc-root
└── .kc-panel（需要统一边界时）
    ├── 顶栏：导航 / 当前模式（可选）
    ├── 主区域：图解、画布、表格或结果
    ├── 参数和操作区（按需）
    └── 数值结果 / 状态反馈（可选）
```

不要自动添加外层标题、导语、品牌栏、风格选择器、设计参数、原理说明卡片、源码展示或页脚。只保留组件名、标签、单位、变量、caption、错误及必要说明等功能性文本；不加口号、装饰性英文和无用途编号。无障碍名称、文档或 SVG 的 title/desc 不属于装饰。

## 公共资源与注册

- 正式资源位于 `assets/component/`，不在 skill 内维护第二份代码。
- **唯一自动注入清单**是 `ComponentAssets.kt` 中的 `registered.css` / `registered.js`。放入目录不等于注册，禁止目录遍历、glob 或按扩展名自动收集全部文件。
- 博客对每个组件 iframe 注入已注册 CSS/JS；Obsidian 构建导出同一 Kotlin 清单，将这些资源打包为离线 data URL。普通 HTML 代码块及外站 iframe 不参与。
- CSS 按注册顺序放在作者样式之前；JS 按注册顺序加载，行为只对明确标记的 DOM 初始化。CSS 必须限定在 `.kc-*` 作用域内，不用全局选择器给任意组件加样式。
- 面板使用 `.kc-root` / `.kc-panel`，图片对比使用 `.kc-compare`。不要为了使用图片对比而套面板。
- 已注册 CSS/JS 不需要在文章里再次 link、script 或内联。组件特有的计算、绘图和局部样式仍在围栏中编写。
- Skill 示例的 link/script 仅用于浏览器直接预览。交付到支持预设的博客/插件时省略它们；脱离这些宿主的独立 HTML 才需要显式加载或内联公共文件。
- 样式是项目近似采样，不是宿主主题或字体的动态继承。字体使用系统无衬线和 JetBrains Mono 的本地回退，不默认加载网络字体。

新增注册不是例行写文章的步骤。确需新增公共行为时按 [注册步骤](references/registration.md) 修改资源、清单、验证和文档；不要把整份页面业务脚本注册到所有 iframe。

## 自定义交互

- 优先原生 button、input、select、output、table；按钮声明 `type="button"`，输入绑定可访问名称。
- 分段导航只在有多个视图时使用，支持选中态、焦点顺序、左右键及 Home/End。
- 输入驱动真实计算与图形。无效时说明原因、保留有效结果、停用依赖无效参数的操作，不能显示 NaN/Infinity 或静默改值。
- 指标带单位；图解位置与数据同步；不只用颜色表达区别。
- 重置、记录上限、清空范围和保存周期必须明确。默认只在内存保存，不擅自加入持久化。
- 脚本使用 IIFE 或局部作用域；动态文本用 textContent。公共脚本无匹配组件时必须不做事，重复初始化不能重复绑定。
- 不添加无意义的自动动画；确需动画时提供暂停并尊重减少动态偏好。

## 沙箱与资源边界

- iframe 只允许 `sandbox="allow-scripts"`。预设不增加同源权限，也不提供宿主 DOM、Cookie、存储、Obsidian API 或任意文件访问桥。
- body 透明、无外边距；按内容撑高，不用 `100vh` 或依赖 iframe 高度的百分比布局。高度测量由宿主运行时处理，不复制其消息协议。
- 图片等资源通过 HTML src/href 明确引用，相对路径以目标文章/笔记为基准。博客限于允许的内容目录，Obsidian 限于 Vault；不是以 skill 目录为基准。
- CSS url()、JS import/fetch、srcset 和传递依赖不自动重写。公共预设也不能依赖这些隐式资源解析。
- 保留作者自包含 HTML/CSS/经典 JS 的写法；不要求所有新组件都进入公共注册表。

## 验证与交付

1. 检查 740px、375px，必要时 320px；无页面横向溢出，caption 换行，控件不整体缩小。
2. 面板验证输入边界、无效值、计算及实际提供的操作；图片对比验证左右关系、0/50/100%、透明图、鼠标/触摸/键盘、加载失败与多实例。
3. 在真实博客生成结果和仅允许脚本的 iframe 中检查；公共资源变更还要构建、验证 Obsidian 离线版本。
4. 注册变更必须验证：目录中未注册的 CSS/JS 不会加载或执行，普通自定义组件不被预设样式影响。
5. 不为测试增加生产接口，不复制展示页或未使用的控件，不把未做的检查写成通过。
6. 用户要代码时直接给完整围栏；要文件时给路径。仅修改本次组件或注册需要的文件，不提交工作区已有的无关变更。
