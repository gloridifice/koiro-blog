/* Preserve old Chinese article anchors; never override with a locale preference. */
document.addEventListener('DOMContentLoaded', () => {
  const target = document.getElementById('legacy-target');
  if (target && location.hash) location.replace(target.href + location.hash);
});
