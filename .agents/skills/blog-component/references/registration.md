# 新增公共 CSS / JS

## 唯一注册入口

[ComponentAssets.kt](../../../../src/main/kotlin/htmlgen/component/ComponentAssets.kt) 的 `registered` 是唯一清单：

```kotlin
val registered = ComponentAssetManifest(
    css = listOf("component.css", "image-compare.css"),
    js = listOf("image-compare.js"),
)
```

文件名相对于仓库 `assets/component/`。站点复制静态目录后发布为 `/component/...`，不是 `/assets/component/...`。

禁止扫描目录、glob、递归遍历或按 `.css` / `.js` 扩展名自动注入。未注册文件可以随静态目录被发布，但不会作为预设加载或执行；注册表控制自动执行，不是资源访问控制。作者显式引用脚本的既有能力不变。

## 新增步骤

1. **确定是否应共享。** 只在一篇文章使用的公式、实验数据或业务逻辑留在围栏内。注册脚本会进入所有组件 iframe，不能默认运行整份实验。
2. **创建文件。** 放到 `assets/component/`，使用相对文件名，不放在 `.agents/skills` 中。
   - CSS 用独有 `.kc-xxx` 作用域；不要全局覆盖 body、img、button、input 或作者主题。
   - JS 使用经典脚本、局部作用域；在 DOM 可用后只查找自己的组件标记，没有匹配节点时不做事。
   - 初始化应幂等；不能重复插入控件或绑定事件。动态内容用 textContent，不增加宿主/Vault 消息桥。
   - 避免外部依赖、模块 import、fetch、CSS url()；它们不会自动按注册目录解析、打包或复制传递依赖。
3. **手动注册。** 在 `registered.css` 或 `registered.js` 末尾添加对应文件名。例如 `"plot.css"` / `"plot.js"`；有依赖时显式调整列表顺序。不要在 Node/Obsidian 中维护第二份清单。
4. **验证博客。** 在仓库根运行：
   ```sh
   ./gradlew test exportComponentAssets
   npm run test:components
   npm run build
   ```
   已安装 Chrome 时可用 `BROWSER_CHANNEL=chrome npm run test:components`。完整构建还需要项目原有的 Sass、Rust/Wasm 环境。
5. **构建插件。** 在 `data/.obsidian/plugins/koiro_blog/` 运行：
   ```sh
   npm ci
   npm run build
   BROWSER_CHANNEL=chrome npm test
   ```
   构建需要 Node.js 24+、JDK 21 和仓库 Gradle wrapper；安装好的插件不需要这些工具。
6. **更新用法。** 在本 skill 的介绍、相关 reference 和示例中说明新组件的最小 HTML、参数及限制。不要让文章依赖 skill 路径，也不要复制第二份公共实现。

## 注入与离线打包

- 博客的 `ArticleContent` 调用 `ComponentAssets.inject`。CSS 按清单顺序放在作者样式之前；经典 JS 以 defer 按清单顺序加载。样式正常级联，作者可在围栏内覆盖局部规则。
- `exportComponentAssets` 执行 Kotlin 导出入口，生成 `build/generated/component-assets.json`，并检查注册文件的路径、类型和存在性。该文件是构建产物，不能手改或作为第二份配置提交。
- 插件构建的 `component-assets.mjs` 每次先执行导出；失败即终止，不回退到旧清单。随后只读取清单列出的资源，编码为 data URL 并打包。
- 插件在 iframe 内按相同顺序加载资源，CSS 仍先于作者样式；不从 Vault、博客服务器或插件安装目录临时扫描资源。
- 修改公共文件或 Kotlin 注册表后必须重建博客输出及插件；已安装插件不会自动同步仓库更新。

## 验收重点

- 新文件存在但没有注册时，博客与插件都不自动加载；禁止把「放进去即运行」作为便利功能。
- 公共行为只激活自己的 HTML 标记，普通自定义组件的颜色、布局、脚本保持原样。
- 原生 HTML 与作者局部样式仍能使用；注册不会扩大沙箱权限。
- 图片对比仍无边框、标题或额外面板。手机、键盘、多实例、卸载和高度变化正常。
- Obsidian 的浏览器测试不是实际客户端验收；客户端兼容性未验证时明确说明。
