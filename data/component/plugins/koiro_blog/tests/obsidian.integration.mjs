import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { chromium } from 'playwright';

async function eventually(check) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await check()) return;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.fail('Timed out waiting for the file/view update');
}

test('Obsidian exposes TOML files, persists edits through rename/move, and creates files from explorer menus', { timeout: 120000 }, async () => {
  assert.ok(process.env.OBSIDIAN_EXECUTABLE, 'Set OBSIDIAN_EXECUTABLE to a desktop Obsidian executable');
  const root = mkdtempSync(path.join(os.tmpdir(), 'koiro-blog-obsidian-'));
  const vault = path.join(root, 'vault');
  const config = path.join(root, 'obsidian');
  const write = (relative, text) => {
    const file = path.join(vault, relative);
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, text);
  };
  write('.obsidian/community-plugins.json', '["koiro_blog"]');
  write('.obsidian/app.json', '{"showUnsupportedFiles":false}');
  write('posts/meta.toml', '# Keep comments\nid = "post"\npublished = false\n');
  write('posts/note.md', '# Note');
  mkdirSync(path.join(vault, 'archive'));
  mkdirSync(path.join(vault, '.obsidian/plugins/koiro_blog'), { recursive: true });
  for (const file of ['main.js', 'manifest.json', 'styles.css']) {
    copyFileSync(file, path.join(vault, '.obsidian/plugins/koiro_blog', file));
  }
  mkdirSync(config);
  writeFileSync(path.join(config, 'obsidian.json'), JSON.stringify({
    updateDisabled: true, vaults: { test: { path: vault, ts: Date.now(), open: true } },
  }));
  // Separate application data as well as user-data-dir: never drive an existing user vault.
  const child = spawn(process.env.OBSIDIAN_EXECUTABLE, [`--user-data-dir=${config}`, '--remote-debugging-port=0'], {
    env: { ...process.env, APPDATA: root, XDG_CONFIG_HOME: root }, stdio: ['ignore', 'ignore', 'pipe'],
  });
  const exited = new Promise(resolve => child.once('close', resolve));
  let browser;
  try {
    const endpoint = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Obsidian did not expose its debugging endpoint')), 20000);
      let stderr = '';
      child.stderr.on('data', data => {
        stderr += data;
        const match = /DevTools listening on (ws:\/\/[^\s]+)/.exec(stderr);
        if (match) { clearTimeout(timer); resolve(match[1]); }
      });
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('exit', code => { clearTimeout(timer); reject(new Error(`Obsidian exited: ${code}`)); });
    });
    browser = await chromium.connectOverCDP(endpoint);
    const context = browser.contexts()[0];
    const page = context.pages()[0] ?? await context.waitForEvent('page');
    await page.waitForFunction(() => window.app?.workspace?.layoutReady);
    const trust = page.getByRole('button', { name: /Trust author and enable plugins|信任仓库作者并启用插件/ });
    await trust.click();
    await page.waitForFunction(() => !!app.plugins.plugins.koiro_blog);
    await page.locator('.modal-container .modal-close-button').click();

    const treeFile = name => page.locator(`.nav-file-title[data-path="${name}"]`);
    const treeFolder = name => page.locator(`.nav-folder-title[data-path="${name}"]`);
    await treeFolder('posts').click();
    await treeFile('posts/meta.toml').click();
    const editor = page.getByRole('textbox', { name: 'TOML editor' });
    await eventually(async () => await editor.inputValue() === '# Keep comments\nid = "post"\npublished = false\n');
    const edited = '# Keep comments\nid = "edited"\npublished = true\n';
    await editor.fill(edited);
    await eventually(() => readFileSync(path.join(vault, 'posts/meta.toml'), 'utf8') === edited);

    // Native FileManager is the same owner used by explorer rename and move actions.
    await page.evaluate(async () => {
      await app.fileManager.renameFile(app.vault.getAbstractFileByPath('posts/meta.toml'), 'posts/config.toml');
      await app.fileManager.renameFile(app.vault.getAbstractFileByPath('posts/config.toml'), 'archive/config.toml');
    });
    await treeFolder('archive').click();
    await treeFile('archive/config.toml').waitFor();
    assert.equal(existsSync(path.join(vault, 'posts/meta.toml')), false);
    const moved = edited + 'title = "中文"\n';
    await editor.fill(moved);
    await eventually(() => readFileSync(path.join(vault, 'archive/config.toml'), 'utf8') === moved);
    write('archive/config.toml', '# External edit\nid = "external"\n');
    await eventually(async () => await editor.inputValue() === '# External edit\nid = "external"\n');

    await treeFolder('posts').click({ button: 'right' });
    await page.getByText('New TOML file', { exact: true }).click();
    await treeFile('posts/Untitled.toml').waitFor();
    await page.waitForFunction(() => app.workspace.getActiveFile()?.path === 'posts/Untitled.toml');
    await editor.fill('id = "keep"\n');
    // Switching files must flush pending edits, not rely on the two-second debounce.
    await treeFile('posts/Untitled.toml').click({ button: 'right' });
    await page.getByText('New TOML file', { exact: true }).click();
    await treeFile('posts/Untitled 1.toml').waitFor();
    await page.waitForFunction(() => app.workspace.getActiveFile()?.path === 'posts/Untitled 1.toml');
    await eventually(() => readFileSync(path.join(vault, 'posts/Untitled.toml'), 'utf8') === 'id = "keep"\n');
    assert.equal(readFileSync(path.join(vault, 'posts/Untitled 1.toml'), 'utf8'), '');
    assert.equal(await editor.inputValue(), '');

    const tree = page.locator('.nav-files-container');
    const bounds = await tree.boundingBox();
    await tree.click({ button: 'right', position: { x: 10, y: bounds.height - 10 } });
    await page.getByText('New TOML file', { exact: true }).click();
    await treeFile('Untitled.toml').waitFor();
    await page.waitForFunction(() => app.workspace.getActiveFile()?.path === 'Untitled.toml');
    await editor.fill('root = true\n');
    await page.evaluate(() => app.workspace.getLeaf(false).detach());
    await eventually(() => readFileSync(path.join(vault, 'Untitled.toml'), 'utf8') === 'root = true\n');
    await treeFile('Untitled.toml').click();
    await eventually(async () => await editor.inputValue() === 'root = true\n');

    await page.evaluate(() => app.plugins.disablePlugin('koiro_blog'));
    await treeFolder('posts').click({ button: 'right' });
    assert.equal(await page.getByText('New TOML file', { exact: true }).count(), 0);
    assert.equal(await editor.count(), 0);
  } finally {
    await browser?.close();
    child.kill();
    await exited;
    rmSync(root, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
  }
});
