/* Language selection only. Translation and content fallback happen at build time. */
(() => {
  'use strict';
  const node = document.getElementById('site-language-config');
  if (!node) return;
  const config = JSON.parse(node.textContent);
  const locales = config.locales;
  const storageKey = 'koiro.site.locale';
  const byTag = tag => locales.find(locale => locale.tag === tag);
  const readPreference = () => { try { return localStorage.getItem(storageKey); } catch { return null; } };
  const canonical = tag => {
    try { return typeof Intl.Locale === 'function' ? new Intl.Locale(tag).baseName : tag; }
    catch { return null; }
  };
  const match = tag => {
    const normalized = canonical(tag);
    if (!normalized) return null;
    const exact = byTag(normalized);
    if (exact) return exact;
    let language, script;
    if (typeof Intl.Locale === 'function') {
      const locale = new Intl.Locale(normalized).maximize();
      language = locale.language;
      script = locale.script;
    } else {
      if (!/^en(?:-|$)/i.test(normalized)) return null;
      language = 'en';
    }
    const candidates = locales.filter(locale => {
      if (locale.language !== language) return false;
      if (!script) return true;
      const candidateScript = locale.script || new Intl.Locale(locale.tag).maximize().script;
      return candidateScript === script;
    });
    return candidates.find(locale => locale.matchDefault) || (candidates.length === 1 ? candidates[0] : null);
  };
  document.querySelectorAll('a[data-locale]').forEach(link => {
    link.addEventListener('click', () => {
      if (!byTag(link.dataset.locale)) return;
      try { localStorage.setItem(storageKey, link.dataset.locale); } catch { /* Navigation still works. */ }
      // Only stable content anchors survive a cross-language switch.
      if (/^#(?:active|project)-[a-z][a-z0-9_]*$/.test(location.hash)) {
        const url = new URL(link.href); url.hash = location.hash; link.href = url.href;
      }
    });
  });
  if (config.root) {
    const languages = navigator.languages?.length ? navigator.languages : [navigator.language].filter(Boolean);
    const target = byTag(readPreference()) || languages.map(match).find(Boolean) || byTag(config.default);
    if (target) location.replace(target.url);
    return;
  }
  const menu = document.getElementById('site-language-menu');
  const trigger = document.querySelector('.language-trigger');
  // Leave the ordinary links visible when popover is unsupported.
  if (!menu || !trigger || typeof menu.showPopover !== 'function') return;
  menu.setAttribute('popover', 'auto');
  trigger.setAttribute('popovertarget', menu.id);
  document.documentElement.classList.add('language-enhanced');
  const choices = [...menu.querySelectorAll('a')];
  const position = () => {
    const rect = trigger.getBoundingClientRect();
    const left = Math.max(12, Math.min(rect.right - menu.offsetWidth, innerWidth - menu.offsetWidth - 12));
    const top = rect.top >= menu.offsetHeight + 20 ? rect.top - menu.offsetHeight - 10 :
      Math.min(rect.bottom + 10, innerHeight - menu.offsetHeight - 12);
    menu.style.left = `${left}px`; menu.style.top = `${Math.max(12, top)}px`;
  };
  menu.addEventListener('toggle', () => {
    const open = menu.matches(':popover-open');
    trigger.setAttribute('aria-expanded', String(open));
    if (open) position();
  });
  trigger.addEventListener('keydown', event => {
    if (event.key !== 'ArrowDown') return;
    event.preventDefault(); menu.showPopover(); position();
    (choices.find(choice => choice.hasAttribute('aria-current')) || choices[0])?.focus();
  });
  menu.addEventListener('keydown', event => {
    if (event.key === 'Escape') { trigger.focus(); return; }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const index = choices.indexOf(document.activeElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? choices.length - 1 :
      (index + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length;
    choices[next]?.focus();
  });
  window.addEventListener('resize', () => { if (menu.matches(':popover-open')) position(); });
  window.addEventListener('scroll', () => { if (menu.matches(':popover-open')) position(); }, true);
})();
