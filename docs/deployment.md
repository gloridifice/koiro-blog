# Build and deployment

## Directory contract

- `assets/` contains files copied unchanged into the site root. Its nested `assets/` directory preserves existing `/assets/...` URLs.
- `data/` contains Markdown and source attachments. The generator copies referenced images to `dist/images/`.
- Kotlin generates HTML and RSS, Sass generates `dist/assets/css/`, and wasm-pack generates `dist/pkg/`.
- `dist/` is disposable and ignored by Git. Do not put source files or a separate Git checkout inside it.

`assets/index.html`, `assets/CNAME`, `assets/.nojekyll`, `assets/favicon.ico`, the album and third-party libraries are static inputs. Handwritten `reset.css` is also an input. Generated CSS, source maps, HTML, RSS and Wasm do not belong in `assets/`.

## Local build

Install Python 3.11+, JDK 21, Node.js 24, Git LFS and Rust through rustup. Set `JAVA_HOME` to JDK 21. The checked-in Rust toolchain file selects Rust 1.96.0 and the Wasm target.

```sh
git lfs install --local
git lfs pull
cargo install wasm-pack --version 0.15.0 --locked
python scripts/build_site.py
python -m http.server 8000 --directory dist
```

The Python script works on Windows, macOS and Linux, and resolves paths relative to its own location rather than the caller's working directory. It selects `gradlew.bat` on Windows and `gradlew` elsewhere. Java, Node/npm and wasm-pack must be on PATH. The build installs locked Sass and `lucide-static` dependencies with `npm ci`; no global npm packages or frontend bundler are needed.

A full build validates input files, recreates `dist/`, copies static inputs including hidden files, runs Kotlin tests and generation, compiles styles and Wasm, then validates the resulting site. It rejects LFS pointers, repository metadata, missing local image/script/stylesheet references, invalid RSS and invalid Wasm output. External URLs are not fetched. The build does not run the image-preparation script or modify content.

Use `python scripts/build_site.py --help` for incremental styles/Wasm commands and standalone validation. For stylesheet development after the first build:

```sh
python scripts/build_site.py watch-styles
```

## Blog icons

Set `icon` in a blog's fenced TOML header to a Lucide kebab-case name:

````markdown
```toml
icon = "book-open"
```
````

Browse names at <https://lucide.dev/icons/>. Both large and regular blog cards use this field. An explicit `icon` takes precedence over the legacy `emoji` field; without `icon`, a nonblank `emoji` is preserved. If neither is supplied, the card uses `file-text`. Unknown or malformed names fail generation with the source filename, including for unpublished drafts. Paths and raw SVG are not accepted.

Kotlin reads SVGs from `node_modules/lucide-static/icons/` at build time and inlines only the icons used in cards. No Lucide JavaScript, icon font or CDN request is needed in the browser. The full build copies the package's ISC/MIT notices to `dist/assets/licenses/lucide.txt`. Run `npm ci` from the project root before invoking Gradle directly, including Kotlin tests.

## GitHub Actions

`.github/workflows/site.yml` builds on pushes to `master`, pull requests and manual dispatches. It uploads the site as an artifact, including `.nojekyll`. PRs never publish. The separate publish job validates the downloaded artifact before using a deployment token.

Publishing is **disabled by default**. Before enabling it:

1. Confirm the target repository name and a dedicated publication branch. The default branch name in the workflow is `gh-pages`.
2. Back up any existing contents on that branch. Publication replaces its contents with `dist/`, including deletion of files no longer generated. Do not use a branch containing independent source files, workflows or other sites.
3. Create a fine-grained PAT limited to the target repository, with **Contents: Read and write**, and save it as the source repository's Actions secret `PAGES_DEPLOY_TOKEN`. Set an expiration and rotate it before expiry. The default `GITHUB_TOKEN` cannot push to another repository.
4. Set these source repository Actions variables:

   | Variable | Value |
   | --- | --- |
   | `PAGES_REPOSITORY` | Actual `owner/repository` of the separate Pages repository |
   | `PAGES_BRANCH` | Publication branch; optional, defaults to `gh-pages` |
   | `PAGES_PUBLISH_ENABLED` | `true`, only after the checks above |

5. Optionally configure required reviewers on the source repository's `pages-publish` environment. Ensure the target branch's rules permit the deployment identity to publish.
6. In the target repository, set **Settings → Pages → Deploy from a branch**, selecting the publication branch and `/(root)`. If that branch does not exist yet, select it after the first successful publication.
7. Keep the custom domain configured as `blog.koiro.xyz`, matching `assets/CNAME`, and verify the target repository's Pages deployment after the first push.

The source checkout downloads LFS objects. The target repository receives ordinary asset bytes, not LFS pointers or the source `.gitattributes`. Do not enable LFS for published files in the target repository. Publication keeps commit history for rollback; it does not use orphan commits.

The workflow does not run merely because it exists locally. No target repository settings, secrets or remote branches are changed by the local build script.

## After the local history rewrite

The old `static/` path has been removed from every retained local branch's history with `git-filter-repo`. A full directory backup, including `.git` and LFS objects, was made and verified before rewriting. The local `.git/filter-repo/commit-map` records old and new commit IDs.

`git-filter-repo` removes `origin` as a safety measure and converts remote-tracking branches into local branches. Do not merge or pull the old remote history back into this checkout. Remote history and other clones remain unchanged until a separately authorized, coordinated history replacement. Keep the backup until that process is complete.

The migrated `assets/` still needs Git LFS storage for its images and fonts. Removing `static/` from Git history is not a reason to delete all `.git/lfs` objects. Do not prune while migrated files remain uncommitted.

## Checks

```sh
python -m unittest discover -s scripts -p test_build_site.py
python scripts/build_site.py
python scripts/build_site.py check
git diff --check
```

To verify cleanup, place a disposable file in `dist/`, run the full build again, and check that the file is gone. The build must not recreate `static/` or change any source files.
