package htmlgen

import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Reads only named icons from the locked npm package; no icon library is shipped to the browser. */
object LucideIcons {
    private val directory = Path("node_modules/lucide-static/icons")
    private val namePattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val cache = mutableMapOf<String, SvgIcon>()

    fun load(name: String): SvgIcon {
        require(namePattern.matches(name)) {
            "Invalid Lucide icon name '$name': use a kebab-case name, not a path or SVG."
        }
        return cache.getOrPut(name) {
            check(directory.isDirectory()) {
                "Lucide icons are not installed. Run npm ci from the project root."
            }
            val file = directory.resolve("$name.svg")
            require(file.isRegularFile()) { "Unknown Lucide icon '$name'. See https://lucide.dev/icons/" }
            // Package SVGs are trusted build inputs. Preserve their license comments and currentColor strokes.
            SvgIcon(file.readText())
        }
    }
}
