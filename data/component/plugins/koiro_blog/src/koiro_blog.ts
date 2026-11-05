import { MarkdownRenderChild, MarkdownView, Plugin } from 'obsidian';
import { componentEditor, type Mount } from './editor';
import { fences, isComponent } from './fences';
import { mountComponent } from './renderer';
import { vaultResources } from './resources';
import { registerToml } from './toml';

class PreviewChild extends MarkdownRenderChild {
  constructor(container: HTMLElement, private start: () => () => void) { super(container); }
  onload() { this.register(this.start()); }
}

export default class KoiroBlog extends Plugin {
  private disposers = new Set<() => void>();

  private track(dispose: () => void): () => void {
    const tracked = () => {
      if (!this.disposers.delete(tracked)) return;
      dispose();
    };
    this.disposers.add(tracked);
    return tracked;
  }

  private mount: Mount = (element, source, path) =>
    this.track(mountComponent(element, source, vaultResources(this.app, path)));

  onload() {
    registerToml(this);
    this.registerEditorExtension(componentEditor(this.mount));
    this.registerMarkdownPostProcessor((element, context) => {
      // Never replace the html language processor: ordinary HTML retains Obsidian highlighting.
      for (const code of element.querySelectorAll<HTMLElement>('pre > code')) {
        const pre = code.parentElement!;
        const section = context.getSectionInfo(pre);
        if (!section) continue; // Do not guess from matching text elsewhere in the note.
        const candidates = fences(section.text).filter(token =>
          token.map![0] >= section.lineStart && token.map![1] - 1 <= section.lineEnd &&
          token.content.replace(/\n$/, '') === (code.textContent ?? '').replace(/\n$/, ''));
        // Ambiguous duplicate blocks fail closed rather than executing unmarked code.
        if (!candidates.length || !candidates.every(token => isComponent(token.info))) continue;
        const container = pre.ownerDocument.createElement('div');
        container.className = 'koiro-blog-component';
        pre.replaceWith(container);
        context.addChild(new PreviewChild(container, () => {
          const stop = this.mount(container, candidates[0].content, context.sourcePath);
          return this.track(() => { stop(); container.replaceWith(pre); });
        }));
      }
    });
    this.addCommand({
      id: 'refresh-component-previews',
      name: 'Refresh reading-view components',
      callback: () => this.app.workspace.getActiveViewOfType(MarkdownView)?.previewMode.rerender(true),
    });
  }

  onunload() {
    for (const dispose of [...this.disposers]) dispose();
  }
}
