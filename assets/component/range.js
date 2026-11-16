(() => {
  function initialize(input) {
    if (input.dataset.kcRangeInitialized === 'true') return;
    input.dataset.kcRangeInitialized = 'true';
    const slider = document.createElement('span');
    slider.className = 'kc-slider';
    const track = document.createElement('span');
    track.className = 'kc-slider-track';
    track.setAttribute('aria-hidden', 'true');
    const fill = document.createElement('span');
    fill.className = 'kc-slider-fill';
    track.append(fill);
    input.before(slider);
    slider.append(track, input);

    // The original input owns labels, value sanitization, steps and keyboard behavior.
    let pointer = null, startValue = '';
    const number = (text, fallback) => text.trim() !== '' && Number.isFinite(Number(text)) ? Number(text) : fallback;
    function bounds() {
      return [number(input.min, 0), number(input.max, 100)];
    }
    function sync() {
      const [min, max] = bounds();
      const progress = max > min ? Math.max(0, Math.min(1, (Number(input.value) - min) / (max - min))) : 0;
      slider.style.setProperty('--kc-progress', `${progress * 100}%`);
    }
    function move(event) {
      if (input.matches(':disabled')) return;
      const rect = slider.getBoundingClientRect();
      if (!rect.width) return;
      const [min, max] = bounds();
      const fraction = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
      const previous = input.value;
      input.value = String(min + fraction * Math.max(0, max - min));
      sync();
      if (input.value !== previous) input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    slider.addEventListener('pointerdown', event => {
      if (input.matches(':disabled') || !event.isPrimary || event.button !== 0 || pointer !== null) return;
      event.preventDefault();
      pointer = event.pointerId;
      startValue = input.value;
      slider.dataset.pointerFocus = '';
      input.focus({ preventScroll: true });
      slider.setPointerCapture(pointer);
      slider.dataset.dragging = '';
      move(event);
    });
    slider.addEventListener('pointermove', event => {
      if (event.pointerId === pointer) move(event);
    });
    function stop(event) {
      if (event.pointerId !== pointer) return;
      if (event.type === 'pointerup') move(event);
      pointer = null;
      delete slider.dataset.dragging;
      if (slider.hasPointerCapture(event.pointerId)) slider.releasePointerCapture(event.pointerId);
      if (input.value !== startValue) input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) slider.addEventListener(type, stop);
    input.addEventListener('keydown', () => { delete slider.dataset.pointerFocus; });
    input.addEventListener('blur', () => { delete slider.dataset.pointerFocus; });
    for (const type of ['input', 'change']) input.addEventListener(type, sync);
    // Attribute changes cover dynamic limits; dispatch input after assigning .value in author scripts.
    new MutationObserver(sync).observe(input, { attributes: true, attributeFilter: ['min', 'max', 'step', 'value', 'disabled'] });
    input.form?.addEventListener('reset', () => queueMicrotask(sync));
    sync();
  }
  const initializeAll = () => document.querySelectorAll('input.kc-range[type="range"]').forEach(initialize);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeAll, { once: true });
  else initializeAll();
})();
