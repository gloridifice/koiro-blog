"""Filesystem contracts for the build entry point; no compiler subprocess mocks."""

from pathlib import Path
import tempfile
import unittest

from build_site import check_local_assets, check_tree, prepare_output


class BuildSiteTest(unittest.TestCase):
    def test_clean_build_copies_static_inputs_including_hidden_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets, output = root / "assets", root / "dist"
            (assets / "assets/css").mkdir(parents=True)
            (assets / ".nojekyll").touch()
            (assets / "assets/css/reset.css").write_text("body{margin:0}")
            output.mkdir()
            (output / "deleted-article.html").write_text("obsolete")

            prepare_output(assets, output)

            self.assertFalse((output / "deleted-article.html").exists())
            self.assertTrue((output / ".nojekyll").is_file())
            self.assertEqual((output / "assets/css/reset.css").read_text(), "body{margin:0}")
            self.assertEqual((assets / "assets/css/reset.css").read_text(), "body{margin:0}")

    def test_lfs_pointer_is_rejected_before_the_previous_build_is_deleted(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets, output = root / "assets", root / "dist"
            assets.mkdir()
            output.mkdir()
            (assets / "image.png").write_text(
                "version https://git-lfs.github.com/spec/v1\noid sha256:" + "a" * 64 + "\nsize 4\n"
            )
            (output / "index.html").write_text("previous build")

            with self.assertRaisesRegex(ValueError, "Git LFS pointer"):
                prepare_output(assets, output)
            self.assertEqual((output / "index.html").read_text(), "previous build")

    def test_output_cannot_contain_or_replace_the_input_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            assets = Path(temporary) / "assets"
            assets.mkdir()
            (assets / "index.html").write_text("keep")
            for output in (assets, assets / "dist", assets.parent):
                with self.subTest(output=output), self.assertRaisesRegex(ValueError, "overlap"):
                    prepare_output(assets, output)
            self.assertEqual((assets / "index.html").read_text(), "keep")

    def test_local_asset_validation_resolves_encoded_urls_and_css_relative_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "assets/css").mkdir(parents=True)
            (root / "assets/image space.png").write_bytes(b"image")
            (root / "page.html").write_text(
                '<img src="/assets/image%20space.png?version=1">'
                '<script src="https://example.com/app.js"></script>'
                '<link href="/assets/css/root.css" rel="stylesheet">'
            )
            css = root / "assets/css/root.css"
            css.write_text('body{background:url("../image%20space.png")}')
            check_local_assets(root)

            css.write_text('body{background:url("../../../outside.png")}')
            with self.assertRaisesRegex(ValueError, "outside.png"):
                check_local_assets(root)
            css.write_text('body{background:url("../missing.png")}')
            with self.assertRaisesRegex(ValueError, "missing.png"):
                check_local_assets(root)

    def test_git_attributes_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / ".gitattributes").write_text("*.png filter=lfs")
            with self.assertRaisesRegex(ValueError, "repository metadata"):
                check_tree(root)


if __name__ == "__main__":
    unittest.main()
