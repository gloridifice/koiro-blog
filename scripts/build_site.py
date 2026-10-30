#!/usr/bin/env python3
"""Build or validate the site without a shell. Requires Python 3.11+."""

from __future__ import annotations

import argparse
import json
from html.parser import HTMLParser
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
LFS_HEADER = b"version https://git-lfs.github.com/spec/v1"
CSS_URL = re.compile(r"url\(\s*['\"]?([^'\")]+?)['\"]?\s*\)", re.IGNORECASE)


def run(*args: str | Path) -> None:
    command = [str(arg) for arg in args]
    executable = shutil.which(command[0])
    if executable is None:
        raise RuntimeError(f"Required executable not found: {command[0]}")
    command[0] = executable
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def check_tree(root: Path) -> None:
    """Reject files that cannot be published as a self-contained static site."""
    if not root.is_dir() or root.is_symlink():
        raise ValueError(f"Expected a real directory: {root}")
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ValueError(f"Cannot publish symlink: {path}")
        if path.name in {".git", ".gitattributes", ".github", ".idea"}:
            raise ValueError(f"Cannot publish repository metadata: {path}")
        if path.is_file():
            with path.open("rb") as stream:
                if stream.read(len(LFS_HEADER)) == LFS_HEADER:
                    raise ValueError(f"Git LFS pointer found: {path}. Run git lfs pull first.")


def prepare_output(assets: Path, output: Path) -> None:
    # Validate before deleting the previous build; never follow an output symlink.
    check_tree(assets)
    if output.is_symlink():
        raise ValueError(f"Refusing to replace output symlink: {output}")
    source, target = assets.resolve(), output.resolve()
    if source.is_relative_to(target) or target.is_relative_to(source):
        raise ValueError("Assets and output directories must not overlap")
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(assets, output)


class AssetReferences(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.urls: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag in {"img", "script", "source", "audio", "video", "iframe"}:
            url = attributes.get("src")
        elif tag in {"link", "a"}:
            url = attributes.get("href")
        else:
            url = None
        if url:
            self.urls.append(url)
        if tag == "meta" and attributes.get("http-equiv", "").lower() == "refresh":
            match = re.search(r"url\s*=\s*(.+)$", attributes.get("content", ""), re.I)
            if match: self.urls.append(match[1])
        if tag == "video" and attributes.get("poster"):
            self.urls.append(attributes["poster"])


def check_local_assets(root: Path) -> None:
    root = root.resolve()
    failures = []
    for document in root.rglob("*"):
        if document.suffix not in {".html", ".css"} or not document.is_file():
            continue
        text = document.read_text(encoding="utf-8")
        if document.suffix == ".html":
            parser = AssetReferences()
            parser.feed(text)
            urls = parser.urls
        else:
            urls = CSS_URL.findall(text)
        for url in urls:
            parsed = urlsplit(url)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            decoded = unquote(parsed.path)
            target = (root / decoded.lstrip("/") if decoded.startswith("/")
                      else document.parent / decoded).resolve()
            if not target.is_relative_to(root) or not target.is_file():
                failures.append(f"{document.relative_to(root)} -> {url}")
    if failures:
        raise ValueError("Missing or escaping local assets:\n" + "\n".join(failures[:20])
                         + (f"\n({len(failures)} failures total)" if len(failures) > 20 else ""))


def check_generated_files(root: Path) -> None:
    """Windows must produce the exact spelling later served on case-sensitive hosts."""
    manifest = root / ".generated-site-files.json"
    entries = json.loads(manifest.read_text(encoding="utf-8"))
    if not isinstance(entries, list) or not all(isinstance(entry, str) for entry in entries):
        raise ValueError("Invalid generated-file manifest")
    for relative in entries:
        parts = Path(relative).parts
        if not parts or Path(relative).is_absolute() or ".." in parts or chr(92) in relative:
            raise ValueError(f"Unsafe generated path: {relative}")
        current = root
        for part in parts:
            if not current.is_dir() or part not in {child.name for child in current.iterdir()}:
                raise ValueError(f"Generated output missing or case mismatch: {relative}")
            current = current / part
        if not current.is_file(): raise ValueError(f"Generated output is not a file: {relative}")


def validate_site(output: Path) -> None:
    check_tree(output)
    required = (
        "index.html", "home.html", "blogs.html", "portfolio.html", "about.html", "rss.xml",
        "CNAME", ".nojekyll", ".generated-site-files.json", "assets/css/root.css", "pkg/rs_wasm_script.js",
        "pkg/rs_wasm_script_bg.wasm", "assets/licenses/lucide.txt",
    )
    for relative in required:
        path = output / relative
        if not path.is_file() or (relative != ".nojekyll" and path.stat().st_size == 0):
            raise ValueError(f"Missing or empty build output: {path}")
    ET.parse(output / "rss.xml")
    configuration = tomllib.loads((ROOT / "i18n/locales.toml").read_text(encoding="utf-8"))
    for locale in configuration["locales"]:
        for name in ("home.html", "blogs.html", "portfolio.html", "about.html", "rss.xml"):
            path = output / locale / name
            if not path.is_file() or path.stat().st_size == 0:
                raise ValueError(f"Missing or empty localized output: {path}")
        feed = ET.parse(output / locale / "rss.xml")
        if feed.findtext("./channel/language") != locale:
            raise ValueError(f"Incorrect RSS language: {locale}")
        for link in feed.findall("./channel/item/link"):
            parsed = urlsplit(link.text or "")
            if not parsed.path.startswith(f"/{locale}/") or not (output / unquote(parsed.path).lstrip("/")).is_file():
                raise ValueError(f"Missing or wrong-language RSS link: {link.text}")
    with (output / "pkg/rs_wasm_script_bg.wasm").open("rb") as stream:
        if stream.read(8) != b"\x00asm\x01\x00\x00\x00":
            raise ValueError("Invalid WebAssembly output")
    check_generated_files(output)
    check_local_assets(output)
    print(f"Validated site: {output}")


def styles(*, watch: bool = False) -> None:
    sass = ROOT / "node_modules/sass/sass.js"
    if not sass.is_file():
        raise RuntimeError("Sass is not installed. Run npm ci or a full site build first.")
    options = ["--watch", "--style=expanded"] if watch else ["--style=compressed", "--no-source-map"]
    run("node", sass, "src/main/stylesheet:dist/assets/css", *options)


def wasm() -> None:
    run("wasm-pack", "build", ROOT / "src/main/rs-wasm-script", "--target", "web",
        "--release", "--out-dir", DIST / "pkg", "--locked")
    # wasm-pack's package ignores belong to an npm package, not the published site.
    (DIST / "pkg/.gitignore").unlink(missing_ok=True)


def build() -> None:
    check_tree(ROOT / "assets")
    check_tree(ROOT / "data")
    run("npm", "ci", "--no-audit", "--no-fund")
    prepare_output(ROOT / "assets", DIST)
    license_path = DIST / "assets/licenses/lucide.txt"
    license_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "node_modules/lucide-static/LICENSE", license_path)
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    run(wrapper, "--no-daemon", "test", "run")
    styles()
    wasm()
    validate_site(DIST)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", nargs="?", default="build",
                        choices=("build", "check", "styles", "watch-styles", "wasm"))
    args = parser.parse_args()
    try:
        if args.command == "build":
            build()
        elif args.command == "check":
            validate_site(DIST)
        elif args.command == "wasm":
            wasm()
        else:
            styles(watch=args.command == "watch-styles")
    except (OSError, ValueError, RuntimeError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
