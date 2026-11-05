(() => {
  let scheduled = false;
  let previousHeight = 0;
  function measure() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      const body = document.body;
      const style = getComputedStyle(body);
      const height = Math.max(1, Math.ceil(Math.max(body.offsetHeight, body.scrollHeight) +
        (parseFloat(style.marginTop) || 0) + (parseFloat(style.marginBottom) || 0)));
      if (height === previousHeight) return;
      previousHeight = height;
      parent.postMessage({ type: 'koiro:component-height', height }, '*');
    });
  }

  window.addEventListener('message', (event) => {
    if (event.source !== parent || event.data?.type !== 'koiro:component-measure') return;
    previousHeight = 0;
    measure();
  });
  new ResizeObserver(measure).observe(document.body);
  window.addEventListener('load', measure, true);
  document.fonts.ready.then(measure);
  measure();
})();
