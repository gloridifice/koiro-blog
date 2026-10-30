"""Behavioral migration tests at the on-disk/dry-run/apply boundary."""
import json
from pathlib import Path
import shutil
import tempfile
import tomllib
import unittest

from migrate_content import migrate


class MigrateContentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.data = self.base / 'data'
        (self.data / 'blogs').mkdir(parents=True)
        self.plan = self.base / 'plan.json'
        self.plan.write_text(json.dumps({'blogs/中文.md': 'blogs/english_title'}), encoding='utf-8')

    def snapshot(self):
        return {p.relative_to(self.data).as_posix(): p.read_bytes() for p in self.data.rglob('*') if p.is_file()}

    def article(self, body, header=''):
        path = self.data / 'blogs/中文.md'
        path.write_text('```toml\ntitle="A/B: Title"\npublished=true\nclass="Dev"\ndate="2025-01-01"\n' + header + '```\n' + body, encoding='utf-8')
        return path

    def test_dry_run_apply_and_repeat_preserve_media_code_metadata_and_old_addresses(self):
        image = self.data / 'blogs/图 片.png'
        image.write_bytes(b'unchanged-source-media')
        (self.data / 'blogs/unused.txt').write_text('retain me')
        body = '![image](%E5%9B%BE%20%E7%89%87.png?cache=1#photo)\n![caption [source](https://example.com)](%E5%9B%BE%20%E7%89%87.png)\n```md\n![example](missing.png)\n```\n'
        self.article(body, 'date="2025-02-02"\ncustom="preserve"\n')
        original = self.snapshot()
        report = migrate(self.data, plan_path=self.plan)
        self.assertFalse(report['changed'])
        self.assertEqual(original, self.snapshot())
        report = migrate(self.data, True, self.plan)
        backup = Path(report['backup'])
        self.addCleanup(shutil.rmtree, backup.parent)
        self.assertEqual(original, {p.relative_to(backup).as_posix():p.read_bytes() for p in backup.rglob('*') if p.is_file()})
        meta = tomllib.loads((self.data / 'blogs/english_title/meta.toml').read_text(encoding='utf-8'))
        self.assertEqual(meta['date'], '2025-02-02')
        self.assertEqual(meta['legacy_dates'], ['2025-01-01'])
        self.assertEqual(meta['custom'], 'preserve')
        md = (self.data / 'blogs/english_title/zh-CN.md').read_text(encoding='utf-8')
        self.assertIn('images/%E5%9B%BE%20%E7%89%87.png?cache=1#photo', md)
        self.assertIn('```md\n![example](missing.png)\n```', md)
        self.assertIn('[source](https://example.com)](images/%E5%9B%BE%20%E7%89%87.png)', md)
        self.assertIn('ai-translation = false', md)
        self.assertFalse(list(self.data.rglob('en-US.md')))
        self.assertEqual((self.data / 'blogs/english_title/images/图 片.png').read_bytes(), b'unchanged-source-media')
        self.assertEqual((self.data / 'blogs/unused.txt').read_text(), 'retain me')
        manifest = tomllib.loads((self.data / 'migration-map.toml').read_text(encoding='utf-8'))
        self.assertEqual(manifest['contents'][0]['old-url'], '/pages/blogs/AB Title.html')
        self.assertEqual(manifest['assets'][0]['old-url'], '/images/blogs/图 片.png')
        migrated = self.snapshot()
        self.assertFalse(migrate(self.data, True, self.plan)['changed'])
        self.assertEqual(migrated, self.snapshot())

    def test_local_article_links_move_but_explicit_language_and_external_urls_do_not(self):
        self.article('[other](other.md#heading)\n[external](https://example.com/other.md)\n[explicit](/en-US/pages/blogs/other.html)\n')
        (self.data / 'blogs/other.md').write_text('```toml\ntitle="Other"\npublished=false\nclass="Dev"\ndate="2025-01-01"\n```\n')
        self.plan.write_text(json.dumps({'blogs/中文.md':'blogs/english_title','blogs/other.md':'blogs/other'}))
        result = migrate(self.data, True, self.plan)
        self.addCleanup(shutil.rmtree, Path(result['backup']).parent)
        md = (self.data / 'blogs/english_title/zh-CN.md').read_text(encoding='utf-8')
        self.assertIn('../other/zh-CN.md#heading', md)
        self.assertIn('https://example.com/other.md', md)
        self.assertIn('/en-US/pages/blogs/other.html', md)

    def test_failure_never_replaces_original_content(self):
        for url in ('missing.png', '../../outside.png'):
            with self.subTest(url=url):
                self.article(f'![image]({url})\n')
                before = self.snapshot()
                with self.assertRaises(ValueError): migrate(self.data, True, self.plan)
                self.assertEqual(before, self.snapshot())
        self.article('text')
        (self.data / 'blogs/english_title').mkdir()
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, 'existing target'): migrate(self.data, True, self.plan)
        self.assertEqual(before, self.snapshot())

    def test_lfs_pointer_is_rejected_before_staging(self):
        self.article('text')
        (self.data / 'image.png').write_bytes(b'version https://git-lfs.github.com/spec/v1\n')
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, 'LFS pointer'): migrate(self.data, True, self.plan)
        self.assertEqual(before, self.snapshot())


if __name__ == '__main__': unittest.main()
