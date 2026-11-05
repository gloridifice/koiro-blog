declare const COMPONENT_FRAME_RUNTIME: string;
declare const COMPONENT_PRESET_STYLES: readonly string[];
declare const COMPONENT_PRESET_SCRIPTS: readonly string[];
export type ResourceResolver = (url: string) => Promise<string>;

/** One iframe owns its listener and lifetime; no messages expose host/vault APIs. */
export function mountComponent(container: HTMLElement, source: string, resolveResource: ResourceResolver): () => void {
  const document = container.ownerDocument;
  const host = document.defaultView!;
  const frame = document.createElement('iframe');
  frame.className = 'article-component';
  frame.title = 'HTML component';
  frame.setAttribute('sandbox', 'allow-scripts');
  frame.referrerPolicy = 'no-referrer';
  frame.style.cssText = 'display:block;width:100%;height:150px;border:0';
  let disposed = false;

  const onMessage = (event: MessageEvent) => {
    if (event.origin !== 'null' || event.source !== frame.contentWindow ||
        event.data?.type !== 'koiro:component-height') return;
    const height = event.data.height;
    if (typeof height !== 'number' || !Number.isFinite(height) || height <= 0) return;
    frame.style.height = `${Math.min(10000, Math.ceil(height))}px`;
  };
  const measure = () => frame.contentWindow?.postMessage({ type: 'koiro:component-measure' }, '*');
  host.addEventListener('message', onMessage);
  frame.addEventListener('load', measure);

  void (async () => {
    // Parse in an inert document, never in Obsidian's live DOM.
    const parsed = new host.DOMParser().parseFromString(source, 'text/html');
    for (const element of parsed.querySelectorAll('img[src], source[src], video[src], audio[src], video[poster], script[src], link[rel="stylesheet"][href]')) {
      for (const attr of ['src', 'poster', 'href']) {
        const url = element.getAttribute(attr);
        if (url) element.setAttribute(attr, await resolveResource(url));
      }
    }
    // Relative URLs must never accidentally inherit Obsidian's app:// origin.
    parsed.querySelectorAll('base').forEach(element => element.remove());
    const base = parsed.createElement('base');
    base.href = 'about:blank';
    parsed.head.prepend(base);
    const style = parsed.createElement('style');
    style.textContent = 'html{height:auto}body{margin:0;display:flow-root}';
    parsed.head.prepend(style);
    const viewport = parsed.createElement('meta');
    viewport.name = 'viewport';
    viewport.content = 'width=device-width, initial-scale=1';
    parsed.head.append(viewport);
    // These data URLs come only from the Kotlin allowlist exported at build time.
    // Prepend in reverse so preset order is preserved and author styles still win.
    for (const href of [...COMPONENT_PRESET_STYLES].reverse()) {
      const link = parsed.createElement('link');
      link.rel = 'stylesheet';
      link.href = href;
      parsed.head.prepend(link);
    }
    for (const src of COMPONENT_PRESET_SCRIPTS) {
      const script = parsed.createElement('script');
      script.src = src;
      parsed.body.append(script);
    }
    const runtime = parsed.createElement('script');
    // The blog runtime is bundled at build time; installed plugins need no repo files.
    runtime.textContent = COMPONENT_FRAME_RUNTIME;
    parsed.body.append(runtime);
    if (disposed) return;
    frame.title = parsed.title || 'HTML component';
    frame.srcdoc = '<!doctype html>\n' + parsed.documentElement.outerHTML;
    container.replaceChildren(frame);
  })().catch(error => {
    if (disposed) return;
    const message = document.createElement('pre');
    message.className = 'koiro-blog-component-error';
    message.textContent = `koiro_blog: ${error instanceof Error ? error.message : String(error)}`;
    container.replaceChildren(message);
  });

  return () => {
    disposed = true;
    host.removeEventListener('message', onMessage);
    frame.removeEventListener('load', measure);
    frame.remove();
  };
}
