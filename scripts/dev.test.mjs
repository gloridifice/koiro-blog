import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { createServer, request } from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { serve } from './dev.mjs';

function fixture(t) {
  const root = mkdtempSync(path.join(os.tmpdir(), 'site dev '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(path.join(root, 'dist'));
  return root;
}

function close(t, server) {
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
}

function get(server, pathname, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = request({ host: '127.0.0.1', port: server.address().port, path: pathname, method }, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
      res.on('error', reject);
    });
    req.on('error', reject);
    req.end();
  });
}

test('dev server serves current files without caching and sends correct CSS, Wasm and HEAD responses', async t => {
  const root = fixture(t);
  const dist = path.join(root, 'dist');
  writeFileSync(path.join(dist, 'index.html'), '<h1>Preview</h1>');
  const css = path.join(dist, 'style space.css');
  writeFileSync(css, 'p{color:red}');
  const wasm = Buffer.from([0, 97, 115, 109, 1, 0, 0, 0]);
  writeFileSync(path.join(dist, 'app.wasm'), wasm);
  const server = await serve(dist, { port: 0 });
  close(t, server);
  assert.equal((await get(server, '/')).body.toString(), '<h1>Preview</h1>');
  let response = await get(server, '/style%20space.css?v=1');
  assert.equal(response.status, 200);
  assert.equal(response.headers['content-type'], 'text/css; charset=utf-8');
  assert.equal(response.headers['cache-control'], 'no-store');
  assert.equal(response.body.toString(), 'p{color:red}');
  writeFileSync(css, 'p{color:blue}');
  assert.equal((await get(server, '/style%20space.css')).body.toString(), 'p{color:blue}');
  response = await get(server, '/app.wasm');
  assert.equal(response.headers['content-type'], 'application/wasm');
  assert.deepEqual(response.body, wasm);
  response = await get(server, '/app.wasm', 'HEAD');
  assert.equal(response.headers['content-length'], '8');
  assert.equal(response.body.length, 0);
  assert.equal((await get(server, '/missing.html')).status, 404);
  assert.equal((await get(server, '/', 'POST')).status, 405);
});

test('an occupied requested port falls back to a higher free port without disturbing its owner', async t => {
  const root = fixture(t);
  writeFileSync(path.join(root, 'dist/index.html'), 'preview');
  const occupied = createServer((_req, res) => res.end('other service'));
  await new Promise(resolve => occupied.listen(0, '127.0.0.1', resolve));
  close(t, occupied);
  const port = occupied.address().port;
  const server = await serve(path.join(root, 'dist'), { port });
  close(t, server);
  assert.ok(server.address().port > port);
  assert.equal((await get(server, '/')).body.toString(), 'preview');
  assert.equal((await get(occupied, '/')).body.toString(), 'other service');
});

test('dev server denies encoded traversal and symlink escapes, redirects directories, and rejects malformed URLs', async t => {
  const root = fixture(t);
  const dist = path.join(root, 'dist');
  writeFileSync(path.join(root, 'secret.txt'), 'private');
  mkdirSync(path.join(dist, 'nested'));
  writeFileSync(path.join(dist, 'nested/index.html'), 'nested');
  mkdirSync(path.join(root, 'outside'));
  writeFileSync(path.join(root, 'outside/secret.txt'), 'private');
  symlinkSync(path.join(root, 'outside'), path.join(dist, 'escape'), process.platform === 'win32' ? 'junction' : 'dir');
  const server = await serve(dist, { port: 0 });
  close(t, server);
  for (const pathname of ['/../secret.txt', '/%2e%2e/secret.txt', '/escape/secret.txt']) {
    assert.equal((await get(server, pathname)).status, 403, pathname);
  }
  for (const pathname of ['/%ZZ', '/%00', '/..%5csecret.txt']) {
    assert.equal((await get(server, pathname)).status, 400, pathname);
  }
  const redirect = await get(server, '/nested');
  assert.equal(redirect.status, 301);
  assert.equal(redirect.headers.location, '/nested/');
  assert.equal((await get(server, '/nested/')).body.toString(), 'nested');
});
