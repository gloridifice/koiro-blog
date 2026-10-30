# Koiro's Cat Café

[English](../README.md) | 简体中文 | [日本語](ja-JP.md) | [Français](fr-FR.md)

使用本地 Markdown 和 TOML 元数据的静态博客生成器，无需 Notion 账户或 API。

![主页预览](main_page.jpg)

## 从源码构建

需要 JDK 21、Node.js 24、Git LFS，以及通过 rustup 安装的 Rust。将 `JAVA_HOME` 指向 JDK 21。`rust-toolchain.toml` 指定 Rust 版本及 Wasm 目标；`npm ci` 安装锁定的构建依赖。

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
npm ci
npm run dev
```

打开终端打印的地址，默认端口为 8000，被占用时自动选择后续空闲端口。`npm run dev` 在缺少产物时先构建，再提供 `dist/` 本地服务并监听 SCSS；修改后刷新浏览器即可。`npm run build` 用于完整的生产构建。

## 技术栈

- Kotlin/JVM 21、kotlinx-html、JetBrains Markdown 和 ktoml
- Rust/WebAssembly 实现浏览器交互
- Dart Sass 编译 SCSS
- Node.js/npm 负责跨平台构建、产物校验和本地开发

## 目录结构

- `src/main/kotlin/`：内容解析、HTML 和 RSS 生成，入口为 `Main.kt`。
- `src/main/rs-wasm-script/`：浏览器交互。
- `src/main/stylesheet/`：样式源码。
- `assets/`：受追踪的静态输入，复制到 `dist/`；内层 `assets/` 保留现有 `/assets/...` URL。
- `data/`：分为 `blogs/`、`devlogs/`、`portfolio/`、`actives/` 的内容包。每个英文 `snake_case` 文件夹包含 `meta.toml`、`zh-CN.md`、可选译本和共享附件，详见[内容契约](../doco/specs/content-localization.md)。
- `i18n/`：语言注册和 TOML 页面字典。中文为基础字典，默认访问英文。已提供英文界面和 AI 翻译内容；缺译时仍回退中文。
- `dist/`：完整生成站点，Git 忽略，每次完整构建都会重建。

## Git LFS

二进制图片（`png`、`jpg`、`jpeg`、`gif`、`webp`、`avif`、`ico`）及字体（`otf`、`ttf`、`woff`、`woff2`）使用 Git LFS；Markdown、代码和 SVG 使用普通 Git 文本存储。

新资产在暂存时转换为 LFS 指针。已追踪的二进制文件可在提交前执行 `git add --renormalize -- <asset-paths>`，再检查 `git lfs status`。这不会改写已有历史。

CI 拉取真实 LFS 对象，并向独立的 Pages 仓库发布普通资产文件。GitHub Pages 不会提供 LFS 指针对应的文件内容。

## 部署

GitHub Actions 在 `master` 推送、PR 和手动运行时构建。配置仓库变量及部署密钥前，不会向独立的 Pages 仓库发布。详见[部署配置](../docs/deployment.md)，包括本地历史改写后的注意事项。

## 开发

- [构建、校验与部署](../docs/deployment.md)
- [暂存前预处理内容图片](../docs/image-preparation.md)
