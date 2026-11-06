import assert from 'node:assert/strict';
import { writeFileSync, rmSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import test from 'node:test';
import { build } from 'esbuild';
import { chromium } from 'playwright';
import { componentDefines } from '../component-assets.mjs';

test('real browser and CM6 render, isolate, resize, edit and dispose components', { timeout: 180000 }, async () => {
  const unregistered = path.resolve('../../../../assets/component', `unregistered-${randomUUID()}`);
  let browser;
  try {
    writeFileSync(`${unregistered}.js`, 'document.documentElement.dataset.unregisteredPreset="yes";');
    writeFileSync(`${unregistered}.css`, 'html { --unregistered-preset: yes !important; }');
    const result = await build({
      entryPoints: ['tests/browser-entry.ts'], bundle: true, format: 'iife', globalName: 'fixture', write: false,
      alias: { obsidian: path.resolve('tests/editor-fields.ts') },
      define: componentDefines(),
    });
    browser = await chromium.launch(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {});
    const page = await browser.newPage();
    const network = [];
    page.on('request', request => { if (/^https?:/.test(request.url())) network.push(request.url()); });
    await page.setContent('<main></main>');
    await page.addScriptTag({ content: result.outputFiles[0].text });
    const source = `<style>body{color:rgb(255,0,0)} #space{height:40px}</style>
<button id="count">0</button><button id="resize">Resize</button><div id="space"></div>
<script>
let count=0;
document.querySelector('#count').onclick=()=>document.querySelector('#count').textContent=++count;
document.querySelector('#resize').onclick=()=>document.querySelector('#space').style.height=count++%2?'40px':'400px';
try { parent.document.body.dataset.escaped='yes'; } catch { document.body.dataset.isolated='yes'; }
</script>`;
    const text = `Intro\n\n\`\`\`html component\n${source}\n\`\`\`\n\nEnd`;
    await page.evaluate(text => { window.editor = fixture.editor(document.querySelector('main'), text); }, text);
    const frame = page.locator('iframe.article-component').contentFrame();
    await frame.locator('#count').click();
    assert.equal(await frame.locator('#count').textContent(), '1');
    assert.equal(await frame.locator('body').getAttribute('data-isolated'), 'yes');
    assert.equal(await page.locator('body').getAttribute('data-escaped'), null);
    assert.notEqual(await page.locator('body').evaluate(el => getComputedStyle(el).color), 'rgb(255, 0, 0)');
    await page.waitForFunction(() => parseFloat(document.querySelector('iframe').style.height) < 150);
    await frame.locator('#resize').click(); // count=1, small first
    await frame.locator('#resize').click();
    await page.waitForFunction(() => parseFloat(document.querySelector('iframe').style.height) > 400);
    await frame.locator('#resize').click();
    await page.waitForFunction(() => parseFloat(document.querySelector('iframe').style.height) < 150);

    // Selection enters the source range: the widget is destroyed, not merely hidden.
    await page.evaluate(() => editor.dispatch({ selection: { anchor: 15 } }));
    assert.equal(await page.locator('iframe').count(), 0);
    await page.evaluate(() => editor.dispatch({ selection: { anchor: 0 } }));
    await frame.locator('#count').waitFor();
    assert.equal(await frame.locator('#count').textContent(), '0');
    // An edit to only the info string must stop execution (Obsidian's reading cache can miss it).
    await page.evaluate(() => {
      const start = editor.state.doc.toString().indexOf(' component');
      editor.dispatch({ changes: { from: start, to: start + 10, insert: '' } });
    });
    assert.equal(await page.locator('iframe').count(), 0);
    await page.evaluate(() => editor.destroy());

    // Disposing while an attachment is loading must not resurrect a removed component.
    await page.evaluate(() => {
      const container = document.createElement('div');
      container.id = 'pending';
      document.body.append(container);
      let resolve;
      const pending = new Promise(done => { resolve = done; });
      const stop = fixture.mountComponent(container, '<img src="slow.png">', () => pending);
      stop();
      resolve('data:image/png;base64,AA==');
    });
    await page.waitForTimeout(50);
    assert.equal(await page.locator('#pending iframe').count(), 0);

    // Offline preset transport: real exported allowlist, data-URL CSS/JS, and resolved images.
    await page.evaluate(() => {
      const container = document.createElement('div');
      container.id = 'preset'; document.body.append(container);
      const source = '<style>.kc-compare { --compare-caption-color: rgb(0,128,0); }</style>' +
        '<figure class="kc-compare"><img data-side="a" src="a.svg" alt="A"><img data-side="b" src="b.svg" alt="B"><figcaption>Comparison</figcaption></figure>';
      window.stopPreset = fixture.mountComponent(container, source, async url => 'data:image/svg+xml,' + encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="500"><rect width="800" height="500" fill="${url === 'a.svg' ? 'red' : 'blue'}"/></svg>`));
    });
    const preset = page.locator('#preset iframe').contentFrame();
    await preset.locator('[data-state="ready"]').waitFor();
    await preset.getByRole('slider').focus();
    await preset.getByRole('slider').press('End');
    assert.equal(await preset.getByRole('slider').inputValue(), '100');
    assert.equal(await preset.locator('figcaption').evaluate(el => getComputedStyle(el).color), 'rgb(0, 128, 0)');
    assert.equal(await preset.locator('html').getAttribute('data-unregistered-preset'), null);
    assert.equal(await preset.locator('html').evaluate(el => getComputedStyle(el).getPropertyValue('--unregistered-preset')), '');
    await page.waitForFunction(() => parseFloat(document.querySelector('#preset iframe').style.height) > 400);
    assert.deepEqual(network, []);
    await page.evaluate(() => stopPreset());
    assert.equal(await page.locator('#preset iframe').count(), 0);
  } finally {
    await browser?.close();
    rmSync(`${unregistered}.css`, { force: true });
    rmSync(`${unregistered}.js`, { force: true });
  }
});
