# Koiro's Cat Café

[English](../README.md) | [简体中文](zh-CN.md) | 日本語 | [Français](fr-FR.md)

ローカルの Markdown と TOML メタデータから生成する静的ブログです。Notion のアカウントや API は不要です。

![トップページ](main_page.jpg)

## ソースからビルド

Python 3.11+、JDK 21、Node.js 24、Git LFS、rustup 経由でインストールした Rust が必要です。`JAVA_HOME` は JDK 21 に設定してください。`rust-toolchain.toml` が Rust のバージョンと Wasm ターゲットを指定し、ビルド時に npm のロックファイルに従って Sass をインストールします。

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
python scripts/build_site.py
python -m http.server 8000 --directory dist
```

<http://localhost:8000> を開いてください。環境によっては `python` の代わりに `python3` を使います。ビルドコマンドは `python scripts/build_site.py --help` で確認できます。

## 技術構成

- Kotlin/JVM 21、kotlinx-html、JetBrains Markdown、ktoml
- ブラウザー操作用の Rust/WebAssembly
- Dart Sass でコンパイルする SCSS
- クロスプラットフォームのビルドと成果物検証を行う Python

## ディレクトリ構成

- `src/main/kotlin/`：コンテンツ解析、HTML と RSS の生成。エントリーポイントは `Main.kt`。
- `src/main/rs-wasm-script/`：ブラウザー操作。
- `src/main/stylesheet/`：スタイルのソース。
- `assets/`：Git で管理する静的入力。`dist/` にコピーします。内側の `assets/` は既存の `/assets/...` URL を維持します。
- `data/`：Markdown と添付ファイル。`blogs/`、`devlogs/`、`portfolio/`、`actives/` に分類します。各 Markdown の先頭には TOML コードブロックを置きます。フィールドは `Models.kt` を参照してください。
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
