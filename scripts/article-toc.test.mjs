import assert from 'node:assert/strict';
import { cpSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { chromium } from 'playwright';
import { serve } from './dev.mjs';
import { completed, ROOT, start } from './site.mjs';

// The generated article in a real browser owns both hierarchy and interaction
// coverage. No duplicate tests of private tree builders or hover state helpers.
test('desktop article rail previews heading branches and navigates the real article', { timeout: 180000 }, async t => {
  const root = mkdtempSync(path.join(ROOT, '.toc-browser-'));
  let server;
  let browser;
  try {
    for (const kind of ['blogs', 'devlogs', 'actives', 'portfolio']) mkdirSync(path.join(root, 'data', kind), { recursive: true });
    writeFileSync(path.join(root, 'data/friends.toml'), 'friends = []\n');
    const prose = '\n\n' + Array.from({ length: 5 }, () => '这是文章正文。目录只在需要时展开，不打断阅读；移动到相邻标题，可以继续浏览这一章节的内容。').join('\n\n') + '\n\n';
    const longTitle = '需要完整显示的较长目录标题';
    const articles = {
      demo: '## 一级标题1' + prose + '### 二级标题1' + prose + '#### 三级标题1' + prose +
        '##### 更深的正文标题' + prose + '#### 三级标题2' + prose + '### 二级标题2' + prose +
        '#### 另一个分支' + prose + '### 二级标题3' + prose + '## 一级标题2' + prose +
        '一级标题3\n--------' + prose + '#### 跳级标题' + prose + '###### 跳级子标题' + prose +
        '## ' + longTitle + prose + '### 二级标题1' + prose,
      empty: '这篇文章没有正文标题。',
      long: Array.from({ length: 45 }, (_, i) => `## 章节 ${i}\n\n` +
        (i === 0 ? Array.from({ length: 35 }, (_, j) => `### 小节 ${j}\n\n#### 细节 ${j}\n\n正文\n\n`).join('') : '正文\n\n')).join(''),
    };
    for (const [id, markdown] of Object.entries(articles)) {
      const post = path.join(root, 'data/blogs', id);
      mkdirSync(post);
      writeFileSync(path.join(post, 'meta.toml'), `id = "${id}"\npublished = true\nclass = "Dev"\ndate = "2025-01-01"\n`);
      writeFileSync(path.join(post, 'zh-CN.md'), '```toml\ntitle = "Preview Rail 文章目录"\nsummary = "目录交互验证"\n```\n\n' + markdown);
    }
    const relative = path.relative(ROOT, root).replaceAll('\\', '/');
    const args = `${relative}/data ${relative}/output i18n`;
    await completed(start(path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'),
      ['--no-daemon', 'run', process.platform === 'win32' ? `--args="${args}"` : `--args=${args}`]));
    const output = path.join(root, 'output');
    cpSync(path.join(ROOT, 'assets/assets'), path.join(output, 'assets'), { recursive: true });
    await completed(start(process.execPath, [path.join(ROOT, 'node_modules/sass/sass.js'),
      `src/main/stylesheet:${relative}/output/assets/css`, '--no-source-map']));
    server = await serve(output, { port: 0 });
    browser = await chromium.launch(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {});
    const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const url = id => `http://127.0.0.1:${server.address().port}/zh-CN/pages/blogs/${id}.html`;
    const screenshots = path.join(ROOT, 'build/reports/article-toc');
    mkdirSync(screenshots, { recursive: true });
    await page.goto(url('demo'));
    const rail = page.getByRole('navigation', { name: '文章目录', exact: true });
    const first = rail.getByRole('link', { name: '一级标题1', exact: true });
    const child = rail.getByRole('link', { name: '二级标题1', exact: true });
    const grandchild = rail.getByRole('link', { name: '三级标题1', exact: true });
    const columnTexts = () => rail.locator('ul:visible').evaluateAll(columns => columns.map(column =>
      [...column.querySelectorAll('a')].map(link => link.textContent.trim())));

    await t.test('compact state reveals only root ticks, then opens only the hovered branch', async () => {
      await first.waitFor({ state: 'visible' });
      assert.equal(await rail.locator('.article-toc__label:visible').count(), 0);
      assert.deepEqual(await columnTexts(), [['一级标题1', '一级标题2', '一级标题3', longTitle]]);
      assert.equal(await rail.getByRole('link', { name: 'Preview Rail 文章目录' }).count(), 0);
      await page.screenshot({ path: path.join(screenshots, 'collapsed.png') });
      await first.hover({ position: { x: 8, y: 14 } });
      await child.waitFor({ state: 'visible' });
      assert.deepEqual(await columnTexts(), [
        ['一级标题1', '一级标题2', '一级标题3', longTitle], ['二级标题1', '二级标题2', '二级标题3'],
      ]);
      assert.equal(await rail.locator('.article-toc__label:visible').count(), 7);
      assert.ok(await rail.getByRole('link', { name: longTitle }).locator('span').last()
        .evaluate(el => el.scrollWidth <= el.clientWidth), 'ordinary long titles must not be squeezed into the gutter');
      await page.screenshot({ path: path.join(screenshots, 'second-level.png') });
      // Traverse the actual gap horizontally, rather than teleporting directly to a child.
      const firstBox = await first.boundingBox();
      const childBox = await child.boundingBox();
      await page.mouse.move(childBox.x + 8, firstBox.y + firstBox.height / 2, { steps: 12 });
      await child.hover();
      await grandchild.waitFor({ state: 'visible' });
      assert.deepEqual((await columnTexts())[2], ['三级标题1', '三级标题2']);
      assert.equal(await rail.getByRole('link', { name: '更深的正文标题' }).count(), 0);
      await page.screenshot({ path: path.join(screenshots, 'third-level.png') });
      const firstBounds = await first.boundingBox();
      const secondBounds = await child.boundingBox();
      const thirdBounds = await grandchild.boundingBox();
      assert.ok(firstBounds.x < secondBounds.x && secondBounds.x < thirdBounds.x);
      assert.ok(await rail.locator('ul:visible, ul:visible a').evaluateAll(elements => elements.every(el => {
        const style = getComputedStyle(el);
        return style.backgroundColor === 'rgba(0, 0, 0, 0)' && style.boxShadow === 'none' && parseFloat(style.borderTopWidth) === 0;
      })), 'columns and link rows must remain transparent');
      assert.ok(await rail.locator('ul[data-depth="0"] .article-toc__label').evaluateAll(labels =>
        labels.every(label => getComputedStyle(label).backgroundColor === 'rgba(0, 0, 0, 0)')),
      'root title labels stay transparent');
      assert.ok(await rail.locator('ul:not([data-depth="0"]) .article-toc__label:visible').evaluateAll(labels => labels.every(label => {
        const style = getComputedStyle(label);
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = 1;
        const context = canvas.getContext('2d');
        context.fillStyle = style.backgroundColor;
        context.fillRect(0, 0, 1, 1);
        const alpha = context.getImageData(0, 0, 1, 1).data[3];
        return alpha > 0 && alpha < 255 && parseFloat(style.paddingTop) > 0 &&
          parseFloat(style.paddingLeft) > 0 && parseFloat(style.borderTopLeftRadius) > 0;
      })), 'child title labels need a translucent padded, rounded backdrop');
      await rail.getByRole('link', { name: '二级标题2', exact: true }).hover();
      await rail.getByRole('link', { name: '另一个分支' }).waitFor({ state: 'visible' });
      assert.deepEqual((await columnTexts())[2], ['另一个分支']);
      await rail.getByRole('link', { name: '二级标题3', exact: true }).hover();
      assert.equal((await columnTexts()).length, 2, 'leaf has no empty preview');
      await rail.getByRole('link', { name: '一级标题3', exact: true }).hover();
      await rail.getByRole('link', { name: '跳级标题', exact: true }).hover();
      assert.deepEqual((await columnTexts())[2], ['跳级子标题'], 'skipped Markdown levels attach to the nearest parent');
      await page.mouse.move(900, 100);
      await page.waitForFunction(() => !document.querySelector('.article-toc').hasAttribute('data-open'));
      assert.equal(await rail.locator('.article-toc__label:visible').count(), 0);
    });

    await t.test('anchors, scroll tracking, keyboard navigation and reduced motion work without Wasm', async () => {
      await page.goto(url('demo'));
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await first.focus();
      await first.press('ArrowRight');
      assert.equal(await child.evaluate(el => el === document.activeElement), true);
      await child.press('ArrowRight');
      assert.equal(await grandchild.evaluate(el => el === document.activeElement), true);
      const target = await grandchild.getAttribute('href');
      const linkId = await grandchild.getAttribute('id');
      await grandchild.press('Enter');
      assert.ok(page.url().endsWith(target));
      await page.waitForFunction(() => {
        const target = document.getElementById(location.hash.slice(1));
        return Math.abs(target.getBoundingClientRect().top - 100) < 2;
      });
      await page.waitForFunction(id => document.getElementById(id).getAttribute('aria-current') === 'location', linkId);
      await first.focus();
      await first.press('ArrowRight');
      await child.press('ArrowRight');
      await grandchild.press('Escape');
      assert.equal(await first.evaluate(el => el === document.activeElement), true);
      assert.equal(await rail.locator('ul:visible').count(), 1);
      assert.equal(await first.getAttribute('aria-expanded'), 'false');
      assert.equal(await page.locator('html').evaluate(el => getComputedStyle(el).scrollBehavior), 'auto');
      // Scrolling into a heading deeper than three menu levels retains its visible ancestor.
      await page.getByRole('heading', { name: '更深的正文标题' }).evaluate(el => el.scrollIntoView());
      await page.waitForFunction(id => document.getElementById(id).getAttribute('aria-current') === 'location', linkId);
      await page.getByRole('heading', { name: '一级标题2', exact: true }).evaluate(el => el.scrollIntoView());
      const second = rail.getByRole('link', { name: '一级标题2', exact: true });
      await page.waitForFunction(id => document.getElementById(id).getAttribute('aria-current') === 'location', await second.getAttribute('id'));
      assert.equal(await rail.locator('ul:visible').count(), 1, 'reading position must not force open a preview');
      await first.focus();
      await first.press('ArrowDown');
      assert.equal(await second.evaluate(el => el === document.activeElement), true);
      await second.press('Tab');
      assert.notEqual(await page.evaluate(() => document.activeElement.tagName), 'BODY');
    });

    await t.test('desktop resizing stays bounded and phone or narrow layouts have no directory', async () => {
      await page.goto(url('demo'));
      for (const width of [1440, 1280]) {
        await page.setViewportSize({ width, height: 900 });
        await first.hover({ position: { x: 8, y: 14 } });
        await child.hover();
        await grandchild.waitFor({ state: 'visible' });
        assert.ok(await rail.locator('ul:visible').evaluateAll(columns => columns.every(el => {
          const rect = el.getBoundingClientRect();
          return rect.left >= 0 && rect.right <= innerWidth && rect.top >= 0 && rect.bottom <= innerHeight;
        })));
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      }
      for (const width of [1024, 768, 390]) {
        await page.setViewportSize({ width, height: 844 });
        assert.equal(await rail.isVisible(), false);
        assert.equal(await page.getByRole('navigation', { name: '文章目录' }).count(), 0);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      }
      await page.screenshot({ path: path.join(screenshots, 'mobile.png') });
      await page.setViewportSize({ width: 1920, height: 1080 });
      await first.waitFor({ state: 'visible' });
      assert.equal(await rail.locator('ul:visible').count(), 1);
    });

    await t.test('long lists scroll independently and articles without headings omit the rail', async () => {
      await page.setViewportSize({ width: 1920, height: 1080 });
      await page.goto(url('long'));
      await rail.getByRole('link', { name: '章节 0', exact: true }).focus();
      await rail.getByRole('link', { name: '章节 0', exact: true }).press('ArrowRight');
      await rail.getByRole('link', { name: '小节 0', exact: true }).press('End');
      const lastChild = rail.getByRole('link', { name: '小节 34', exact: true });
      assert.equal(await lastChild.evaluate(el => el === document.activeElement), true);
      const detail = rail.getByRole('link', { name: '细节 34', exact: true });
      await detail.waitFor({ state: 'visible' });
      assert.ok(await lastChild.evaluate(el => el.closest('ul').scrollTop > 0));
      await lastChild.press('ArrowLeft');
      await rail.getByRole('link', { name: '章节 0', exact: true }).press('End');
      assert.equal(await rail.getByRole('link', { name: '章节 44', exact: true }).evaluate(el => el === document.activeElement), true);
      assert.equal(await rail.locator('ul:visible').count(), 1);
      await page.goto(url('empty'));
      assert.equal(await page.locator('.article-toc').count(), 0);
      assert.deepEqual(errors, []);
    });
  } finally {
    await browser?.close();
    if (server) {
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
    rmSync(root, { recursive: true, force: true });
  }
});
