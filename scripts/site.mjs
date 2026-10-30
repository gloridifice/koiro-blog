#!/usr/bin/env node
import { spawn } from 'node:child_process';
import {
  closeSync, copyFileSync, cpSync, existsSync, lstatSync, mkdirSync, openSync,
  readFileSync, readSync, readdirSync, realpathSync, rmSync,
} from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parse as parseHtml } from 'parse5';
import { SaxesParser } from 'saxes';
import { parse as parseToml } from 'smol-toml';

export const ROOT = fileURLToPath(new URL('../', import.meta.url));
export const DIST = path.join(ROOT, 'dist');
const LFS_HEADER = Buffer.from('version https://git-lfs.github.com/spec/v1');
const WASM_HEADER = Buffer.from([0, 97, 115, 109, 1, 0, 0, 0]);
const METADATA = new Set(['.git', '.gitattributes', '.github', '.idea']);

export function isWithin(root, target) {
  const relative = path.relative(root, target);
  return relative === '' || (!path.isAbsolute(relative) && relative !== '..' && !relative.startsWith(`..${path.sep}`));
}

function* walk(root) {
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const file = path.join(root, entry.name);
    yield file;
    if (entry.isDirectory() && !entry.isSymbolicLink()) yield* walk(file);
  }
}

function header(file, length) {
  const buffer = Buffer.alloc(length);
  const fd = openSync(file, 'r');
  try {
    return buffer.subarray(0, readSync(fd, buffer, 0, length, 0));
  } finally {
    closeSync(fd);
  }
}

export function checkTree(root) {
  const stat = lstatSync(root);
  if (stat.isSymbolicLink() || !stat.isDirectory()) throw new Error(`Expected a real directory: ${root}`);
  for (const file of walk(root)) {
    const stat = lstatSync(file);
    if (stat.isSymbolicLink()) throw new Error(`Cannot publish symlink: ${file}`);
    if (METADATA.has(path.basename(file))) throw new Error(`Cannot publish repository metadata: ${file}`);
    if (stat.isFile() && header(file, LFS_HEADER.length).equals(LFS_HEADER)) {
      throw new Error(`Git LFS pointer found: ${file}. Run git lfs pull first.`);
    }
  }
}

