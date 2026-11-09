(() => {
  const rail = document.querySelector('.article-toc');
  const content = document.querySelector('.post > .contents');
  if (!rail || !content) return;

  const desktop = matchMedia('(min-width: 1200px) and (hover: hover) and (pointer: fine)');
  const columns = [...rail.querySelectorAll('.article-toc__column')];
  const root = columns[0];
  const links = [...rail.querySelectorAll('a')];
  const byHeading = new Map(links.map(link => [link.hash.slice(1), link]));
  const children = new Map(columns.slice(1).map(column => [column.dataset.parent, column]));
  const parentOf = link => byHeading.get(link.closest('ul').dataset.parent);
  const depthOf = link => Number(link.closest('ul').dataset.depth);
  let path = [];
  let current;
  let closeTimer;
  let frame;
  let keyboard = false;
  let restoringFocus = false;

  // Deeper headings still belong to their nearest visible ancestor in the rail.
  let owner;
  const headings = [...content.querySelectorAll('.page_content :is(h1,h2,h3,h4,h5,h6)[id]')]
    .map(heading => {
      owner = byHeading.get(heading.id) || owner;
      return { heading, link: owner };
    }).filter(entry => entry.link);

  function ancestors(link) {
    const result = [];
    for (; link; link = parentOf(link)) result.unshift(link);
    return result;
  }

  function layout() {
    if (!desktop.matches) return;
    const left = Math.max(20, Math.min(40, content.getBoundingClientRect().left * 0.12));
    // Preview columns may extend beyond the gutter; splitting only the gutter
    // between three levels left room for barely a few characters per title.
    const width = Math.min(280, (innerWidth - left - 24) / 3);
    rail.style.setProperty('--toc-column-width', `${width}px`);
    const boundedTop = (center, height) => Math.max(24, Math.min(innerHeight - height - 24, center - height / 2));
    root.style.left = `${left}px`;
    root.style.top = `${boundedTop(innerHeight * 0.43, root.offsetHeight)}px`;
    for (const column of columns.slice(1)) {
      if (column.hidden) continue;
      const parentLink = byHeading.get(column.dataset.parent);
      const parent = parentLink.getBoundingClientRect();
      const parentColumn = parentLink.closest('ul');
      column.style.left = `${parseFloat(parentColumn.style.left) + parentColumn.offsetWidth}px`;
      column.style.top = `${boundedTop(parent.top + parent.height / 2, column.offsetHeight)}px`;
    }
  }

  function render() {
    rail.toggleAttribute('data-open', path.length > 0);
    for (const link of links) {
      const selected = path.includes(link);
      link.toggleAttribute('data-preview', selected);
      if (link.hasAttribute('aria-controls')) link.setAttribute('aria-expanded', String(selected));
      link.style.removeProperty('--toc-tick-width');
    }
    for (const column of columns.slice(1)) {
      column.hidden = path[Number(column.dataset.depth) - 1]?.hash.slice(1) !== column.dataset.parent;
    }
    for (const selected of path) {
      const siblings = [...selected.closest('ul').querySelectorAll('a')];
      const index = siblings.indexOf(selected);
      siblings.forEach((link, i) => link.style.setProperty('--toc-tick-width', `${16 + Math.max(0, 4 - Math.abs(i - index)) * 4}px`));
    }
    layout();
  }

  function preview(link) {
    if (!desktop.matches) return;
    clearTimeout(closeTimer);
    if (path[depthOf(link)] === link) return;
    path = ancestors(link);
    render();
  }

  function close() {
    clearTimeout(closeTimer);
    // Do not leave focus inside a column that is about to become hidden.
    const focused = document.activeElement;
    if (rail.contains(focused) && depthOf(focused) > 0) {
      restoringFocus = true;
      ancestors(focused)[0].focus({ preventScroll: true });
      restoringFocus = false;
    }
    path = [];
    render();
  }

  function updateCurrent() {
    if (!desktop.matches || !headings.length) return;
    const threshold = Math.min(160, innerHeight * 0.25);
    let selected = headings[0].link;
    for (const { heading, link } of headings) {
      if (heading.getBoundingClientRect().top <= threshold) selected = link;
      else break;
    }
    if (scrollY > 0 && scrollY + innerHeight >= document.documentElement.scrollHeight - 2) {
      selected = headings.at(-1).link;
    }
    if (selected === current) return;
    current = selected;
    const activePath = ancestors(current);
    for (const link of links) {
      link.toggleAttribute('data-current', activePath.includes(link));
      if (link === current) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    }
    if (!path.length) {
      const row = activePath[0].getBoundingClientRect();
      const bounds = root.getBoundingClientRect();
      if (row.top < bounds.top) root.scrollTop -= bounds.top - row.top;
      else if (row.bottom > bounds.bottom) root.scrollTop += row.bottom - bounds.bottom;
    }
  }

  function schedule() {
    if (frame || !desktop.matches) return;
    frame = requestAnimationFrame(() => {
      frame = undefined;
      layout();
      updateCurrent();
    });
  }

  rail.addEventListener('pointerover', event => {
    if (event.pointerType === 'touch') return;
    const link = event.target.closest('a');
    if (link) preview(link);
  });
  rail.addEventListener('pointerenter', () => clearTimeout(closeTimer));
  rail.addEventListener('pointerleave', () => {
    if (!keyboard || !rail.contains(document.activeElement)) closeTimer = setTimeout(close, 180);
  });
  document.addEventListener('pointerdown', event => {
    keyboard = false;
    if (!rail.contains(event.target)) close();
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Tab') keyboard = true;
  });
  rail.addEventListener('focusin', event => {
    if (!restoringFocus && event.target.matches('a')) preview(event.target);
  });
  rail.addEventListener('focusout', event => {
    if (!rail.contains(event.relatedTarget)) close();
  });
  rail.addEventListener('keydown', event => {
    const link = event.target.closest('a');
    if (!link) return;
    const siblings = [...link.closest('ul').querySelectorAll('a')];
    const index = siblings.indexOf(link);
    let next;
    switch (event.key) {
      case 'ArrowRight':
        preview(link);
        next = children.get(link.hash.slice(1))?.querySelector('a');
        break;
      case 'ArrowLeft': next = parentOf(link); break;
      case 'ArrowUp': next = siblings[Math.max(0, index - 1)]; break;
      case 'ArrowDown': next = siblings[Math.min(siblings.length - 1, index + 1)]; break;
      case 'Home': next = siblings[0]; break;
      case 'End': next = siblings.at(-1); break;
      case 'Escape': event.preventDefault(); close(); return;
      default: return;
    }
    event.preventDefault();
    keyboard = true;
    if (next) {
      next.focus({ preventScroll: true });
      next.scrollIntoView({ block: 'nearest', behavior: 'instant' });
      preview(next);
    }
  });
  // Re-anchor children after scrolling a long column; hide branches whose parent
  // has left that column's viewport rather than leaving an orphaned preview.
  for (const column of columns) column.addEventListener('scroll', () => {
    const depth = Number(column.dataset.depth);
    const selected = path[depth];
    if (selected) {
      const row = selected.getBoundingClientRect();
      const bounds = column.getBoundingClientRect();
      if (row.bottom <= bounds.top || row.top >= bounds.bottom) {
        path = path.slice(0, depth);
        render();
      }
    }
    layout();
  }, { passive: true });
  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule);
  window.addEventListener('hashchange', schedule);
  new ResizeObserver(schedule).observe(content);
  desktop.addEventListener('change', () => {
    close();
    current = undefined;
    schedule();
  });
  rail.setAttribute('data-ready', '');
  schedule();
})();
