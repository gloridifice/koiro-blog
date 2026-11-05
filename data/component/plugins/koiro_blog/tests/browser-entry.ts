import { EditorState } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { componentEditor } from '../src/editor';
import { mountComponent } from '../src/renderer';
import { editorInfoField, editorLivePreviewField } from './editor-fields';

export function editor(container: HTMLElement, text: string) {
  return new EditorView({
    parent: container,
    state: EditorState.create({
      doc: text,
      extensions: [editorInfoField, editorLivePreviewField,
        componentEditor((element, source) => mountComponent(element, source, async url => url))],
    }),
  });
}
export { mountComponent };
