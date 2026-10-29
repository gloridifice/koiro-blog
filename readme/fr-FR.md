# Koiro's Cat Café

[English](../README.md) | [简体中文](zh-CN.md) | [日本語](ja-JP.md) | Français

Un générateur de blog statique à partir de fichiers Markdown locaux et de métadonnées TOML, sans compte ni API Notion.

![Aperçu de la page principale](main_page.jpg)

## Compiler depuis les sources

Prérequis : Python 3.11+, JDK 21, Node.js 24, Git LFS et Rust installé avec rustup. Configurez `JAVA_HOME` pour utiliser le JDK 21. `rust-toolchain.toml` définit la version de Rust et la cible Wasm ; le build installe Sass selon le fichier de verrouillage npm.

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
python scripts/build_site.py
python -m http.server 8000 --directory dist
```

Ouvrez <http://localhost:8000>. Remplacez `python` par `python3` si votre système utilise ce nom. Consultez `python scripts/build_site.py --help` pour les commandes de build.

## Technologies

- Kotlin/JVM 21, kotlinx-html, JetBrains Markdown et ktoml
- Rust/WebAssembly pour les interactions dans le navigateur
- SCSS compilé avec Dart Sass
- Python pour le build multiplateforme et la validation des fichiers générés

## Structure

- `src/main/kotlin/` : analyse du contenu, génération HTML et RSS ; point d’entrée : `Main.kt`.
- `src/main/rs-wasm-script/` : interactions dans le navigateur.
- `src/main/stylesheet/` : sources des styles.
- `assets/` : fichiers statiques suivis par Git, copiés dans `dist/`. Le sous-dossier `assets/` conserve les URL `/assets/...` existantes.
- `data/` : Markdown et pièces jointes, répartis entre `blogs/`, `devlogs/`, `portfolio/` et `actives/`. Chaque fichier Markdown commence par un bloc de code TOML ; les champs sont définis dans `Models.kt`.
- `dist/` : site généré complet, ignoré par Git et recréé à chaque build complet.

## Git LFS

Les images binaires (`png`, `jpg`, `jpeg`, `gif`, `webp`, `avif`, `ico`) et les polices (`otf`, `ttf`, `woff`, `woff2`) utilisent Git LFS. Markdown, code et SVG restent des fichiers texte Git ordinaires.

Les nouvelles ressources deviennent des pointeurs LFS lors de leur ajout à l’index. Pour les fichiers binaires déjà suivis, exécutez `git add --renormalize -- <asset-paths>` avant le commit, puis vérifiez `git lfs status`. Cette opération ne réécrit pas l’historique existant.

La CI récupère les objets LFS réels et publie des fichiers ordinaires dans le dépôt Pages séparé. GitHub Pages ne sert pas le contenu référencé par les pointeurs LFS.

## Déploiement

GitHub Actions lance le build lors des push sur `master`, des PR et des exécutions manuelles. La publication dans un dépôt Pages séparé reste désactivée tant que les variables du dépôt et le secret de déploiement ne sont pas configurés. Voir la [configuration du déploiement](../docs/deployment.md), qui précise aussi les précautions après la réécriture locale de l’historique.

## Développement

- [Build, validation et déploiement](../docs/deployment.md)
- [Préparation des images avant leur ajout à l’index](../docs/image-preparation.md)
