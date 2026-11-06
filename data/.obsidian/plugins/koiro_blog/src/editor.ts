import { StateField, type EditorState } from '@codemirror/state';
import { Decoration, EditorView, WidgetType, type DecorationSet } from '@codemirror/view';
import { editorInfoField, editorLivePreviewField } from 'obsidian';
import { fences, isComponent } from './fences';

export type Mount = (element: HTMLElement, source: string, sourcePath: string) => () => void;

const mounted = new WeakMap<HTMLElement, () => void>();

class ComponentWidget extends WidgetType {
  constructor(private source: string, private path: string, private mount: Mount) { super(); }
  eq(other: ComponentWidget) { return other.source === this.source && other.path === this.path; }
  toDOM(view: EditorView): HTMLElement {
    const container = view.dom.ownerDocument.createElement('div');
    container.className = 'koiro-blog-component';
    mounted.set(container, this.mount(container, this.source, this.path));
    return container;
  }
  destroy(element: HTMLElement) {
    mounted.get(element)?.();
    mounted.delete(element);
  }
  ignoreEvent() { return true; }
}

export function componentEditor(mount: Mount) {
  function decorations(state: EditorState): DecorationSet {
    if (!state.field(editorLivePreviewField, false)) return Decoration.none;
    const path = state.field(editorInfoField, false)?.file?.path;
    if (!path) return Decoration.none;
    const ranges = [];
    for (const token of fences(state.doc.toString())) {
      if (!isComponent(token.info)) continue;
      const [start, end] = token.map!;
      const from = state.doc.line(start + 1).from;
      const to = state.doc.line(Math.min(end, state.doc.lines)).to;
      // Entering the fence reveals editable source; leaving it restores the component.
      if (state.selection.ranges.some(range => range.from <= to && range.to >= from)) continue;
      ranges.push(Decoration.replace({ block: true, widget: new ComponentWidget(token.content, path, mount) }).range(from, to));
    }
    return Decoration.set(ranges, true);
  }
  return StateField.define<DecorationSet>({
    create: decorations,
    update(value, transaction) {
      if (transaction.docChanged || transaction.selection ||
          transaction.state.field(editorLivePreviewField, false) !== transaction.startState.field(editorLivePreviewField, false) ||
          transaction.state.field(editorInfoField, false)?.file?.path !== transaction.startState.field(editorInfoField, false)?.file?.path) {
        return decorations(transaction.state);
      }
      return value;
    },
    provide: field => EditorView.decorations.from(field),
  });
}
