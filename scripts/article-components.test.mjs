import assert from 'node:assert/strict';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { chromium } from 'playwright';
import { serve } from './dev.mjs';
import { completed, ROOT, start } from './site.mjs';

// Exercise the actual Markdown -> Kotlin generator -> browser boundary.
// Run separately from the fast Node tests: npm run test:components.
test('articles render responsive math and media with isolated interactive components', { timeout: 180000 }, async () => {
  const root = mkdtempSync(path.join(ROOT, '.component-browser-'));
  let server;
  let browser;
  const unregistered = path.join(ROOT, 'assets/component', `unregistered-${path.basename(root)}`);
  try {
    // Real, publishable files must stay inert unless explicitly registered in Kotlin.
    writeFileSync(`${unregistered}.js`, 'document.documentElement.dataset.unregisteredPreset="yes";');
    writeFileSync(`${unregistered}.css`, 'html { --unregistered-preset: yes !important; }');
    for (const kind of ['blogs', 'devlogs', 'actives', 'portfolio']) mkdirSync(path.join(root, 'data', kind), { recursive: true });
    const post = path.join(root, 'data/blogs/demo');
    mkdirSync(post);
    writeFileSync(path.join(post, 'meta.toml'), 'id = "demo"\npublished = true\nclass = "Dev"\ndate = "2025-01-01"\n');
    const component = `<style>body { color: rgb(255, 0, 0); } #space { height: 40px; }</style>
<button id="counter">0</button><button id="resize">Resize</button><div id="space"></div>
<script>
let count = 0;
document.querySelector('#counter').onclick = () => document.querySelector('#counter').textContent = ++count;
document.querySelector('#resize').onclick = () => {
  const space = document.querySelector('#space');
  space.style.height = space.style.height === '400px' ? '40px' : '400px';
};
try { parent.document.body.dataset.escaped = 'yes'; } catch { document.body.dataset.isolated = 'yes'; }
</script>`;
    for (const [name, color] of [['a', 'red'], ['b', 'blue']]) {
      writeFileSync(path.join(post, `${name}.svg`), `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="500"><rect width="800" height="500" fill="${color}"/></svg>`);
    }
    const comparison = `<figure class="kc-compare"><img data-side="a" src="a.svg" alt="A"><img data-side="b" src="b.svg" alt="B"><figcaption>Comparison</figcaption></figure>`;
    const article = '\n# Article rendering and component layout\n\n浓度 $N_i$。\n\n' +
      '$$ N_{new} = N_{previous} e^{-a\\Delta t} + \\frac{S}{a}(1-e^{-a\\Delta t}) + ' +
      '\\frac{fa}{a-b}(e^{-b\\Delta t}-e^{-a\\Delta t}) $$\n\n' +
      '![Captioned](a.svg)\nFigure caption.\n\n`$literal$`\n';
    writeFileSync(path.join(post, 'zh-CN.md'), '```toml\ntitle = "Browser fixture"\nsummary = "Summary only"\n```\n' + article +
      `\n\`\`\`html component\n${component}\n\`\`\`\n\n\`\`\`html component\n${component}\n\`\`\`\n` +
      `\n\`\`\`html component\n<style>.kc-compare { --compare-caption-color: rgb(0,128,0); }</style>${comparison}${comparison}\n\`\`\`\n`);
    const relative = path.relative(ROOT, root).replaceAll('\\', '/');
    const args = `${relative}/data ${relative}/output i18n`;
    await completed(start(path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'),
      ['--no-daemon', 'run', process.platform === 'win32' ? `--args="${args}"` : `--args=${args}`]));
    const output = path.join(root, 'output');
    cpSync(path.join(ROOT, 'assets/assets'), path.join(output, 'assets'), { recursive: true });
    cpSync(path.join(ROOT, 'assets/component'), path.join(output, 'component'), { recursive: true });
    await completed(start(process.execPath, [path.join(ROOT, 'node_modules/sass/sass.js'),
      `src/main/stylesheet:${path.relative(ROOT, output).replaceAll('\\', '/')}/assets/css`, '--no-source-map']));
    const feed = readFileSync(path.join(output, 'zh-CN/rss.xml'), 'utf8');
    assert.ok(feed.includes('Summary only'));
    assert.ok(feed.includes('/zh-CN/pages/blogs/demo.html'));
    assert.ok(!feed.includes('<iframe') && !feed.includes('srcdoc') && !feed.includes('let count'));

    server = await serve(output, { port: 0 });
    browser = await chromium.launch(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {});
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    await page.goto(`http://127.0.0.1:${server.address().port}/zh-CN/pages/blogs/demo.html`);
    assert.equal(await page.locator('.inline_math .katex').count(), 1);
    assert.equal(await page.locator('.equation .katex-display').count(), 1);
    assert.equal(await page.locator('.katex-error').count(), 0);
    assert.equal(await page.locator('code').textContent(), '$literal$');
    for (const width of [1440, 768, 375, 320]) {
      await page.setViewportSize({ width, height: 900 });
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${width}px page overflow`);
      const image = await page.locator('p > img').evaluate(el => {
        const rect = el.getBoundingClientRect();
        return { width: rect.width, height: rect.height, naturalWidth: el.naturalWidth, naturalHeight: el.naturalHeight,
          available: el.parentElement.clientWidth };
      });
      assert.ok(image.width <= image.available, `${width}px captioned image`);
      assert.ok(Math.abs(image.width / image.height - image.naturalWidth / image.naturalHeight) < 0.01);
      const math = await page.locator('.katex-display').evaluate(el => {
        el.scrollLeft = 0;
        const start = el.scrollLeft;
        el.scrollLeft = el.scrollWidth;
        return { start, end: el.scrollLeft, overflow: el.scrollWidth > el.clientWidth,
          height: el.clientHeight, contentHeight: el.scrollHeight };
      });
      assert.ok(math.contentHeight <= math.height + 1, `${width}px math vertical clipping`);
      if (width < 400) {
        assert.ok(math.overflow && math.end > math.start, `${width}px long math must scroll locally`);
      }
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    const frames = page.locator('iframe.article-component');
    const first = frames.nth(0).contentFrame();
    const second = frames.nth(1).contentFrame();
    await first.locator('#counter').click();
    assert.equal(await first.locator('#counter').textContent(), '1');
    assert.equal(await second.locator('#counter').textContent(), '0');
    assert.equal(await first.locator('body').getAttribute('data-isolated'), 'yes');
    assert.equal(await page.locator('body').getAttribute('data-escaped'), null);
    assert.equal(await first.locator('body').evaluate(el => getComputedStyle(el).color), 'rgb(255, 0, 0)');
    assert.notEqual(await page.locator('body').evaluate(el => getComputedStyle(el).color), 'rgb(255, 0, 0)');

    const height = () => frames.nth(0).evaluate(el => parseFloat(el.style.height));
    await page.waitForFunction(() => parseFloat(document.querySelector('iframe.article-component').style.height) < 150);
    const small = await height();
    // Computed backgrounds can be transparent while Chromium paints a white canvas
    // when the dark host and srcdoc use different color schemes. Compare real pixels.
    await first.locator('#space').scrollIntoViewIfNeeded();
    const space = await first.locator('#space').boundingBox();
    const clip = { x: Math.ceil(space.x + 4), y: Math.ceil(space.y + 4), width: 4, height: 4 };
    const visible = await page.screenshot({ clip });
    await frames.nth(0).evaluate(el => { el.style.visibility = 'hidden'; });
    const backdrop = await page.screenshot({ clip });
    await frames.nth(0).evaluate(el => { el.style.visibility = ''; });
    assert.deepEqual(visible, backdrop, 'component canvas must reveal the host background');
    await first.locator('body').evaluate(el => { el.style.background = 'rgb(255, 255, 255)'; });
    assert.notDeepEqual(await page.screenshot({ clip }), backdrop, 'author backgrounds must remain visible');
    await first.locator('body').evaluate(el => { el.style.background = ''; });
    await first.locator('#resize').click();
    await page.waitForFunction(() => parseFloat(document.querySelector('iframe.article-component').style.height) > 400);
    await first.locator('#resize').click();
    await page.waitForFunction(expected => parseFloat(document.querySelector('iframe.article-component').style.height) === expected, small);

    // A different sandboxed window must not be able to resize registered components.
    await page.evaluate(() => new Promise(resolve => {
      const rogue = document.createElement('iframe');
      rogue.sandbox = 'allow-scripts';
      rogue.srcdoc = '<script>parent.postMessage({type:"koiro:component-height",height:9999}, "*")<\/script>';
      rogue.onload = () => setTimeout(resolve, 100);
      document.body.append(rogue);
    }));
    assert.equal(await height(), small);
    // A genuine component's malformed measurement is ignored; huge values are bounded.
    await first.locator('body').evaluate(() => {
      parent.postMessage({ type: 'koiro:component-height', height: '9999' }, '*');
      parent.postMessage({ type: 'koiro:component-height', height: Infinity }, '*');
      parent.postMessage({ type: 'koiro:component-height', height: -1 }, '*');
    });
    await page.waitForTimeout(100);
    assert.equal(await height(), small);
    await first.locator('body').evaluate(() => parent.postMessage({ type: 'koiro:component-height', height: 1000000 }, '*'));
    await page.waitForFunction(() => document.querySelector('iframe.article-component').style.height === '10000px');

    // Declarative markup needs no author CSS/JS or control scaffolding.
    const compare = frames.nth(2).contentFrame();
    await compare.locator('[data-state="ready"]').first().waitFor();
    assert.equal(await compare.getByRole('slider').count(), 2);
    const sliders = compare.getByRole('slider');
    await sliders.first().focus();
    await sliders.first().press('Home');
    assert.equal(await sliders.first().inputValue(), '0');
    assert.equal(await sliders.nth(1).inputValue(), '50');
    await sliders.first().press('End');
    assert.equal(await sliders.first().inputValue(), '100');
    assert.equal(await compare.locator('figcaption').first().evaluate(el => getComputedStyle(el).color), 'rgb(0, 128, 0)');
    assert.notEqual(await sliders.first().getAttribute('aria-describedby'), await sliders.nth(1).getAttribute('aria-describedby'));
    assert.equal(await compare.locator('.kc-compare-stage').first().evaluate(el => getComputedStyle(el).borderTopWidth), '0px');
    for (const frame of [first, compare]) {
      assert.equal(await frame.locator('html').getAttribute('data-unregistered-preset'), null);
      assert.equal(await frame.locator('html').evaluate(el => getComputedStyle(el).getPropertyValue('--unregistered-preset')), '');
    }
    await page.setViewportSize({ width: 375, height: 700 });
    assert.ok(await frames.nth(0).evaluate(el => el.getBoundingClientRect().width <= innerWidth));
    await compare.locator('.kc-compare-stage').first().scrollIntoViewIfNeeded();
    const stage = await compare.locator('.kc-compare-stage').first().boundingBox();
    await page.mouse.click(stage.x + stage.width / 4, stage.y + stage.height / 2);
    assert.ok(Math.abs(Number(await sliders.first().inputValue()) - 25) <= 1);
    assert.equal(await compare.locator('body').evaluate(el => el.scrollWidth <= innerWidth), true);
  } finally {
    await browser?.close();
    if (server) {
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
    rmSync(root, { recursive: true, force: true });
    rmSync(`${unregistered}.css`, { force: true });
    rmSync(`${unregistered}.js`, { force: true });
  }
});
