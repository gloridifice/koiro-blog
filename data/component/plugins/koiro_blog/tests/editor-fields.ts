// Only the two documented Obsidian host fields; the editor/decorations below use real CM6.
import { StateField } from '@codemirror/state';
export const editorLivePreviewField = StateField.define({ create: () => true, update: value => value });
export const editorInfoField = StateField.define({ create: () => ({ file: { path: 'notes/demo.md' } }), update: value => value });
