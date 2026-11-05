(() => {
  for (const figure of document.querySelectorAll('.kc-compare')) {
    const stage = figure.querySelector('.kc-compare-stage');
    const a = figure.querySelector('.kc-compare-image-a');
    const b = figure.querySelector('.kc-compare-image-b');
    const range = figure.querySelector('.kc-compare-range');
    const error = figure.querySelector('.kc-compare-error');
    if (!stage || !a || !b || !range || !error) continue;

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
})();
