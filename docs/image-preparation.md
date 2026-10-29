# Prepare content images

`scripts/prepare_images.py` scans JPEG, PNG and GIF files under `data/`. It removes descriptive metadata (GPS, camera details, capture dates, EXIF comments, XMP/IPTC and PNG text), retaining orientation and ICC color profiles. Existing JPEG image data is not recompressed. GIFs, transparent PNGs and animated PNGs retain their formats; only fully opaque, still PNGs become quality-80 JPGs. Markdown image/link destinations, reference links and TOML `preview` paths are updated together.

Install ExifTool and the Python dependencies, then preview or apply the changes (commands below are for macOS):

```sh
brew install exiftool
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements.txt
.venv/bin/python scripts/prepare_images.py data         # dry run
.venv/bin/python scripts/prepare_images.py data --apply # validate, then write
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```

Run this **before staging assets for LFS**. The script prepares and validates a temporary copy first, refuses JPEG filename collisions and does not leave metadata-bearing backups in `data/`. Keep originals outside the repository. It does not detect private information visible in pixels or present in filenames. Unsupported image formats are not processed.

This is a content-maintenance step, not part of the deployment build.
