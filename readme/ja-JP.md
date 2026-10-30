# Koiro's Cat Café

[English](../README.md) | [简体中文](zh-CN.md) | 日本語 | [Français](fr-FR.md)

ローカルの Markdown と TOML メタデータから生成する静的ブログです。Notion のアカウントや API は不要です。

![トップページ](main_page.jpg)

## ソースからビルド

JDK 21、Node.js 24、Git LFS、rustup 経由でインストールした Rust が必要です。`JAVA_HOME` は JDK 21 に設定してください。`rust-toolchain.toml` が Rust のバージョンと Wasm ターゲットを指定し、`npm ci` がロック済みのビルド依存関係をインストールします。

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
npm ci
npm run dev
```

ターミナルに表示された URL を開いてください。既定のポートは 8000 で、使用中なら次の空きポートを選びます。`npm run dev` は成果物がなければビルドし、`dist/` の配信と SCSS の監視を開始します。変更後はブラウザーを再読み込みしてください。本番用のクリーンビルドは `npm run build` で行います。

## 技術構成

- Kotlin/JVM 21、kotlinx-html、JetBrains Markdown、ktoml
- ブラウザー操作用の Rust/WebAssembly
- Dart Sass でコンパイルする SCSS
- クロスプラットフォームのビルド、成果物検証、ローカル開発を行う Node.js/npm

## ディレクトリ構成

- `src/main/kotlin/`：コンテンツ解析、HTML と RSS の生成。エントリーポイントは `Main.kt`。
- `src/main/rs-wasm-script/`：ブラウザー操作。
- `src/main/stylesheet/`：スタイルのソース。
- `assets/`：Git で管理する静的入力。`dist/` にコピーします。内側の `assets/` は既存の `/assets/...` URL を維持します。
- `data/`：`blogs/`、`devlogs/`、`portfolio/`、`actives/` に分類したコンテンツ。英語の `snake_case` フォルダーに `meta.toml`、`zh-CN.md`、任意の翻訳と共有添付ファイルを置きます。[形式の仕様](../doco/specs/content-localization.md)を参照してください。
- `i18n/`：言語設定と TOML の画面辞書。基準は中国語、既定のアクセス先は英語です。英語の画面と AI 翻訳のコンテンツを提供しています。英訳がない場合は引き続き中国語にフォールバックします。
- `dist/`：生成済みサイト全体。Git の管理対象外で、フルビルドごとに作り直します。

## Git LFS

画像（`png`、`jpg`、`jpeg`、`gif`、`webp`、`avif`、`ico`）とフォント（`otf`、`ttf`、`woff`、`woff2`）は Git LFS を使います。Markdown、コード、SVG は通常の Git テキストとして保存します。

新しいアセットはステージング時に LFS ポインターになります。追跡済みのバイナリには、コミット前に `git add --renormalize -- <asset-paths>` を実行し、`git lfs status` を確認してください。既存の履歴は書き換えません。

CI は LFS の実体を取得し、別の Pages リポジトリに通常のファイルとして公開します。GitHub Pages は LFS ポインターの参照先を配信しません。

## デプロイ

GitHub Actions は `master` への push、PR、手動実行でビルドします。リポジトリ変数とデプロイ用シークレットを設定するまで、別の Pages リポジトリへの公開は無効です。[デプロイ設定](../docs/deployment.md)には、ローカルで履歴を書き換えた後の注意事項も記載しています。

## 開発

- [ビルド、検証、デプロイ](../docs/deployment.md)
- [ステージング前の画像処理](../docs/image-preparation.md)
