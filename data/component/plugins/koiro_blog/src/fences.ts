import MarkdownIt from 'markdown-it';

const markdown = new MarkdownIt();

export function fences(text: string) {
  // Obsidian frontmatter is not Markdown. Blank it without shifting line numbers.
  const normalized = text.replace(/\r\n?/g, '\n');
  const lines = normalized.split('\n');
  if (lines[0] === '---') {
    const end = lines.findIndex((line, i) => i > 0 && /^(---|\.\.\.)\s*$/.test(line));
    if (end !== -1) lines.fill('', 0, end + 1);
  }
  const tokens = markdown.parse(lines.join('\n'), {}).filter(token => token.type === 'fence' && token.map);
  // %% comments are an Obsidian extension. Never execute a fence hidden by one.
  // Inside a real fence, %% is literal source (e.g. a JS string), not a comment.
  let comment = false;
  const hidden = new Set<number>();
  const byStart = new Map(tokens.map(token => [token.map![0], token]));
  for (let line = 0; line < lines.length; line++) {
    const token = byStart.get(line);
    if (token && !comment) { line = token.map![1] - 1; continue; }
    if (token) hidden.add(line);
    for (const _ of lines[line].matchAll(/%%/g)) comment = !comment;
  }
  return tokens.filter(token => !hidden.has(token.map![0]));
}

export function isComponent(info: string): boolean {
  const markers = info.trim().split(/\s+/);
  return markers.includes('html') && markers.includes('component');
}
