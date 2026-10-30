# Browser DOM and events

This complete example targets **Kotlin/JS in a browser**. Call it after the body is ready. Returned DOM nodes and DSL tag receivers are different objects.

```kotlin
import kotlinx.browser.document
import kotlinx.html.ButtonType
import kotlinx.html.dom.create
import kotlinx.html.js.*

fun mountCounter() {
    val host = document.getElementById("app") ?: error("Missing #app")
    var count = 0
    val output = document.create.span { +"0" }
    val increment = document.create.button {
        type = ButtonType.button
        onClickFunction = {
            count += 1
            output.textContent = count.toString()
        }
        +"Increment"
    }
    host.appendChild(output)
    host.appendChild(increment)
}
```

`kotlinx.html.js.*` contains browser-specific consumer tag builders that return concrete DOM types, along with event properties. Import the shared tag DSL from `kotlinx.html.*` and platform consumer extensions as needed; use explicit imports if overload resolution becomes ambiguous.

- `document.create.*` creates nodes without mounting them; mount them with `appendChild`.
- Import `kotlinx.html.dom.append` to create and mount nodes through `host.append { ... }`, or use `host.append.div { ... }` on an HTMLElement.
- Update `textContent`, `classList`, or DOM properties afterward, or explicitly replace nodes. There is no implicit reactive rerendering.
- Repeating the same mount function appends more nodes. If remounting is required, define the replacement boundary, remove external listeners, or reuse existing nodes.
- `onClick` is an inline event string; `onClickFunction` is a browser callback. They are not interchangeable. Callbacks require a DOM consumer and do not work with `createHTML` text rendering, even in a JS project.
- In 0.12.0, JS DOM `unsafe` writes through `innerHTML += ...`, reparsing existing children and potentially losing node identity and event listeners. Prefer DOM APIs and safe text nodes for existing interactive subtrees.

## WasmJS

Version 0.12.0 provides a WasmJS DOM implementation with corresponding tag/event extensions in the `kotlinx.html.js` package. Do not infer that Wasm is unsupported from the package name, and do not copy JS-specific `asDynamic()` or `dynamic` usage into Wasm code. Consult the resolved version's WasmJS source set and DOM types, then compile the `wasmJs` target. This browser approach does not directly apply to Node/WasmWasi environments.

## Validation

Use the project's browser build/test workflow to verify that the container exists, the initial text is 0, a click changes it to 1, and repeated mounting matches the product contract. Source inspection or successful compilation alone is not a passed browser interaction test.

Sources: [JS DOM implementation](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/jsMain/kotlin/dom-js.kt), [WasmJS DOM implementation](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/wasmJsMain/kotlin/dom-js.kt), [WasmJS event extensions](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/src/wasmJsMain/kotlin/generated/gen-event-attrs-wasm-js.kt), [events wiki](https://github.com/Kotlin/kotlinx.html/wiki/Events).
