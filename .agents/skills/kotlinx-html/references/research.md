# Research sources and maintenance notes

Research date: 2026-10-30. Core source baseline: official `0.12.0` tag, commit `5a8158a23d26f52a67d9b375be7b306beff1b254`. Recheck releases and the target project's versions when maintaining this skill; "latest" is specific to the research date.

## Findings

kotlinx.html is a type-safe Kotlin HTML builder suitable for server rendering, static HTML, HTTP fragments, and imperative browser DOM construction. It is licensed under Apache-2.0. Its core abstraction, `TagConsumer<R>`, receives events for opening tags, attribute changes, text, closing tags, and other content; tag-call results depend on the consumer. It is not an HTML parser, a complete HTML validator, a CSS framework, or a reactive UI framework.

Typed tags, attributes, and receivers provide compile-time constraints, while shared components reuse Kotlin functions and control flow. The tradeoff is coupling templates to Kotlin compilation and correctly handling DSL receivers, platform variants, HTML contexts, and stream/DOM lifecycles. Products requiring state management, a virtual DOM, or hydration need the corresponding application architecture; this library does not supply those capabilities automatically.

## Verified primary sources

| Source | Findings and limitations |
| --- | --- |
| [Release 0.12.0](https://github.com/Kotlin/kotlinx.html/releases/tag/0.12.0) | Latest stable release query; published 2025-01-15; template tag, attribute, and platform additions |
| [README at 0.12.0](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/README.md) | Writer/Appendable/DOM scope, platforms, and basic entry points; badge versions are not compatibility contracts |
| [Build at 0.12.0](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/build.gradle.kts) | Kotlin 2.0.21, platform targets, and publication configuration |
| [api.kt](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/api.kt) | `TagConsumer`, text/entities, `unsafe`, `visit` / `visitAndFinalize` |
| [stream.kt](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/stream.kt) | `createHTML`, `appendHTML`, formatting defaults, escaping, no automatic doctype, and text-output event limitations |
| [delayed-consumer.kt](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/delayed-consumer.kt) | Attribute changes allowed before content, rejected after the opening tag is committed |
| [DOM JS](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/jsMain/kotlin/dom-js.kt) | create/append, actual nodes and event assignment, and unsafe innerHTML behavior |
| [Generated content groups](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/generated/gen-tag-groups.kt) | Receiver capabilities and the `template` tag extension |
| [Micro-templating wiki](https://github.com/Kotlin/kotlinx.html/wiki/Micro-templating-and-DSL-customizing) | Extension functions and custom HTMLTag design; old examples still need compilation against the current project version |
| [Kotlin type-safe HTML DSL](https://kotlinlang.org/docs/typesafe-html-dsl.html) | Browser DSL basics; the page still shows 0.8.0, so it is not a version baseline for new projects |
| [Ktor HTML DSL](https://ktor.io/docs/server-html-dsl.html) | Framework dependency, respondHtml, templates, and respondHtmlPartial in current documentation; verify against the project's Ktor version |

## Design choices for agents

- Keep target selection, error-prone constraints, and troubleshooting in the entry point; load platform examples as needed.
- Do not duplicate the complete tag catalog. Resolve uncertain tags or attributes through resolved sources, the official tag's `generated/` directory, or IDE type information.
- Do not introduce automatic upgrade scripts or require Ktor, a CSS framework, HTMX, or browser tools for every task.
- Do not describe ordinary HTML escaping as universal XSS protection. Handle URL, raw HTML, and script/style contexts separately.

## Validation record

The skill passed skill-creator's `quick_validate.py`. Checks of `agents/openai.yaml` metadata and all local reference links also passed.

On 2026-10-30, Kotlin examples extracted from patterns.md were compiled and executed with Kotlin compiler 2.2.20, JDK 17, and kotlinx-html-jvm 0.12.0. Passing behavior checks covered complete pages and text escaping, Appendable output, component slots, multiple root fragments, boolean form attributes and attribute escaping, custom tags, correct attribute ordering, and the expected exception when modifying attributes after content. This verifies only that toolchain combination, not compatibility with every Kotlin version.

Browser create/node/event APIs were checked against official 0.12.0 sources; Ktor examples were checked against official documentation. JS/Wasm compilation, real browser events, Ktor HTTP integration, and Native/WasmWasi behavior were not tested. Agents using this skill should perform the relevant validation in the target project.
