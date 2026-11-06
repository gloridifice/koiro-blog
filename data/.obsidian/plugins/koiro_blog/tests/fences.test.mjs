import assert from 'node:assert/strict';
import test from 'node:test';
import { build } from 'esbuild';

const result = await build({ entryPoints: ['src/fences.ts'], bundle: true, format: 'esm', platform: 'node', write: false });
const { fences, isComponent } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);

test('only explicit component fences execute, preserving nested source and excluding comments/frontmatter', () => {
  for (const info of ['html component', 'component html', 'html\tcomponent extra']) assert.ok(isComponent(info));
  for (const info of ['html', 'component', 'htmlcomponent', 'html mycomponent', 'HTML component']) assert.ok(!isComponent(info));
  const source = '<script>const value = `first\n\n\tlast %% literal`;</script>\n';
  const block = `\`\`\`html component\n${source}\`\`\``;
  for (const text of [block, block.split('\n').map(line => '> ' + line).join('\n'), '- Item\n\n' + block.split('\n').map(line => '    ' + line).join('\n')]) {
    const [token] = fences(text.replaceAll('\n', '\r\n'));
    assert.equal(token.content, source);
    assert.ok(isComponent(token.info));
  }
  const hidden = `---\ntext: |\n  ${block.replaceAll('\n', '\n  ')}\n---\n%%\n${block.replace('%% literal', 'literal')}\n%%\n\n${block}`;
  assert.equal(fences(hidden).filter(token => isComponent(token.info)).length, 1);
  assert.equal(fences(`\`\`\`\`markdown\n${block}\n\`\`\`\``).filter(token => isComponent(token.info)).length, 0);
});
