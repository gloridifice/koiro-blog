import io
from pathlib import Path
import shutil
import tempfile
import unittest
from urllib.parse import quote

from PIL import Image, ImageCms, PngImagePlugin

from prepare_images import JPEG_QUALITY, exiftool, metadata, opaque_png, prepare, private_tags, update_references


@unittest.skipUnless(shutil.which("exiftool"), "ExifTool is required")
class PrepareImagesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def snapshot(self):
        return {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}

    def test_conversion_updates_only_local_references_and_is_idempotent(self):
        folder = self.root / "中文 + images"
        folder.mkdir()
        image = folder / "sample image.png"
        Image.new("RGBA", (16, 10), (100, 150, 200, 255)).save(image)
        relative = image.relative_to(self.root).as_posix()
        encoded = quote(relative, safe="/+")
        document = self.root / "article.md"
        document.write_text(
            f'```toml\npreview="{relative}"\n```\n'
            f'![sample image.png]({encoded})\n'
            f'![image](./{encoded}?download=1#image "caption")\n'
            f'[reference]: <{encoded}> "caption"\n'
            f'![remote](https://example.com/{encoded})\n'
            '```\nexample.png\n```\n', encoding="utf-8"
        )
        original = self.snapshot()
        report = prepare(self.root)
        self.assertEqual(self.snapshot(), original, "dry runs must not modify source files")
        self.assertEqual(report["converted_png"], 1)

        prepare(self.root, apply=True)
        self.assertFalse(image.exists())
        jpeg = image.with_suffix(".jpg")
        with Image.open(jpeg) as result:
            self.assertEqual(result.size, (16, 10))
            expected = io.BytesIO()
            Image.new("RGB", (16, 10)).save(expected, "JPEG", quality=JPEG_QUALITY)
            expected.seek(0)
            with Image.open(expected) as quality_reference:
                self.assertEqual(result.quantization, quality_reference.quantization)
        text = document.read_text(encoding="utf-8")
        self.assertIn(f'preview="{relative[:-4]}.jpg"', text)
        self.assertIn(f'![sample image.png]({encoded[:-4]}.jpg)', text)
        self.assertIn(f'./{encoded[:-4]}.jpg?download=1#image "caption"', text)
        self.assertIn(f'[reference]: <{encoded[:-4]}.jpg>', text)
        self.assertIn(f'https://example.com/{encoded}', text)
        self.assertIn('example.png', text)
        first_run = self.snapshot()
        prepare(self.root, apply=True)
        self.assertEqual(self.snapshot(), first_run)

    def test_transparency_gif_and_animated_png_are_preserved(self):
        rgba = self.root / "alpha.png"
        Image.new("RGBA", (4, 4), (20, 30, 40, 100)).save(rgba)
        palette = Image.new("P", (4, 4), 0)
        palette.putpalette([0, 0, 0, 255, 255, 255] + [0] * 762)
        palette.save(self.root / "palette.png", transparency=0)
        Image.new("RGB", (4, 4), (0, 0, 0)).save(self.root / "trns.png", transparency=(0, 0, 0))
        frames = [Image.new("RGB", (4, 4), color) for color in ("red", "blue")]
        frames[0].save(self.root / "animation.gif", save_all=True, append_images=frames[1:], duration=120, loop=0)
        frames[0].save(self.root / "animation.png", save_all=True, append_images=frames[1:], duration=120, loop=0)
        before = self.snapshot()
        report = prepare(self.root, apply=True)
        self.assertEqual(report["converted_png"], 0)
        self.assertEqual(self.snapshot(), before)

    def test_unused_transparent_palette_entry_does_not_prevent_conversion(self):
        image = Image.new("P", (4, 4), 1)
        image.putpalette([0, 0, 0, 255, 255, 255] + [0] * 762)
        path = self.root / "opaque.png"
        image.save(path, transparency=0)
        self.assertTrue(opaque_png(path))

    def test_jpeg_privacy_removed_without_recompression_or_color_changes(self):
        path = self.root / "photo.jpg"
        profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
        Image.new("RGB", (8, 12), "green").save(path, icc_profile=profile)
        exiftool("-overwrite_original", "-GPSLatitude=12", "-GPSLatitudeRef=N",
                 "-GPSLongitude=34", "-GPSLongitudeRef=E", "-Make=Test Camera",
                 "-DateTimeOriginal=2020:01:02 03:04:05", "-UserComment=Private note",
                 "-XMP-dc:Creator=Private author", "-IFD0:Orientation#=6", path)
        before = metadata([path])[path]
        self.assertTrue(private_tags(before))
        report = prepare(self.root, apply=True)
        after = metadata([path])[path]
        self.assertEqual(report["images_with_gps"], 1)
        self.assertFalse(private_tags(after))
        self.assertEqual(before["File:ImageDataHash"], after["File:ImageDataHash"])
        self.assertEqual(after["EXIF:IFD0:Orientation"], 6)
        with Image.open(path) as result:
            self.assertEqual(result.info["icc_profile"], profile)

    def test_png_orientation_is_applied_before_jpeg_conversion(self):
        path = self.root / "rotated.png"
        Image.new("RGB", (8, 12), "blue").save(path)
        exiftool("-overwrite_original", "-IFD0:Orientation#=6", path)
        prepare(self.root, apply=True)
        with Image.open(path.with_suffix(".jpg")) as result:
            self.assertEqual(result.size, (12, 8))
            self.assertNotIn(274, result.getexif())

    def test_transparent_png_text_is_removed_without_changing_pixels(self):
        path = self.root / "private.png"
        text = PngImagePlugin.PngInfo()
        text.add_text("Comment", "Private note")
        text.add_text("ArbitraryPrivateField", "Private value")
        source = Image.new("RGBA", (4, 4), (50, 60, 70, 90))
        source.save(path, pnginfo=text)
        prepare(self.root, apply=True)
        with Image.open(path) as result:
            self.assertEqual(result.tobytes(), source.tobytes())
            self.assertEqual(result.text, {})

    def test_collision_aborts_without_touching_content(self):
        Image.new("RGB", (4, 4)).save(self.root / "image.png")
        Image.new("RGB", (4, 4)).save(self.root / "image.jpg")
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "already exists"):
            prepare(self.root, apply=True)
        self.assertEqual(self.snapshot(), before)

    def test_same_named_file_in_another_directory_is_not_referenced(self):
        converted = {self.root / "one" / "image.png"}
        text = '![image.png](image.png)\npreview="image.png"\n'
        self.assertEqual(update_references(text, self.root / "two" / "post.md", converted), text)


if __name__ == "__main__":
    unittest.main()
