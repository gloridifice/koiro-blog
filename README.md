# Koiro's Cat Café

A static blog generator powered by local Markdown files with TOML metadata. No Notion account or API is required.

![Main Page Showcase](readme/main_page.jpg)

## Stack

- Kotlin/JVM 21, kotlinx-html, JetBrains Markdown and ktoml
- Rust/WebAssembly for browser interactions
- SCSS for styles

## Structure

- `src/main/kotlin/` — content parsing, HTML and RSS generation; entry point: `Main.kt`.
- `src/main/rs-wasm-script/` — browser interactions.
- `src/main/stylesheet/` — stylesheets.
- `static/` — generated site and static assets.
- `data/` — Markdown content and attachments, grouped into `blogs/`, `devlogs/`, `portfolio/` and `actives/`.

## Git LFS

Binary images (`png`, `jpg`, `jpeg`, `gif`, `webp`, `avif`, `ico`) and fonts (`otf`, `ttf`, `woff`, `woff2`) use Git LFS. Markdown, code and SVG files remain ordinary Git text.

Install [Git LFS](https://git-lfs.com/) (`brew install git-lfs` on macOS), then run from this repository:

```sh
git lfs install --local
git lfs pull
```

New assets become LFS pointers when staged. For already tracked binary files, use `git add --renormalize -- <asset-paths>` before committing, then review `git lfs status`. This does not rewrite existing history. CI must also fetch LFS objects before building (with `actions/checkout`, set `lfs: true`).

If `static/` is a separate GitHub Pages checkout, keep its Git configuration independent and publish actual asset files, not LFS pointers; GitHub Pages does not serve LFS objects.

## Build

Requires JDK 21, Rust with `wasm-pack`, and the Sass CLI. Run from the repository root:

1. Place content in the repository's `data/` directory, which is read relative to the working directory. It must contain `blogs/`, `devlogs/`, `portfolio/` and `actives/`. Each Markdown file starts with a fenced `toml` metadata block; fields are defined in `Models.kt`.
2. Generate the site and assets:

   ```sh
   ./gradlew run # Windows: .\gradlew.bat run
   bash compileScript.sh
   sass src/main/stylesheet:static/assets/css --style expanded
   ```

Serve `static/` with a static HTTP server. For stylesheet development, run `bash compileSCSS_watch.sh`.
