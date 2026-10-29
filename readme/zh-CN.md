# Koiro's Cat Café

[English](../README.md) | 简体中文 | [日本語](ja-JP.md) | [Français](fr-FR.md)

使用本地 Markdown 和 TOML 元数据的静态博客生成器，无需 Notion 账户或 API。

![主页预览](main_page.jpg)

## 从源码构建

需要 Python 3.11+、JDK 21、Node.js 24、Git LFS，以及通过 rustup 安装的 Rust。将 `JAVA_HOME` 指向 JDK 21。`rust-toolchain.toml` 指定 Rust 版本及 Wasm 目标；构建时按 npm 锁文件安装 Sass。

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
python scripts/build_site.py
python -m http.server 8000 --directory dist
```

打开 <http://localhost:8000>。如果系统中的 Python 命令是 `python3`，请用它代替 `python`。构建命令见 `python scripts/build_site.py --help`。

## 技术栈

- Kotlin/JVM 21、kotlinx-html、JetBrains Markdown 和 ktoml
- Rust/WebAssembly 实现浏览器交互
- Dart Sass 编译 SCSS
- Python 负责跨平台构建和产物校验

## 目录结构

- `src/main/kotlin/`：内容解析、HTML 和 RSS 生成，入口为 `Main.kt`。
- `src/main/rs-wasm-script/`：浏览器交互。
- `src/main/stylesheet/`：样式源码。
- `assets/`：受追踪的静态输入，复制到 `dist/`；内层 `assets/` 保留现有 `/assets/...` URL。
- `data/`：Markdown 和附件，分为 `blogs/`、`devlogs/`、`portfolio/`、`actives/`。每篇 Markdown 以 TOML 代码块开头，字段定义见 `Models.kt`。
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
