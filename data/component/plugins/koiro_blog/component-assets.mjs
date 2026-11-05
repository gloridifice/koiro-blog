import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

/** Both the shipped bundle and browser tests use the real Kotlin registry export. */
export function componentDefines() {
  const root = fileURLToPath(new URL('../../../../', import.meta.url));
  const windows = process.platform === 'win32';
  const launcher = path.join(root, windows ? 'gradlew.bat' : 'gradlew');
  const result = windows
    ? spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', `""${launcher}" --no-daemon --quiet exportComponentAssets"`],
      { cwd: root, stdio: 'inherit', windowsVerbatimArguments: true })
    : spawnSync(launcher, ['--no-daemon', '--quiet', 'exportComponentAssets'], { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Component registry export failed (${result.status ?? result.signal})`);
  const registry = JSON.parse(readFileSync(path.join(root, 'build/generated/component-assets.json'), 'utf8'));
  const urls = (files, mime) => files.map(file => `data:${mime};base64,${readFileSync(path.join(root, 'assets/component', file)).toString('base64')}`);
  return {
    COMPONENT_FRAME_RUNTIME: JSON.stringify(readFileSync(path.join(root, 'assets/assets/js/article-component-frame.js'), 'utf8')),
    COMPONENT_PRESET_STYLES: JSON.stringify(urls(registry.css, 'text/css')),
    COMPONENT_PRESET_SCRIPTS: JSON.stringify(urls(registry.js, 'text/javascript')),
  };
}
