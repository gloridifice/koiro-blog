import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/** Real on-disk inputs for generator boundary tests; no production-only test hooks. */
fun withSiteFixture(block: (data: Path, output: Path, locales: Path) -> Unit) {
    val root = Files.createTempDirectory("localized-site-")
    try {
        val data = root.resolve("data").createDirectories()
        for (kind in listOf("blogs", "devlogs", "actives", "portfolio")) data.resolve(kind).createDirectories()
        val locales = root.resolve("i18n").createDirectories()
        for (file in Path.of("i18n").listDirectoryEntries("*.toml")) Files.copy(file, locales.resolve(file.name))
        block(data, root.resolve("output"), locales)
    } finally { root.toFile().deleteRecursively() }
}

fun writeBundle(data: Path, relative: String, metadata: String, body: String, header: String = "title = \"Example\"") : Path {
    val dir = data.resolve(relative).createDirectories()
    dir.resolve("meta.toml").writeText(metadata.trimIndent() + "\n")
    return dir.resolve("zh-CN.md").apply { writeText("```toml\n$header\n```\n$body") }
}
