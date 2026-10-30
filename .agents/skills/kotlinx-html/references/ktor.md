# Ktor integration

Ktor's HTTP response and template APIs belong to `io.ktor.server.html`. Do not treat them as kotlinx.html core APIs.

Add `implementation("io.ktor:ktor-server-html-builder:$ktorVersion")` to the existing project, using its current Ktor version for `ktorVersion`. HTML DSL does not require an additional plugin `install` call.

## Pages and partial fragments

```kotlin
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun Application.pages() {
    routing {
        get("/") {
            // Complete suspending queries before the synchronous respondHtml builder.
            val pageTitle = "Task list"
            call.respondHtml {
                head { title { +pageTitle } }
                body { h1 { +pageTitle } }
            }
        }
        get("/fragment") {
            val markup = createHTML(prettyPrint = false).div {
                id = "result"
                attributes["aria-live"] = "polite"
                +"Updated"
            }
            call.respondText(markup, ContentType.Text.Html)
        }
    }
}
```

The `respondHtml` lambda is already `HTML.() -> Unit`; do not nest another `html {}`. The fragment example uses the general `respondText` API and explicitly specifies `text/html`.

Current Ktor documentation also provides `respondHtmlPartial`. Use it only when the project's version exposes that API; do not copy APIs from current documentation unconditionally into older Ktor projects. Set HTMX `hx-*` attributes through `attributes`; the application must still load the HTMX client and select the correct swap container. kotlinx.html does not process requests or swap DOM content itself.

## Ktor templates

Prefer ordinary tag extension functions for small amounts of repeated UI. Use `Template<T>`, `Placeholder<T>`, and `TemplatePlaceholder<T>` when working within an existing template system or when layout slots are needed.

```kotlin
import io.ktor.server.html.*
import kotlinx.html.*

class Layout : Template<HTML> {
    var pageTitle: String = "Page"
    val content = Placeholder<FlowContent>()

    override fun HTML.apply() {
        head { title { +pageTitle } }
        body { main { insert(content) } }
    }
}
```

In a route, use `call.respondHtmlTemplate(Layout()) { pageTitle = "Overview"; content { p { +"Welcome" } } }`. Create template objects with mutable slots per request rather than sharing them across requests.

Distinguish three meanings of "template": ordinary Kotlin extension-function components, Ktor `Template` layouts, and the HTML `<template>` element. "HTML templates" in the kotlinx.html 0.12.0 release refers to tag support; it does not imply a built-in Ktor template engine.

Verify HTTP status, the `text/html` Content-Type, body structure, and the absence of extra page wrappers in fragments. Dependency and compilation checks do not replace verification of actual HTTP responses.

Sources: [official Ktor HTML DSL documentation](https://ktor.io/docs/server-html-dsl.html), [respondHtml API](https://api.ktor.io/ktor-server-html-builder/io.ktor.server.html/respond-html.html), [0.12.0 TEMPLATE tag](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/commonMain/kotlin/generated/gen-tags-t.kt).
