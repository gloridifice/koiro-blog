# koiro_blog

[English](../README.md) | [简体中文](zh-CN.md) | 日本語 | [Français](fr-FR.md)

Obsidian でブログの HTML コンポーネントを表示し、TOML ファイルの作成・編集・名前変更・移動を行えます。

## クイックスタート

### インストール

`data/component/plugins/koiro_blog/` でビルドし、`main.js`、`manifest.json`、`styles.css` を `<vault>/.obsidian/plugins/koiro_blog/` にコピーします。この 3 つのファイル名は Obsidian の指定です。旧 Component Renderer を無効化・削除してから Obsidian を再読み込みし、**koiro_blog** を有効にしてください。コードを信頼できる Vault でのみ使用してください。

### 更新

プラグインを無効にし、上記の 3 ファイルを新しいビルドで置き換えてから再度有効にします。

## 機能

- ファイルエクスプローラーに TOML を表示。標準の名前変更・移動と、自動保存付きプレーンテキスト編集に対応します。
- ファイルやフォルダーの右クリックメニューに **New TOML file** を追加。既存ファイルを上書きせず、連番の空ファイルを作成して開きます。
- リーディングビューと Live Preview に対応。ソースモードは変更しません。
- 大文字・小文字を区別した `html` と `component` を、順序に関係なく認識します。
- インライン HTML、CSS、JavaScript を独立した DOM とスコープで実行します。
- ブログの高さ調整スクリプトを同梱。高さの上限は 10000px です。
- Vault 内の相対画像、メディア、通常のスクリプト、スタイルシートを data URL として埋め込みます。
- Kotlin に明示登録したプリセットをオフラインで提供します。画像比較は HTML のみで利用でき、ディレクトリの自動読み込みは行いません。

````markdown
```html component
<button onclick="this.textContent = 'Clicked!'">Click me</button>
```
````

Live Preview ではカーソルをブロック内に移すと編集でき、外に移すと再実行します。リーディングビューでマーカーだけの変更が反映されない場合は **koiro_blog: Refresh reading-view components** を実行してください。ソース位置を特定できない場合は元のコードを維持します。

## ソースからビルド

Node.js 24+、JDK 21、リポジトリの Gradle wrapper が必要です。このディレクトリで実行します。

```sh
npm ci
npm run build
```

ビルドは `exportComponentAssets` で Kotlin の登録一覧を出力し、登録済みの `assets/component/` CSS/JS と共有の高さ調整スクリプトだけを同梱します。プリセットや登録を変更したら再ビルドしてください。インストール後はリポジトリ、サーバー、ブログの生成物、ルートの npm 依存関係、Java 実行環境は不要です。

## 開発

```sh
npx playwright install chromium
npm test
```

`BROWSER_CHANNEL=chrome` または `msedge` で既存のブラウザーも使えます。これらは構文判定と実際の Chromium/CodeMirror の描画を検証します。TOML の統合テストは `OBSIDIAN_EXECUTABLE` にデスクトップ版の実行ファイルを指定し、`npm run test:obsidian` で実行します。既存の Vault ではなく、隔離した一時 Vault を使用します。

[仕様と制限](../../../../../doco/specs/obsidian-koiro-blog.md)を参照してください。サンドボックスは通信やリソース消費を制限しません。CSS/JS の依存 URL の自動解決とブログテーマの継承には対応しません。
