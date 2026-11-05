(() => {
  function initialize(figure) {
    // Only the declarative API opts in. Legacy self-contained examples keep their own scripts.
    if (figure.dataset.kcCompareInitialized === 'true') return;
    const a = figure.querySelector(':scope > img[data-side="a"]');
    const b = figure.querySelector(':scope > img[data-side="b"]');
    const caption = figure.querySelector(':scope > figcaption');
    if (!a || !b || !caption) return;
    figure.dataset.kcCompareInitialized = 'true';
    figure.dataset.state = 'loading';
    if (!caption.id) {
      let captionNumber = 0;
      do { captionNumber++; } while (document.getElementById(`kc-compare-caption-${captionNumber}`));
      caption.id = `kc-compare-caption-${captionNumber}`;
    }
    const stage = document.createElement('div');
    stage.className = 'kc-compare-stage';
    a.classList.add('kc-compare-image-a');
    b.classList.add('kc-compare-image-b');
    const range = document.createElement('input');
    range.className = 'kc-compare-range';
    range.type = 'range'; range.min = '0'; range.max = '100'; range.step = '0.1'; range.value = '50';
    range.disabled = true;
    range.setAttribute('aria-label', 'A、B 图片分界位置');
    range.setAttribute('aria-describedby', caption.id);
    const divider = document.createElement('span');
    divider.className = 'kc-compare-divider';
    divider.setAttribute('aria-hidden', 'true');
    const handle = document.createElement('span');
    handle.className = 'kc-compare-handle';
    handle.setAttribute('aria-hidden', 'true');
    // Static trusted icon; author-provided text is never interpolated into HTML.
    handle.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="m8 7-5 5 5 5m8-10 5 5-5 5"/></svg>';
    const error = document.createElement('p');
    error.className = 'kc-compare-error';
    error.setAttribute('role', 'status');
    error.hidden = true;
    stage.append(b, a, range, divider, handle);
    figure.prepend(stage);
    figure.append(error);

    let pointerId = null;
    const failed = new Set();
    function update() {
      const value = Math.max(0, Math.min(100, Number(range.value)));
      figure.style.setProperty('--compare-position', value + '%');
      range.setAttribute('aria-valuetext', `A 图 ${value.toFixed(1)}%，B 图 ${(100 - value).toFixed(1)}%`);
    }
    function refresh() {
      if (a.naturalWidth > 0 && a.naturalHeight > 0) {
        figure.style.setProperty('--compare-ratio', `${a.naturalWidth} / ${a.naturalHeight}`);
      }
      const ready = failed.size === 0 && [a, b].every(image => image.complete && image.naturalWidth > 0);
      range.disabled = !ready;
      figure.dataset.state = failed.size ? 'error' : ready ? 'ready' : 'loading';
      error.hidden = failed.size === 0;
      if (failed.size) {
        error.textContent = `${[...failed].map(image => image === a ? 'A' : 'B').join('、')} 图加载失败，请检查图片路径。`;
      }
    }
    for (const image of [a, b]) {
      image.draggable = false;
      image.addEventListener('load', () => { failed.delete(image); refresh(); });
      image.addEventListener('error', () => { failed.add(image); refresh(); });
      if (image.complete && image.naturalWidth === 0) failed.add(image);
    }
    function move(clientX) {
      const bounds = stage.getBoundingClientRect();
      if (bounds.width === 0) return;
      range.value = String(Math.max(0, Math.min(100, (clientX - bounds.left) / bounds.width * 100)));
      update();
    }
    stage.addEventListener('pointerdown', event => {
      if (range.disabled || !event.isPrimary || event.button !== 0 || pointerId !== null) return;
      pointerId = event.pointerId;
      range.focus({ preventScroll: true });
      stage.setPointerCapture(pointerId);
      move(event.clientX);
    });
    stage.addEventListener('pointermove', event => {
      if (event.pointerId === pointerId && !range.disabled) move(event.clientX);
    });
    const endDrag = event => {
      if (event.pointerId !== pointerId) return;
      pointerId = null;
      if (stage.hasPointerCapture(event.pointerId)) stage.releasePointerCapture(event.pointerId);
    };
    for (const name of ['pointerup', 'pointercancel', 'lostpointercapture']) stage.addEventListener(name, endDrag);
    range.addEventListener('input', update);
    update();
    refresh();
  }
  const initializeAll = () => document.querySelectorAll('figure.kc-compare').forEach(initialize);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeAll, { once: true });
  else initializeAll();
})();