export function prepareOutput(assets, output) {
  // Validate before touching the previous build, including dangling symlinks.
  checkTree(assets);
  let outputStat;
  try { outputStat = lstatSync(output); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (outputStat?.isSymbolicLink()) throw new Error(`Refusing to replace output symlink: ${output}`);
  const source = realpathSync(assets);
  const target = outputStat ? realpathSync(output) : path.resolve(realpathSync(path.dirname(output)), path.basename(output));
  if (isWithin(source, target) || isWithin(target, source)) throw new Error('Assets and output directories must not overlap');
  rmSync(output, { recursive: true, force: true });
  cpSync(assets, output, { recursive: true });
}

function* htmlReferences(node) {
  const attrs = Object.fromEntries((node.attrs ?? []).map(({ name, value }) => [name, value]));
  const attribute = ['img', 'script', 'source', 'audio', 'video', 'iframe'].includes(node.tagName) ? 'src' :
    ['link', 'a'].includes(node.tagName) ? 'href' : null;
  if (attribute && attrs[attribute]) yield attrs[attribute];
  if (node.tagName === 'video' && attrs.poster) yield attrs.poster;
  if (node.tagName === 'meta' && attrs['http-equiv']?.toLowerCase() === 'refresh') {
    const match = /url\s*=\s*(.+)$/i.exec(attrs.content ?? '');
    if (match) yield match[1];
  }
  for (const child of node.childNodes ?? []) yield* htmlReferences(child);
  if (node.content) yield* htmlReferences(node.content);
}

function localPath(url) {
  if (/^[a-z][a-z0-9+.-]*:/i.test(url) || url.startsWith('//')) return null;
  const pathname = url.split(/[?#]/, 1)[0];
  return pathname ? decodeURIComponent(pathname) : null;
}

export function checkLocalAssets(root) {
  root = realpathSync(root);
  const failures = [];
  for (const file of walk(root)) {
    const extension = path.extname(file);
    if (!['.html', '.css'].includes(extension) || !lstatSync(file).isFile()) continue;
    const text = readFileSync(file, 'utf8');
    const urls = extension === '.html' ? htmlReferences(parseHtml(text)) :
      [...text.matchAll(/url\(\s*['"]?([^'"\)]+?)['"]?\s*\)/gi)].map(match => match[1]);
    for (const url of urls) {
      const decoded = localPath(url);
      if (!decoded) continue;
      const target = decoded.startsWith('/') ? path.resolve(root, `.${decoded}`) : path.resolve(path.dirname(file), decoded);
      if (!isWithin(root, target) || !existsSync(target) || !lstatSync(target).isFile() || !isWithin(root, realpathSync(target))) {
        failures.push(`${path.relative(root, file)} -> ${url}`);
      }
    }
  }
  if (failures.length) throw new Error(`Missing or escaping local assets:\n${failures.slice(0, 20).join('\n')}\n(${failures.length} failures total)`);
}

export function checkGeneratedFiles(root) {
  const entries = JSON.parse(readFileSync(path.join(root, '.generated-site-files.json'), 'utf8'));
  if (!Array.isArray(entries) || entries.some(entry => typeof entry !== 'string')) throw new Error('Invalid generated-file manifest');
  for (const relative of entries) {
    const parts = relative.split('/');
    if (parts.some(part => !part || part === '..' || part === '.') || relative.includes('\\') || /^[a-z]:/i.test(relative)) {
      throw new Error(`Unsafe generated path: ${relative}`);
    }
    let current = root;
    for (const part of parts) {
      if (!lstatSync(current).isDirectory() || !readdirSync(current).includes(part)) {
        throw new Error(`Generated output missing or case mismatch: ${relative}`);
      }
      current = path.join(current, part);
      if (lstatSync(current).isSymbolicLink()) throw new Error(`Output symlink: ${relative}`);
    }
    if (!lstatSync(current).isFile()) throw new Error(`Generated output is not a file: ${relative}`);
  }
}

function readFeed(file) {
  const feed = { language: '', item: [] };
  const elements = [];
  let hasChannel = false;
  const parser = new SaxesParser({ xmlns: true });
  parser.on('error', error => { throw new Error(`Invalid RSS: ${file}: ${error.message}`); });
  parser.on('opentag', tag => {
    elements.push(tag.name);
    const location = elements.join('/');
    if (location === 'rss/channel') hasChannel = true;
    if (location === 'rss/channel/item') feed.item.push({ link: '' });
  });
  const text = value => {
    const location = elements.join('/');
    if (location === 'rss/channel/language') feed.language += value;
    if (location === 'rss/channel/item/link') feed.item.at(-1).link += value;
  };
  parser.on('text', text);
  parser.on('cdata', text);
  parser.on('closetag', () => elements.pop());
  parser.write(readFileSync(file, 'utf8')).close();
  if (!hasChannel) throw new Error(`Invalid RSS channel: ${file}`);
  feed.language = feed.language.trim();
  for (const item of feed.item) item.link = item.link.trim();
  return feed;
}

export function validateSite(output) {
  checkTree(output);
  const required = [
    'index.html', 'home.html', 'blogs.html', 'portfolio.html', 'about.html', 'rss.xml',
    'CNAME', '.nojekyll', '.generated-site-files.json', 'assets/css/root.css',
    'pkg/rs_wasm_script.js', 'pkg/rs_wasm_script_bg.wasm', 'assets/licenses/lucide.txt',
  ];
  function requireFile(relative) {
    const file = path.join(output, relative);
    if (!existsSync(file) || !lstatSync(file).isFile() || (relative !== '.nojekyll' && lstatSync(file).size === 0)) {
      throw new Error(`Missing or empty build output: ${file}`);
    }
  }
  required.forEach(requireFile);
  readFeed(path.join(output, 'rss.xml'));
  const config = parseToml(readFileSync(path.join(ROOT, 'i18n/locales.toml'), 'utf8'));
  for (const locale of Object.keys(config.locales)) {
    for (const file of ['home.html', 'blogs.html', 'portfolio.html', 'about.html', 'rss.xml']) requireFile(`${locale}/${file}`);
    const feed = readFeed(path.join(output, locale, 'rss.xml'));
    if (feed.language !== locale) throw new Error(`Incorrect RSS language: ${locale}`);
    for (const item of feed.item ?? []) {
      const pathname = new URL(item.link, 'https://local.invalid').pathname;
      if (!pathname.startsWith(`/${locale}/`)) throw new Error(`Wrong-language RSS link: ${item.link}`);
      requireFile(decodeURIComponent(pathname).slice(1));
    }
  }
  if (!header(path.join(output, 'pkg/rs_wasm_script_bg.wasm'), 8).equals(WASM_HEADER)) throw new Error('Invalid WebAssembly output');
  checkGeneratedFiles(output);
  checkLocalAssets(output);
  console.log(`Validated site: ${output}`);
}

export function start(command, args = []) {
  console.log('+', command, ...args);
  const options = { cwd: ROOT, stdio: 'inherit' };
  if (process.platform === 'win32' && /\.bat$/i.test(command)) {
    // cmd is needed only for the checked-in Gradle batch launcher. Arguments
    // here are fixed build tasks, not user-provided shell expressions.
    return spawn(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', `""${command}" ${args.join(' ')}"`],
      { ...options, windowsVerbatimArguments: true });
  }
  return spawn(command, args, options);
}

export function completed(child) {
  return new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) resolve();
      else reject(new Error(`Command failed (${signal ?? code}): ${child.spawnargs.join(' ')}`));
    });
  });
}

