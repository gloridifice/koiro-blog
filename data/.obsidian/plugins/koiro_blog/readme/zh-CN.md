# koiro_blog

[English](../README.md) | 简体中文 | [日本語](ja-JP.md) | [Français](fr-FR.md)

在 Obsidian 中预览博客 HTML 组件，并创建、编辑、改名和移动 TOML 文件。

## 快速开始

### 安装

在 `data/.obsidian/plugins/koiro_blog/` 中构建，`data/` 库会直接从该目录加载插件，无需复制。安装到其他库时，将 `main.js`、`manifest.json`、`styles.css` 复制到 `<库>/.obsidian/plugins/koiro_blog/`。这三个文件名为 Obsidian 固定要求。先停用并移除旧版 Component Renderer，再重载 Obsidian，在第三方插件中启用 **koiro_blog**。只在组件代码可信的库中启用。

### 更新

停用插件，在原目录重新构建（其他库则替换上述三个文件），再重新启用。

## 功能

- 文件树显示 TOML 文件，支持原生改名、移动和自动保存的纯文本编辑。
- 文件或目录右键菜单提供 **New TOML file**，创建并打开空文件，重名时递增命名而不覆盖。
- 支持阅读视图和 Live Preview，不改变源码模式。
- 精确识别 `html`、`component` 两个标记，区分大小写，顺序不限。
- 支持内联 HTML、CSS、JavaScript，DOM 和脚本作用域独立。
- 复用博客的自动高度脚本，上限为 10000px。
- 库内相对图片、媒体、经典脚本和样式表以 data URL 嵌入。
- 离线提供 Kotlin 显式注册的组件预设，图片对比只需 HTML；不扫描目录自动加载。

````markdown
```html component
<button onclick="this.textContent = '已点击！'">点我</button>
```
````

在 Live Preview 中，将光标移入代码块即可编辑，移出后重新运行。阅读视图可能缓存仅修改标记的结果；需要时执行 **koiro_blog: Refresh reading-view components** 命令。无法可靠定位源码时，阅读视图保留原代码，不猜测执行。

## 从源码构建

需要 Node.js 24+、JDK 21 与仓库的 Gradle wrapper。在插件目录运行：

```sh
npm ci
npm run build
```

构建执行 `exportComponentAssets` 导出 Kotlin 清单，只打包其中登记的 `assets/component/` CSS/JS 和共享高度脚本。修改预设或注册表后需重建插件。安装后的插件无需仓库文件、服务器、博客生成产物、根目录 npm 依赖或 Java 运行时。

## 开发

```sh
npx playwright install chromium
npm test
```

也可设置 `BROWSER_CHANNEL=chrome` 或 `msedge`，使用已安装浏览器。这些测试覆盖标记识别和真实 Chromium/CodeMirror 渲染。验证 TOML 客户端集成时，将 `OBSIDIAN_EXECUTABLE` 设为桌面客户端路径后执行 `npm run test:obsidian`；测试使用隔离的临时库，不操作日常库。

详见[行为与限制](../../../../../doco/specs/obsidian-koiro-blog.md)。沙箱不阻止联网或限制资源消耗；CSS/JS 的依赖 URL 不自动解析，也不自动继承博客主题变量。
