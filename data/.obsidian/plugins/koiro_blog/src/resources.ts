import { App, TFile } from 'obsidian';
import type { ResourceResolver } from './renderer';

const mimeTypes: Record<string, string> = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
  svg: 'image/svg+xml', avif: 'image/avif', ico: 'image/x-icon',
  css: 'text/css', js: 'text/javascript', mjs: 'text/javascript',
  mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg', mp4: 'video/mp4', webm: 'video/webm',
};

export function vaultResources(app: App, sourcePath: string): ResourceResolver {
  const cache = new Map<string, Promise<string>>();
  return (url) => {
    if (/^(https?:|data:|blob:)/i.test(url) || url.startsWith('#')) return Promise.resolve(url);
    if (url.startsWith('//')) return Promise.resolve(`https:${url}`);
    if (/^[a-z][a-z0-9+.-]*:/i.test(url)) return Promise.reject(new Error(`Unsupported resource URL: ${url}`));
    if (!cache.has(url)) cache.set(url, (async () => {
      const [pathname] = url.split(/[?#]/, 1);
      const decoded = decodeURIComponent(pathname);
      const parts = decoded.startsWith('/') ? [] : sourcePath.split('/').slice(0, -1);
      for (const part of decoded.replace(/\\/g, '/').split('/')) {
        if (!part || part === '.') continue;
        if (part === '..') {
          if (!parts.length) throw new Error(`Resource escapes vault: ${url}`);
          parts.pop();
        } else parts.push(part);
      }
      const file = app.vault.getAbstractFileByPath(parts.join('/'));
      if (!(file instanceof TFile)) throw new Error(`Resource not found: ${url}`);
      const bytes = await app.vault.readBinary(file);
      const type = mimeTypes[file.extension.toLowerCase()] ?? 'application/octet-stream';
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(new Blob([bytes], { type }));
      });
      // Fragments can select an SVG view; query strings have no meaning for embedded bytes.
      const fragment = url.indexOf('#');
      return data + (fragment < 0 ? '' : url.slice(fragment));
    })());
    return cache.get(url)!;
  };
}
