# Koiro's Cat Café

English | [简体中文](readme/zh-CN.md) | [日本語](readme/ja-JP.md) | [Français](readme/fr-FR.md)

A static blog generator powered by local Markdown files with TOML metadata. No Notion account or API is required.

![Main Page Showcase](readme/main_page.jpg)

## Build from source

Requires Python 3.11+, JDK 21, Node.js 24, Git LFS and Rust installed through rustup. Set `JAVA_HOME` to JDK 21. `rust-toolchain.toml` selects the Rust version and Wasm target; Sass is installed from the npm lockfile during the build.

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
python scripts/build_site.py
python -m http.server 8000 --directory dist
```

Open <http://localhost:8000>. On systems where Python is named `python3`, use that command instead. Run `python scripts/build_site.py --help` for build commands.

## Stack

- Kotlin/JVM 21, kotlinx-html, JetBrains Markdown and ktoml
- Rust/WebAssembly for browser interactions
- SCSS compiled with Dart Sass
- Python for cross-platform build orchestration and output validation

## Structure

- `src/main/kotlin/` — content parsing, HTML and RSS generation; entry point: `Main.kt`.
- `src/main/rs-wasm-script/` — browser interactions.
- `src/main/stylesheet/` — stylesheet sources.
- `assets/` — tracked static inputs copied into `dist/`. The nested `assets/` preserves `/assets/...` URLs.
- `data/` — Markdown and attachments, grouped into `blogs/`, `devlogs/`, `portfolio/` and `actives/`. Each Markdown file starts with fenced TOML metadata; fields are defined in `Models.kt`.
- `dist/` — complete generated site, ignored by Git and recreated on every full build.

## Git LFS

Binary images (`png`, `jpg`, `jpeg`, `gif`, `webp`, `avif`, `ico`) and fonts (`otf`, `ttf`, `woff`, `woff2`) use Git LFS. Markdown, code and SVG files remain ordinary Git text.

New assets become LFS pointers when staged. For already tracked binary files, use `git add --renormalize -- <asset-paths>` before committing, then review `git lfs status`. This does not rewrite existing history.

CI checks out actual LFS objects and publishes ordinary asset bytes to the separate Pages repository. GitHub Pages does not serve LFS pointers.

## Deployment

GitHub Actions builds on `master` pushes, pull requests and manual runs. Publishing to a separate Pages repository is disabled until its repository variables and deployment secret are configured. See [deployment setup](docs/deployment.md), including precautions after the local history rewrite.

## Development

- [Build, validation and deployment](docs/deployment.md)
- [Prepare content images before staging](docs/image-preparation.md)
