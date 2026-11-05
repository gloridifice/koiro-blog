package htmlgen.component

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ComponentAssetManifest(val css: List<String>, val js: List<String>)

/** The sole allowlist for blog frames and the bundled Obsidian presets. Never scan the directory. */
object ComponentAssets {
    val registered = ComponentAssetManifest(
        css = listOf(
            "component.css",
            "image-compare.css",
        ),
        js = listOf(
            "image-compare.js",
        ),
    )

    fun inject(document: Document) {
        // Presets precede author styles, preserving normal cascade overrides and registration order.
        for (file in registered.css.asReversed()) {
            document.head().prependElement("link").attr("rel", "stylesheet").attr("href", "/component/$file")
        }
        for (file in registered.js) {
            document.head().appendElement("script").attr("src", "/component/$file").attr("defer", "")
        }
    }

    fun export(destination: Path) {
        val root = Path.of("assets/component").toRealPath()
        for ((extension, names) in listOf("css" to registered.css, "js" to registered.js)) {
            require(names.distinct().size == names.size) { "Duplicate component $extension registration" }
            for (name in names) {
                require(Regex("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.$extension").matches(name)) {
                    "Invalid component asset registration: $name"
                }
                val file = root.resolve(name)
                require(Files.isRegularFile(file) && file.toRealPath().startsWith(root)) {
                    "Missing or escaping registered component asset: $name"
                }
            }
        }
        Files.createDirectories(destination.toAbsolutePath().parent)
        Files.writeString(destination, Json.encodeToString(registered))
    }
}

/** Build-time bridge: the plugin consumes this exported registry rather than duplicating it. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Expected component manifest output path" }
    ComponentAssets.export(Path.of(args.single()))
}
