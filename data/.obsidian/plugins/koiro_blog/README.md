# koiro_blog

English | [简体中文](readme/zh-CN.md) | [日本語](readme/ja-JP.md) | [Français](readme/fr-FR.md)

Preview blog HTML components and create, edit, rename and move TOML files in Obsidian.

## Quick start

### Install

Build in `data/.obsidian/plugins/koiro_blog/`; the `data/` vault loads the plugin directly from this directory, with no copy step. For another vault, copy `main.js`, `manifest.json`, and `styles.css` into `<vault>/.obsidian/plugins/koiro_blog/`. Obsidian requires these three filenames. Disable and remove the old Component Renderer installation first. Reload Obsidian and enable **koiro_blog** under Community plugins. Only enable it for vaults whose component code you trust.

### Update

Disable the plugin, rebuild in place (or replace those three files in another vault), and enable it again.

## Features

- TOML files in File Explorer with native rename/move and an auto-saving plain-text editor.
- **New TOML file** in the file/folder context menu; creates and opens a uniquely named empty file without overwriting existing files.
- Reading view and Live Preview; source mode remains unchanged.
- Exact, case-sensitive `html` and `component` markers in either order.
- Inline HTML, CSS and JavaScript, with isolated DOM and script scope.
- Auto height using the blog's bundled runtime, capped at 10000px.
- Vault-relative images, media, classic scripts and stylesheets embedded as data URLs.
- Offline component presets from an explicit Kotlin registry, including markup-only image comparison; no directory auto-loading.

````markdown
```html component
<button onclick="this.textContent = 'Clicked!'">Click me</button>
```
````

In Live Preview, move the cursor into the fence to edit it, then move outside to run it again. Reading view can cache info-string-only changes; use **koiro_blog: Refresh reading-view components** if needed. When source location is unavailable or ambiguous, reading view leaves the original code untouched.

## Build from source

Requires Node.js 24+, JDK 21 and the repository's Gradle wrapper. From this directory:

```sh
npm ci
npm run build
```

The build runs `exportComponentAssets` to export the Kotlin registry, then bundles only its registered CSS/JS from `assets/component/` plus the shared height runtime. Rebuild after changing presets or registrations. The installed plugin is self-contained; no server, generated blog, root `npm install`, or Java runtime is needed to use it.

## Development

```sh
npx playwright install chromium
npm test
```

Alternatively set `BROWSER_CHANNEL=chrome` or `msedge` to test with an installed browser. These tests cover source recognition and real Chromium/CodeMirror rendering. For TOML integration tests, set `OBSIDIAN_EXECUTABLE` to the desktop executable and run `npm run test:obsidian`; it launches an isolated temporary vault, not your existing vault.

See [behavior and limitations](../../../../doco/specs/obsidian-koiro-blog.md). The sandbox is not a network or resource-consumption firewall; CSS/JS dependency URLs and blog theme variables are not automatically resolved or inherited.
