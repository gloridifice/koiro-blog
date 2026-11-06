import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync, existsSync, statSync, utimesSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { parse as parseToml } from 'smol-toml';
import { checkGeneratedFiles, checkLocalAssets, checkTree, prepareOutput, ROOT, validateSite } from './site.mjs';

function fixture(t) {
  const root = mkdtempSync(path.join(os.tmpdir(), 'site-build-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function write(relative, contents = '') {
    const file = path.join(root, relative);
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, contents);
    return file;
  }
  return { root, write };
}

test('preparation preserves managed resources, removes unknown stale files and copies hidden static inputs', t => {
  const { root, write } = fixture(t);
  write('assets/.nojekyll');
  write('assets/assets/css/reset.css', 'body{margin:0}');
  write('dist/deleted-article.html', 'obsolete');
  const retained = 'en-US/pages/blogs/retained.html';
  write(`dist/${retained}`, 'cached article');
  write('dist/.generated-site-files.json', JSON.stringify([retained]));
  write('dist/.incremental-site.json', '{"version":1,"resources":{}}');
  prepareOutput(path.join(root, 'assets'), path.join(root, 'dist'));
  assert.equal(existsSync(path.join(root, 'dist/deleted-article.html')), false);
  assert.equal(readFileSync(path.join(root, `dist/${retained}`), 'utf8'), 'cached article');
  assert.equal(existsSync(path.join(root, 'dist/.incremental-site.json')), true);
  assert.equal(existsSync(path.join(root, 'dist/.nojekyll')), true);
  assert.equal(readFileSync(path.join(root, 'dist/assets/css/reset.css'), 'utf8'), 'body{margin:0}');
  assert.equal(readFileSync(path.join(root, 'assets/assets/css/reset.css'), 'utf8'), 'body{margin:0}');
});

test('static images are reused until input changes, while other static files are rewritten and removed images disappear', t => {
  const { root, write } = fixture(t);
  const image = write('assets/album/photo.png', 'original image');
  write('assets/asset.js', 'original script');
  const assets = path.join(root, 'assets');
  const output = path.join(root, 'dist');
  prepareOutput(assets, output);
  const destination = path.join(output, 'album/photo.png');
  const before = statSync(destination, { bigint: true }).mtimeNs;
  write('dist/asset.js', 'edited output');
  prepareOutput(assets, output);
  assert.equal(statSync(destination, { bigint: true }).mtimeNs, before);
  assert.equal(readFileSync(path.join(output, 'asset.js'), 'utf8'), 'original script');
  writeFileSync(image, 'modified image');
  utimesSync(image, new Date(1000), new Date(1000));
  prepareOutput(assets, output);
  assert.equal(readFileSync(destination, 'utf8'), 'modified image');
  rmSync(image);
  prepareOutput(assets, output);
  assert.equal(existsSync(destination), false);
});

test('an unsafe retained-file manifest is rejected before modifying output', t => {
  const { root, write } = fixture(t);
  write('assets/index.html', 'new');
  write('dist/index.html', 'previous build');
  write('dist/.generated-site-files.json', '["../outside.txt"]');
  assert.throws(() => prepareOutput(path.join(root, 'assets'), path.join(root, 'dist')), /Unsafe generated path/);
  assert.equal(readFileSync(path.join(root, 'dist/index.html'), 'utf8'), 'previous build');
});

test('LFS pointers fail before deleting the previous build', t => {
  const { root, write } = fixture(t);
  write('assets/image.png', `version https://git-lfs.github.com/spec/v1\noid sha256:${'a'.repeat(64)}\nsize 4\n`);
  write('dist/index.html', 'previous build');
  assert.throws(() => prepareOutput(path.join(root, 'assets'), path.join(root, 'dist')), /Git LFS pointer/);
  assert.equal(readFileSync(path.join(root, 'dist/index.html'), 'utf8'), 'previous build');
});

test('output cannot contain or replace input', t => {
  const { root, write } = fixture(t);
  const source = write('assets/index.html', 'keep');
  const assets = path.join(root, 'assets');
  for (const output of [assets, path.join(assets, 'dist'), root]) {
    assert.throws(() => prepareOutput(assets, output), /overlap/);
  }
  assert.equal(readFileSync(source, 'utf8'), 'keep');
});

test('symlink output is rejected without touching its target', t => {
  const { root, write } = fixture(t);
  write('assets/index.html', 'new');
  const original = write('previous/index.html', 'previous build');
  const output = path.join(root, 'dist');
  symlinkSync(path.join(root, 'previous'), output, process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => prepareOutput(path.join(root, 'assets'), output), /symlink/);
  assert.throws(() => checkTree(root), /symlink/);
  assert.equal(readFileSync(original, 'utf8'), 'previous build');
});

