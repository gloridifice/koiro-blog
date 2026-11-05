import { Menu, Notice, Plugin, TextFileView, TFolder, normalizePath, type WorkspaceLeaf } from 'obsidian';

const TOML_VIEW = 'koiro-blog-toml';

/** TextFileView owns loading, debounced persistence, external updates and file moves. */
class TomlView extends TextFileView {
  private readonly input: HTMLTextAreaElement;

  constructor(leaf: WorkspaceLeaf) {
    super(leaf);
    this.contentEl.classList.add('koiro-blog-toml');
    this.input = this.contentEl.createEl('textarea', {
      cls: 'koiro-blog-toml-input',
      attr: { 'aria-label': 'TOML editor', spellcheck: 'false' },
    });
    this.registerDomEvent(this.input, 'input', () => this.requestSave());
  }

  getViewType() { return TOML_VIEW; }
  getDisplayText() { return this.file?.name ?? 'TOML'; }
  getIcon() { return 'file-code'; }
  getViewData() { return this.input.value; }
  setViewData(data: string, clear: boolean) {
    if (clear) this.clear();
    if (this.input.value !== data) this.input.value = data;
  }
  clear() { this.input.value = ''; }
}

export function registerToml(plugin: Plugin) {
  const { app } = plugin;
  plugin.registerView(TOML_VIEW, leaf => new TomlView(leaf));
  // Registered extensions are visible in File Explorer without enabling all file types.
  // Rename and move remain native Vault/FileManager operations, not plugin overrides.
  plugin.registerExtensions(['toml'], TOML_VIEW);
  plugin.register(() => app.workspace.detachLeavesOfType(TOML_VIEW));

  const create = async (folder: TFolder) => {
    try {
      let path: string;
      let index = 0;
      do {
        const name = `Untitled${index ? ` ${index}` : ''}.toml`;
        path = normalizePath(`${folder.path}/${name}`);
        index++;
      } while (app.vault.getAbstractFileByPath(path));
      // Vault.create refuses existing paths; even concurrent requests cannot overwrite a file.
      const file = await app.vault.create(path, '');
      await app.workspace.getLeaf(false).openFile(file);
    } catch (error) {
      new Notice(`Could not create TOML file: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  plugin.registerEvent(app.workspace.on('file-menu', (menu, file, source) => {
    if (source !== 'file-explorer' && source !== 'file-explorer-context-menu') return;
    const folder = file instanceof TFolder ? file : file.parent;
    if (!folder) return;
    menu.addItem(item => item.setTitle('New TOML file').setIcon('file-plus')
      .onClick(() => create(folder)));
  }));
  // Recent Obsidian versions have no root tree item and emit no file-menu on empty space.
  const registerRootMenu = (doc: Document) => plugin.registerDomEvent(doc, 'contextmenu', event => {
    const target = event.target as Element | null;
    if (event.defaultPrevented || !target?.closest?.('.workspace-leaf-content[data-type="file-explorer"] .nav-files-container') ||
        target.closest('.nav-file-title, .nav-folder-title')) return;
    event.preventDefault();
    event.stopPropagation();
    const menu = new Menu();
    menu.addItem(item => item.setTitle('New TOML file').setIcon('file-plus')
      .onClick(() => create(app.vault.getRoot())));
    menu.showAtMouseEvent(event);
  }, true);
  registerRootMenu(document);
  plugin.registerEvent(app.workspace.on('window-open', (_workspaceWindow, win) => registerRootMenu(win.document)));

  plugin.addCommand({
    id: 'new-toml-file',
    name: 'New TOML file',
    callback: () => create(app.workspace.getActiveFile()?.parent ?? app.vault.getRoot()),
  });
}