export function styles(watch = false) {
  return start(process.execPath, [path.join(ROOT, 'node_modules/sass/sass.js'),
    'src/main/stylesheet:dist/assets/css', ...(watch ? ['--watch', '--style=expanded'] : ['--style=compressed', '--no-source-map'])]);
}

async function wasm() {
  await completed(start('wasm-pack', ['build', path.join(ROOT, 'src/main/rs-wasm-script'), '--target', 'web',
    '--release', '--out-dir', path.join(DIST, 'pkg'), '--locked']));
  rmSync(path.join(DIST, 'pkg/.gitignore'), { force: true });
}

export async function build() {
  checkTree(path.join(ROOT, 'assets'));
  checkTree(path.join(ROOT, 'data'));
  const license = path.join(ROOT, 'node_modules/lucide-static/LICENSE');
  if (!existsSync(license)) throw new Error('Dependencies are missing. Run npm ci first.');
  prepareOutput(path.join(ROOT, 'assets'), DIST);
  mkdirSync(path.join(DIST, 'assets/licenses'), { recursive: true });
  copyFileSync(license, path.join(DIST, 'assets/licenses/lucide.txt'));
  await completed(start(path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'), ['--no-daemon', 'test', 'run']));
  await completed(styles());
  await wasm();
  validateSite(DIST);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    switch (process.argv[2] ?? 'build') {
      case 'build': await build(); break;
      case 'check': validateSite(DIST); break;
      case 'styles': await completed(styles()); break;
      case 'watch-styles': await completed(styles(true)); break;
      case 'wasm': await wasm(); break;
      default: throw new Error('Usage: node scripts/site.mjs [build|check|styles|watch-styles|wasm]');
    }
  } catch (error) {
    console.error(`Build failed: ${error.message}`);
    process.exitCode = 1;
  }
}
