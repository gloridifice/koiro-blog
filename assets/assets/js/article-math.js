// Markdown math nodes already exclude their delimiters and quote markers.
// Render only those nodes as dollar math, leaving escaped dollars and code alone.
for (const element of document.querySelectorAll('.inline_math, .equation')) {
  katex.render(element.textContent, element, {
    displayMode: element.classList.contains('equation'),
    throwOnError: false,
  });
}

// Preserve the existing KaTeX delimiters in trusted raw HTML (\(...\), \[...\], etc.).
renderMathInElement(document.body, { throwOnError: false });
