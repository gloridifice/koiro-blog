# koiro_blog

[English](../README.md) | [简体中文](zh-CN.md) | [日本語](ja-JP.md) | Français

Prévisualisez les composants HTML du blog et créez, modifiez, renommez ou déplacez des fichiers TOML dans Obsidian.

## Démarrage rapide

### Installation

Compilez dans `data/.obsidian/plugins/koiro_blog/` : le coffre `data/` charge directement l’extension depuis ce répertoire, sans copie. Pour un autre coffre, copiez `main.js`, `manifest.json` et `styles.css` dans `<vault>/.obsidian/plugins/koiro_blog/`. Ces trois noms sont imposés par Obsidian. Désactivez et supprimez l’ancienne installation de Component Renderer, puis rechargez Obsidian et activez **koiro_blog**. Utilisez uniquement des coffres dont vous connaissez et acceptez le code des composants.

### Mise à jour

Désactivez l’extension, recompilez sur place (ou remplacez ces trois fichiers dans un autre coffre), puis réactivez-la.

## Fonctionnalités

- Fichiers TOML visibles dans l’explorateur, avec renommage et déplacement natifs et éditeur de texte à sauvegarde automatique.
- **New TOML file** dans le menu contextuel des fichiers et dossiers ; crée et ouvre un fichier vide avec un nom unique, sans écraser de fichier existant.
- Mode lecture et Live Preview, sans modifier le mode source.
- Reconnaissance exacte de `html` et `component`, sensible à la casse, dans les deux ordres.
- HTML, CSS et JavaScript intégrés, avec DOM et variables isolés.
- Script de hauteur partagé avec le blog, limité à 10000px.
- Images, médias, scripts classiques et feuilles de style locaux intégrés en URL data.
- Préréglages hors ligne enregistrés explicitement en Kotlin, dont la comparaison d’images en HTML seul ; aucun chargement automatique du répertoire.

````markdown
```html component
<button onclick="this.textContent = 'Clicked!'">Click me</button>
```
````

Dans Live Preview, placez le curseur dans le bloc pour le modifier, puis sortez-en pour le relancer. Si le mode lecture conserve une ancienne version après une modification des marqueurs, lancez **koiro_blog: Refresh reading-view components**. Quand la position du code source est inconnue ou ambiguë, le code original reste affiché.

## Compilation

Node.js 24+, JDK 21 et le wrapper Gradle du dépôt sont requis. Depuis ce répertoire :

```sh
npm ci
npm run build
```

La compilation exécute `exportComponentAssets` pour exporter le registre Kotlin, puis intègre uniquement les CSS/JS enregistrés dans `assets/component/` et le script de hauteur partagé. Recompilez après toute modification des préréglages ou du registre. L’extension installée ne nécessite ni dépôt, ni serveur, ni blog généré, ni dépendances npm à la racine, ni environnement Java.

## Développement

```sh
npx playwright install chromium
npm test
```

Vous pouvez aussi définir `BROWSER_CHANNEL=chrome` ou `msedge` pour utiliser un navigateur installé. Ces tests vérifient la reconnaissance des blocs et le rendu réel Chromium/CodeMirror. Pour les tests d’intégration TOML, définissez `OBSIDIAN_EXECUTABLE` avec le chemin du client de bureau, puis lancez `npm run test:obsidian`. Ils utilisent un coffre temporaire isolé, sans toucher à votre coffre habituel.

Voir les [règles et limites](../../../../../doco/specs/obsidian-koiro-blog.md). Le bac à sable ne bloque ni le réseau ni la consommation de ressources. Les URL dans le CSS/JS ne sont pas résolues automatiquement et le thème du blog n’est pas hérité.
