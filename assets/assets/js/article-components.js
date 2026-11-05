(() => {
  const frames = [...document.querySelectorAll('iframe.article-component')];
  if (!frames.length) return;

  window.addEventListener('message', (event) => {
    if (event.origin !== 'null' || event.data?.type !== 'koiro:component-height') return;
    const frame = frames.find((candidate) => candidate.contentWindow === event.source);
    const height = event.data.height;
    if (!frame || typeof height !== 'number' || !Number.isFinite(height) || height <= 0) return;
    // Oversized content remains scrollable instead of taking over the article.
    frame.style.height = `${Math.min(10000, Math.ceil(height))}px`;
  });

  for (const frame of frames) {
    const measure = () => frame.contentWindow?.postMessage({ type: 'koiro:component-measure' }, '*');
    frame.addEventListener('load', measure);
    measure(); // Also handles frames that loaded before this deferred script.
  }
})();
