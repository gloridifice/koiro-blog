---
name: kotlinx-html
description: Write, refactor, and troubleshoot type-safe HTML with Kotlin kotlinx.html, including server-rendered pages and fragments, Ktor HTML DSL, reusable components, and Kotlin/JS or WasmJS browser DOM. Use when the user mentions kotlinx-html or kotlinx.html, or requests HTML generation in a project using this library. Not for general HTML/CSS work or unrelated Compose/React DSLs.
---

# kotlinx.html

Implement the requested HTML using the project's existing kotlinx.html version. The library routes tag calls through a `TagConsumer` to strings, Appendable, or DOM. It does not provide reactive state, a virtual DOM, routing, or automatic hydration.

## Identify the output target

Inspect the project's Gradle/Maven configuration, version catalogs, source sets, and nearby pages. Preserve its Kotlin, Ktor, and library versions; do not upgrade the project merely to match this skill's examples.

| Requirement | Entry point and considerations |
| --- | --- |
| HTML strings, static pages, server-rendered fragments | `kotlinx.html.stream.createHTML`; a root tag call returns a string |
| Writer / Appendable | `kotlinx.html.stream.appendHTML`; the caller manages resource lifetimes |
| Ktor HTTP pages | `io.ktor.server.html.respondHtml`; the lambda already has an `HTML` receiver |
| Creating or inserting browser nodes | `kotlinx.html.dom.append` / `create`; requires a browser DOM environment |
| Shared KMP components | Share tag extension functions; keep DOM, HTTP, and file operations in their platform layers |

Read references relevant to the task rather than loading all of them:

- Dependencies, versions, and platform issues: [setup.md](references/setup.md).
- Strings, fragments, forms, components, and custom tags: [patterns.md](references/patterns.md).
- Browser nodes, events, and WasmJS: [browser.md](references/browser.md).
- Ktor routes, templates, and HTMX fragments: [ktor.md](references/ktor.md).
- API verification, skill maintenance, and research sources: [research.md](references/research.md).

## Implementation constraints

1. Import `kotlinx.html.*`, then add the target-specific stream, dom, or Ktor imports. Diagnose dependency/import errors separately from receiver mismatches. Do not hide DSL type errors with casts.
2. Use `+value` or `text(value)` for ordinary text. String output performs HTML escaping; do not pre-encode ordinary text as entities or concatenate user content through `unsafe`. Use explicit entity APIs such as `+Entities.nbsp`.
3. Set all attributes on each tag before emitting text, comments, or child tags. Although `createHTML` / `appendHTML` use a delayed consumer, the first content emission commits the opening tag; later attribute changes can throw. Attributes inside nested lambdas belong to the current receiver.
4. Prefer typed attributes such as `type = ButtonType.button` and `disabled = true`. When no dedicated property exists, use `attributes["data-id"]`, `attributes["aria-label"]`, or `attributes["hx-get"]`. The `classes` property is a collection: `classes = ...` replaces it, while `classes += ...` adds entries. Use named arguments to avoid passing CSS classes to parameters such as href.
5. HTML escaping is not URL validation or JavaScript/CSS encoding. Validate untrusted URL schemes according to application rules. Do not insert dynamic data directly into inline scripts, event strings, or styles. When raw HTML is necessary, accept only explicitly trusted or appropriately sanitized HTML and document its origin at the call boundary.
6. Implement components as extension functions on suitable receivers: `FlowContent` for block content, `PhrasingContent` or the appropriate interface for permitted inline tags, and specific parent types such as `UL` / `TBODY` for list items/table rows. Expose slots through lambdas such as `DIV.() -> Unit`. Emit tags directly inside components rather than rendering to strings and reinserting them through `unsafe`.
7. Use a DOM consumer and `kotlinx.html.js.onClickFunction` for browser events. Server-rendered strings cannot serialize Kotlin callbacks; emit the required attributes and bind events with client-side scripts. After construction, update actual DOM nodes rather than retaining DSL tag objects as if they were nodes.
8. Builder lambdas are synchronous. Complete suspending data loading first, then pass data to rendering functions; do not introduce `runBlocking` to build HTML. Use a separate consumer for each independent render to avoid accidental output accumulation or shared state across requests.

## Validation and troubleshooting

Compile the actual target/source set and verify the output affected by the change. For string output, check text, attributes, structure, and escaping. For browser interactions, check insertion location and real events. For HTTP pages, check status, Content-Type, and full-page/fragment boundaries. Compare whole strings only when whitespace is part of the contract; otherwise assert DOM structure. Do not introduce a new test framework for a small change.

| Symptom | Check first |
| --- | --- |
| `Unresolved reference` / receiver mismatch | Dependency variant, source set, extension imports, and permitted parent tags |
| `already passed to the downstream` / `already written to the stream` | Whether attributes on the current or outer tag were changed after content |
| Generated HTML has no interaction | Whether DOM callbacks were used with text output; whether client scripts loaded |
| `&amp;lt;` or markup appears as text | Pre-escaping or treating HTML as ordinary text; establish the content's trust boundary first |
| Node references/listeners stop working after an update | Whether `unsafe` / `innerHTML` rebuilt existing DOM |
| An extra html or body wrapper appears | Confusion between `respondHtml`, `createHTML().div`, and partial content |
| Incompatible Kotlin metadata | Resolved dependencies and compiler versions; do not default to flags that skip compatibility checks |

When delivering changes, state what changed, what was actually validated, and which platforms remain unverified. When a version-specific API is uncertain, prefer the project's resolved sources or the corresponding official release/tag. Do not treat dependency versions from old wiki pages as current configuration.
