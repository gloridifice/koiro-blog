#!/usr/bin/env node
import { createReadStream, existsSync } from 'node:fs';
import { realpath, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';
import { build, completed, DIST, isWithin, styles } from './site.mjs';

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json', '.map': 'application/json', '.xml': 'application/xml',
  '.wasm': 'application/wasm', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.webp': 'image/webp',
  '.avif': 'image/avif', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2',
  '.ttf': 'font/ttf', '.otf': 'font/otf', '.mp4': 'video/mp4', '.webm': 'video/webm',
};

export async function serve(root, { host = '127.0.0.1', port = 8000 } = {}) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Port must be an integer between 0 and 65535');
  root = await realpath(root);
  const server = createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.writeHead(405, { Allow: 'GET, HEAD' }).end();
      return;
    }
    try {
      let pathname;
      try { pathname = decodeURIComponent(request.url.split(/[?#]/, 1)[0]); }
      catch { response.writeHead(400).end(); return; }
      if (!pathname.startsWith('/') || pathname.includes('\\') || pathname.includes('\0')) {
        response.writeHead(400).end(); return;
      }
      let file = path.resolve(root, `.${pathname}`);
      if (!isWithin(root, file)) { response.writeHead(403).end(); return; }
      file = await realpath(file);
      if (!isWithin(root, file)) { response.writeHead(403).end(); return; }
      if ((await stat(file)).isDirectory()) {
        if (!pathname.endsWith('/')) {
          response.writeHead(301, { Location: `${encodeURI(pathname)}/` }).end(); return;
        }
        file = await realpath(path.join(file, 'index.html'));
      }
      if (!isWithin(root, file)) { response.writeHead(403).end(); return; }
      const info = await stat(file);
      if (!info.isFile()) { response.writeHead(404).end(); return; }
      response.writeHead(200, { 'Content-Type': MIME[path.extname(file).toLowerCase()] ?? 'application/octet-stream', 'Content-Length': info.size });
      if (request.method === 'HEAD') response.end();
      else await pipeline(createReadStream(file), response);
    } catch (error) {
      if (response.headersSent) response.destroy();
      else response.writeHead(['ENOENT', 'ENOTDIR'].includes(error.code) ? 404 : 500).end();
    }
  });
  // Reuse the same server after a failed listen; never kill the process that
  // owns a busy port. Port 0 explicitly asks the OS for an ephemeral port.
  for (;;) {
    try {
      await new Promise((resolve, reject) => {
        const failed = error => { server.off('listening', ready); reject(error); };
        const ready = () => { server.off('error', failed); resolve(); };
        server.once('error', failed);
        server.once('listening', ready);
        server.listen(port, host);
      });
      return server;
    } catch (error) {
      if (error.code !== 'EADDRINUSE' || port === 0 || port === 65535) throw error;
      console.log(`Port ${port} is busy; trying ${port + 1}.`);
      port++;
    }
  }
}

async function dev() {
  const { values } = parseArgs({ options: { port: { type: 'string' }, host: { type: 'string' } } });
  const port = Number(values.port ?? process.env.PORT ?? 8000);
  const host = values.host ?? '127.0.0.1';
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid port');
  const required = ['.generated-site-files.json', 'index.html', 'pkg/rs_wasm_script_bg.wasm'];
  if (required.some(file => !existsSync(path.join(DIST, file)))) await build();
  await completed(styles());
  const server = await serve(DIST, { host, port });
  let watcher;
  const signals = process.platform === 'win32' ? ['SIGINT', 'SIGTERM', 'SIGBREAK'] : ['SIGINT', 'SIGTERM'];
  let cleanupSignals = () => {};
  try {
    await new Promise((resolve, reject) => {
      for (const signal of signals) process.once(signal, stopped);
      function stopped() { resolve(); }
      cleanupSignals = () => { for (const signal of signals) process.off(signal, stopped); };
      server.once('error', reject);
      watcher = styles(true);
      watcher.once('error', reject);
      watcher.once('exit', (code, signal) => reject(new Error(`Sass watcher stopped (${signal ?? code})`)));
      const address = server.address();
      const displayHost = host.includes(':') ? `[${host}]` : host;
      console.log(`\nLocal: http://${displayHost}:${address.port}\nWatching SCSS. Refresh the browser after changes. Ctrl+C to stop.\n`);
    });
  } finally {
    cleanupSignals();
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    if (watcher && watcher.exitCode === null && watcher.signalCode === null) {
      const stopped = new Promise(resolve => { watcher.once('exit', resolve); watcher.once('error', resolve); });
      watcher.kill();
      await stopped;
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try { await dev(); }
  catch (error) { console.error(`Development server failed: ${error.message}`); process.exitCode = 1; }
}
