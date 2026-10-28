#!/usr/bin/env python3
"""Sanitize image metadata and convert opaque PNGs to quality-80 JPEGs.

Requires Pillow and ExifTool. Defaults to a dry run; --apply writes changes.
Works on a temporary copy first, so conversion/validation failures leave the
source untouched. Does not inspect identifying content in pixels or filenames.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import unquote, urlsplit

from PIL import Image, ImageOps

IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"}
JPEG_QUALITY = 80
SAFE_EXIF = {
    "Orientation", "ColorSpace", "Gamma", "InteropIndex", "InteropVersion",
    "ExifVersion", "FlashpixVersion", "ComponentsConfiguration",
    "XResolution", "YResolution", "ResolutionUnit", "YCbCrPositioning",
    "ExifImageWidth", "ExifImageHeight",
}
PRIVATE_GROUPS = {"EXIF", "MakerNotes", "XMP", "IPTC", "Photoshop", "JUMBF"}
TEXT_TAGS = {
    "Comment", "UserComment", "Author", "Artist", "Creator", "Copyright",
    "Description", "ImageDescription", "Title", "CreationTime", "Software",
    "Disclaimer", "Warning", "Source",
}
# Match destinations, not alt text. Existing URL encoding, titles and query
# strings are retained. The generator uses document-relative image paths.
INLINE_PNG = re.compile(r'(?<=\]\()\s*<?(?P<url>[^\n)]*?\.png)(?=[?#)>\s])', re.I)
PREVIEW_PNG = re.compile(r'''(?m)^\s*preview\s*=\s*["'](?P<url>[^"'\n]+\.png)(?=["'])''', re.I)
REFERENCE_PNG = re.compile(r'(?m)^\s*\[[^\]\n]+\]:\s*<?(?P<url>[^\n]*?\.png)(?=[?#>\s]|$)', re.I)


def images(root):
    return sorted(p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)


def exiftool(*args):
    # Unrecognized MakerNotes are discarded with EXIF, not copied back.
    result = subprocess.run([
        "exiftool", "-q", "-api", r"NoWarning=^(No writable tags|\[minor\] Unrecognized MakerNote(?:s|Unknown))",
        *map(str, args),
    ], capture_output=True, text=True)
    if result.returncode or result.stderr.strip():
        raise RuntimeError(f"ExifTool failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def metadata(paths):
    if not paths:
        return {}
    records = json.loads(exiftool("-json", "-G0:1", "-s", "-n", "-u", "-all", "-ImageDataHash", *paths))
    for record in records:
        if any(key in record for key in ("ExifTool:Error", "ExifTool:Warning")):
            raise RuntimeError(f"Invalid image metadata: {record['SourceFile']}")
    return {Path(record["SourceFile"]): record for record in records}


def private_tags(record):
    found = []
    for key in record:
        group, _, name = key.partition(":")
        name = name.rsplit(":", 1)[-1]
        if group == "EXIF" and name in SAFE_EXIF:
            continue
        if group in PRIVATE_GROUPS or (group in {"PNG", "GIF", "File", "JPEG"} and name in TEXT_TAGS):
            found.append(key)
    return found


def scrub_metadata(paths):
    # PNG/GIF: remove text, EXIF and XMP, keeping color profiles and animation.
    # JPEG: remove descriptive metadata without deleting MPF/HDR image data
    # or recompressing the original JPEG stream.
    for extensions, deletion in [
        ({".png"}, ["-all=", "--ICC_Profile:all", "-Trailer:all="]),
        ({".gif"}, ["-all=", "--ICC_Profile:all"]),
        ({".jpg", ".jpeg"}, [
            "-EXIF:all=", "-MakerNotes:all=", "-XMP:all=", "-IPTC:all=", "-Photoshop:all=",
            "-JUMBF:all=", "-Comment=",
        ]),
    ]:
        selected = [p for p in paths if p.suffix.lower() in extensions]
        if not selected:
            continue
        render_tags = ("IFD0:Orientation", "ExifIFD:ColorSpace", "ExifIFD:Gamma", "InteropIFD:InteropIndex")
        if ".jpg" in extensions:
            # Explicit values avoid asking ExifTool to rebuild unrecognized
            # camera MakerNotes while copying tags from the original JPEG.
            for path, record in metadata(selected).items():
                keep = [f"-{tag}#={record['EXIF:' + tag]}" for tag in render_tags if "EXIF:" + tag in record]
                exiftool("-overwrite_original", *deletion, *keep, path)
        else:
            keep = [] if extensions == {".gif"} else ["-tagsFromFile", "@", *("-" + tag for tag in render_tags)]
            exiftool("-overwrite_original", *deletion, *keep, *selected)


def verify_embedded_metadata(paths):
    if not paths:
        return
    records = json.loads(exiftool(
        "-json", "-ee3", "-u", "-G3:1", "-GPS:all", "-XMP:all", "-IPTC:all",
        "-MakerNotes:all", "-UserComment", "-Comment", "-DateTimeOriginal", "-CreateDate",
        "-Make", "-Model", "-HostComputer", "-SerialNumber", "-OwnerName",
        "-Artist", "-Author", "-ImageDescription", *paths,
    ))
    # iPhone JPEGs may contain an auxiliary HDR image. Its rendering metadata
    # is not personal information; other embedded descriptive tags must go.
    safe_hdr = (
        ":XMP-x:XMPToolkit", ":XMP-HDRGainMap:HDRGainMapVersion",
        ":XMP-HDRGainMap:HDRGainMapHeadroom", ":XMP-apdi:AuxiliaryImageType",
    )
    for record in records:
        unexpected = [key for key in record if key != "SourceFile" and not key.endswith(safe_hdr)]
        if unexpected:
            raise RuntimeError(f"Private embedded metadata remains in {record['SourceFile']}: {unexpected}")


def opaque_png(path):
    with Image.open(path) as image:
        if image.format != "PNG" or getattr(image, "is_animated", False):
            return False
        # Inspect actual alpha values, including palette/tRNS transparency.
        return image.convert("RGBA").getchannel("A").getextrema()[0] == 255


def convert_png(source, destination):
    with Image.open(source) as image:
        oriented = ImageOps.exif_transpose(image)
        rgb = oriented.convert("RGB")
        profile = image.info.get("icc_profile")
        # Do not propagate EXIF, XMP, comments or other source metadata.
        rgb.info.clear()
        rgb.save(destination, "JPEG", quality=JPEG_QUALITY, optimize=True, icc_profile=profile)
        expected_size = rgb.size
    with Image.open(destination) as result:
        result.load()
        if result.format != "JPEG" or result.size != expected_size:
            raise RuntimeError(f"Invalid JPEG conversion: {source}")


def update_references(text, document, converted):
    def replace(match):
        url = match.group("url")
        parsed = urlsplit(url)
        if parsed.scheme or parsed.netloc or parsed.path.startswith("/"):
            return match.group(0)
        target = (document.parent / unquote(parsed.path)).resolve()
        if target not in converted:
            return match.group(0)
        start = match.start("url") - match.start()
        end = match.end("url") - match.start()
        return match.group(0)[:start] + url[:-4] + ".jpg" + match.group(0)[end:]

    for pattern in (INLINE_PNG, PREVIEW_PNG, REFERENCE_PNG):
        text = pattern.sub(replace, text)
    return text


def digest(path):
    return hashlib.sha256(path.read_bytes()).digest()


def atomic_write(path, content):
    descriptor, name = tempfile.mkstemp(dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as temp:
            temp.write(content)
        os.chmod(temporary, path.stat().st_mode & 0o777 if path.exists() else 0o644)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def prepare(root, apply=False):
    root = Path(root).resolve()
    if not root.is_dir():
        raise ValueError(f"Content directory not found: {root}")
    if not shutil.which("exiftool"):
        raise RuntimeError("Install ExifTool first (macOS: brew install exiftool)")
    if any(p.is_symlink() for p in root.rglob("*")):
        raise ValueError("Content directory must not contain symlinks")

    original_images = images(root)
    before = metadata(original_images)
    candidates = [p for p in original_images if p.suffix.lower() == ".png" and opaque_png(p)]
    destinations = [p.with_suffix(".jpg") for p in candidates]
    if len(set(destinations)) != len(destinations) or any(p.exists() for p in destinations):
        raise ValueError("JPEG destination already exists; no files changed")

    with tempfile.TemporaryDirectory(prefix="kt-blog-images-") as temporary:
        work = (Path(temporary) / "data").resolve()
        shutil.copytree(root, work)
        scrub_metadata(images(work))
        converted = {work / p.relative_to(root) for p in candidates}
        for source in sorted(converted):
            convert_png(source, source.with_suffix(".jpg"))

        markdown_changed = 0
        for document in sorted(work.rglob("*.md")):
            original = document.read_bytes().decode("utf-8")
            updated = update_references(original, document, converted)
            if updated != original:
                document.write_bytes(updated.encode("utf-8"))
                markdown_changed += 1
        for source in converted:
            source.unlink()

        after = metadata(images(work))
        verify_embedded_metadata(list(after))
        for path, record in after.items():
            if private_tags(record):
                raise RuntimeError(f"Descriptive metadata remains in {path.relative_to(work)}")
        for source, old in before.items():
            if source in candidates:
                continue
            new = after[work / source.relative_to(root)]
            for key in ("File:ImageDataHash", "EXIF:IFD0:Orientation", "GIF:FrameCount", "GIF:Duration", "GIF:AnimationIterations"):
                if old.get(key) != new.get(key):
                    raise RuntimeError(f"Image content or orientation changed: {source}")
            # ICC profiles are retained as-is, not rewritten with personal data.
            old_icc = {k: v for k, v in old.items() if k.startswith("ICC_Profile:")}
            new_icc = {k: v for k, v in new.items() if k.startswith("ICC_Profile:")}
            if old_icc != new_icc:
                raise RuntimeError(f"Color profile changed: {source}")

        report = {
            "images": len(original_images),
            "images_with_descriptive_metadata": sum(bool(private_tags(r)) for r in before.values()),
            "images_with_gps": sum(any(":GPS" in k for k in r) for r in before.values()),
            "converted_png": len(converted),
            "retained_png": sum(p.suffix.lower() == ".png" for p in after),
            "retained_gif": sum(p.suffix.lower() == ".gif" for p in after),
            "updated_markdown": markdown_changed,
            "image_bytes_before": sum(p.stat().st_size for p in original_images),
            "image_bytes_after": sum(p.stat().st_size for p in after),
        }
        if apply:
            # Publish images first, references second, delete old PNGs last.
            # Do not leave unsanitized *_original backups in the content tree.
            for path in sorted(work.rglob("*"), key=lambda p: p.suffix.lower() == ".md"):
                if path.is_file():
                    target = root / path.relative_to(work)
                    if not target.exists() or digest(path) != digest(target):
                        atomic_write(target, path.read_bytes())
            for source in candidates:
                source.unlink()
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", default="data")
    parser.add_argument("--apply", action="store_true", help="write validated changes (default: dry run)")
    args = parser.parse_args()
    report = prepare(args.directory, args.apply)
    print("Applied" if args.apply else "Dry run: no source files changed")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
