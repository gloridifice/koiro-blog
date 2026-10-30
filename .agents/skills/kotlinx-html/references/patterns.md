# Strings, fragments, and components

## Full pages and Appendable

```kotlin
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.html.stream.appendHTML

fun renderPage(pageTitle: String, names: List<String>): String =
    "<!DOCTYPE html>\n" + createHTML(prettyPrint = false).html {
        lang = "en"
        head {
            meta { charset = "utf-8" }
            title { +pageTitle }
        }
        body {
            h1 { +pageTitle }
            ul { names.forEach { name -> li { +name } } }
        }
    }

fun writeNotice(out: Appendable, message: String) {
    out.appendHTML(prettyPrint = false).p { +message }
}
```

`createHTML().html { ... }` does not insert a doctype automatically. Add one at the rendering boundary when a full static page needs it; do not duplicate one already supplied by a framework. `appendHTML` does not close a Writer. `prettyPrint` defaults to true, while `xhtmlCompatible` defaults to false. The latter primarily changes void-element serialization; it does not validate the entire document as XHTML. Do not rely on indentation to preserve spaces between inline text; emit `+" "` explicitly.

## Reusable components and multiple root fragments

```kotlin
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.html.stream.appendHTML

fun FlowContent.noticeCard(heading: String, content: DIV.() -> Unit) {
    section(classes = "notice") {
        attributes["aria-label"] = heading
        h2 { +heading }
        div(classes = "notice-body", block = content)
    }
}

fun renderCard(): String = createHTML(prettyPrint = false).div {
    noticeCard("Sync results") { p { +"Completed" } }
}

fun renderSiblings(labels: List<String>): String = buildString {
    val sink = appendHTML(prettyPrint = false)
    labels.forEach { label -> sink.span { +label } }
}
```

For a single-root fragment, use `createHTML().div { ... }` directly without an extra `html` / `body` wrapper. For multiple roots, emit them sequentially through one Appendable consumer. Do not repeatedly collect returned strings from the same `createHTML` consumer: each includes previously accumulated output.

Receivers determine which tags are available. A `UL` component emits `li`, a `TBODY` component emits `tr`, and a `TR` component emits `td`. `FlowContent` is not a universal parent that permits every tag. Successful compilation does not establish complete HTML semantics, accessibility, or browser parsing behavior.

## Forms and attributes

```kotlin
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun searchForm(query: String, busy: Boolean): String =
    createHTML(prettyPrint = false).form(action = "/search", method = FormMethod.get) {
        label {
            htmlFor = "query"
            +"Keywords"
        }
        input(type = InputType.search, name = "q") {
            id = "query"
            value = query
            disabled = busy
            attributes["aria-label"] = "Search keywords"
        }
        button(type = ButtonType.submit) { +"Search" }
    }
```

HTML boolean attributes are controlled by presence: do not write `attributes["disabled"] = "false"`. ARIA string attributes follow different rules; `attributes["aria-expanded"] = "false"` is valid. Use `textarea { +value }` for initial textarea text and `value` for an input. These examples generate markup only; the application handles form processing.

## Attribute timing

```kotlin
// Correct: attributes precede child content.
div {
    id = "result"
    classes += "ready"
    p { +"Done" }
}
```

Moving `id = "result"` after `p` fails with a streaming consumer. Raw content through `unsafe`, ordinary text, and comments also commit the opening tag. If an attribute depends on content, compute its value before rendering.

## Custom attributes and tags

Prefer `attributes` for additional attributes rather than creating a whole custom tag: `attributes["hx-target"] = "#result"`. When a new tag such as a Web Component is needed, use `HTMLTag` and `visit` to preserve consumer events and escaping:

```kotlin
import kotlinx.html.*

class STATUS_BADGE(consumer: TagConsumer<*>) : HTMLTag(
    "status-badge", consumer, emptyMap(),
    inlineTag = true, emptyTag = false
), HtmlInlineTag

fun FlowContent.statusBadge(label: String) {
    STATUS_BADGE(consumer).visit {
        attributes["data-state"] = "ready"
        +label
    }
}
```

Custom elements are generally not void elements; preserve their closing tags. This helper is designed for nested calls. If a consumer root entry point is needed, implement it using the project's version of `visitAndFinalize`. Do not concatenate unsupported SVG tags as ordinary HTML; check the required namespace and target consumer capabilities first.

## Raw content and scripts

Let the DSL escape ordinary dynamic text. It does not validate URL schemes; application rules for `href` and `src` still apply. Do not treat HTML encoding as JS/CSS encoding, or assume JSON serialization alone makes data safe to embed inside a script: the HTML parser still recognizes closing tags.

Prefer external resources: `script(src = "/assets/app.js") {}`. When an inline script is necessary and consists of fixed developer-controlled code, use `unsafe { +"fixed trusted script" }` inside the tag. Route rich text through the project's existing HTML sanitization boundary with explicit allowed tags and attributes. Do not sanitize HTML with a simple regex.

Sources: [stream consumer](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/stream.kt), [delayed consumer](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/delayed-consumer.kt), [tag and unsafe APIs](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/api.kt), [official component guidance](https://github.com/Kotlin/kotlinx.html/wiki/Micro-templating-and-DSL-customizing).
