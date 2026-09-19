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

## Build

Requires JDK 21, Rust with `wasm-pack`, and the Sass CLI. Run from the repository root:

1. Set the local content directory in `src/main/kotlin/Main.kt`. It must contain `blogs/`, `devlogs/`, `portfolio/` and `actives/`. Each Markdown file starts with a fenced `toml` metadata block; fields are defined in `Models.kt`. Content is not included in this repository.
2. Generate the site and assets:

   ```sh
   ./gradlew run # Windows: .\gradlew.bat run
   bash compileScript.sh
   sass src/main/stylesheet:static/assets/css --style expanded
   ```

Serve `static/` with a static HTTP server. For stylesheet development, run `bash compileSCSS_watch.sh`.