test('local references resolve HTML entities, encoded paths, redirects, and CSS URLs; missing or escaping files fail', t => {
  const { root, write } = fixture(t);
  write('assets/image space.png', 'image');
  write('target.html', 'target');
  write('page.html', '<a href="target.html#heading">target</a>' +
    '<meta http-equiv="refresh" content="0; url=target.html">' +
    '<img src="/assets/image%20space.png?x=1&amp;y=2">' +
    '<script src="https://example.com/app.js"></script>' +
    '<img src="data:image/png;base64,AA=="><a href="#heading">heading</a>' +
    '<link href="/assets/css/root.css" rel="stylesheet">' +
    '<iframe srcdoc="&lt;script src=&quot;/assets/component.js&quot;&gt;&lt;/script&gt;"></iframe>');
  const componentScript = write('assets/component.js', '// component runtime');
  const css = write('assets/css/root.css', 'body{background:url("../image%20space.png")}');
  checkLocalAssets(root);
  rmSync(componentScript);
  assert.throws(() => checkLocalAssets(root), /component\.js/);
  write('assets/component.js', '// component runtime');
  for (const url of ['../../../outside.png', '../missing.png']) {
    writeFileSync(css, `body{background:url("${url}")}`);
    assert.throws(() => checkLocalAssets(root), /Missing or escaping local assets/);
  }
  writeFileSync(css, 'body{background:url("../image%20space.png")}');
  rmSync(path.join(root, 'target.html'));
  assert.throws(() => checkLocalAssets(root), /target.html/);
});

test('generated manifest enforces exact filename case and safe paths on every platform', t => {
  const { root, write } = fixture(t);
  const names = ['images/content/blogs/art/post/image.png', 'images/blogs/Art/legacy/image.png'];
  names.forEach(name => write(name, 'image'));
  const manifest = write('.generated-site-files.json', JSON.stringify(names));
  checkGeneratedFiles(root);
  writeFileSync(manifest, JSON.stringify(['images/blogs/art/legacy/image.png']));
  assert.throws(() => checkGeneratedFiles(root), /case mismatch/);
  for (const entry of ['../outside.html', '/etc/passwd', 'C:/outside.html', 'images\\file.png', '']) {
    writeFileSync(manifest, JSON.stringify([entry]));
    assert.throws(() => checkGeneratedFiles(root), /Unsafe generated path/);
  }
  writeFileSync(manifest, JSON.stringify({ invalid: 'manifest' }));
  assert.throws(() => checkGeneratedFiles(root), /Invalid generated-file manifest/);
});

test('artifact validation rejects malformed RSS, undefined entities, wrong-language links and invalid Wasm', t => {
  const { root, write } = fixture(t);
  for (const file of ['index.html', 'home.html', 'blogs.html', 'portfolio.html', 'about.html']) write(file, '<p>Page</p>');
  write('.nojekyll');
  write('CNAME', 'example.com');
  write('assets/css/root.css', 'body{}');
  write('assets/licenses/lucide.txt', 'fixture license');
  write('pkg/rs_wasm_script.js', '// fixture');
  const wasm = Buffer.from([0, 97, 115, 109, 1, 0, 0, 0]);
  const wasmFile = write('pkg/rs_wasm_script_bg.wasm', wasm);
  write('.generated-site-files.json', '[]');
  const locales = Object.keys(parseToml(readFileSync(path.join(ROOT, 'i18n/locales.toml'), 'utf8')).locales);
  const feed = (locale, link = `/${locale}/post.html`) =>
    `<rss version="2.0"><channel><language>${locale}</language><description><![CDATA[A & B]]></description><item><link>${link}</link></item></channel></rss>`;
  for (const locale of locales) {
    for (const file of ['home.html', 'blogs.html', 'portfolio.html', 'about.html', 'post.html']) write(`${locale}/${file}`, '<p>Page</p>');
    write(`${locale}/rss.xml`, feed(locale));
  }
  const rss = write('rss.xml', feed(locales[0]));
  validateSite(root);
  for (const invalid of ['<rss><channel></rss>', '<rss><channel>A & B</channel></rss>', '<rss><channel>&undefined;</channel></rss>']) {
    writeFileSync(rss, invalid);
    assert.throws(() => validateSite(root), /Invalid RSS/);
  }
  writeFileSync(rss, feed(locales[0]));
  const localized = path.join(root, locales[0], 'rss.xml');
  writeFileSync(localized, feed(locales[0], '/wrong-language/post.html'));
  assert.throws(() => validateSite(root), /Wrong-language RSS link/);
  writeFileSync(localized, feed(locales[0]));
  writeFileSync(wasmFile, Buffer.from('not wasm'));
  assert.throws(() => validateSite(root), /Invalid WebAssembly/);
});

test('input validation can exclude plugin tooling without weakening checks on content', t => {
  const { root, write } = fixture(t);
  const data = path.join(root, 'data');
  const plugins = path.join(data, 'component/plugins');
  write('data/component/plugins/koiro_blog/node_modules/library/.github/config.yml');
  symlinkSync(path.dirname(write('dependency/index.js')), path.join(plugins, 'linked'),
    process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => checkTree(data), /repository metadata|symlink/);
  checkTree(data, [plugins]);
  write('data/component/.gitattributes', '*.png filter=lfs');
  assert.throws(() => checkTree(data, [plugins]), /repository metadata/);
});

test('repository metadata cannot be published', t => {
  const { root, write } = fixture(t);
  write('.gitattributes', '*.png filter=lfs');
  assert.throws(() => checkTree(root), /repository metadata/);
});
