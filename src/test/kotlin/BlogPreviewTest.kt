import htmlgen.component.blogPostPreview
import htmlgen.component.largeBlogPostPreview
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.w3c.dom.Element
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlogPreviewTest {
    @Test
    fun `both preview sizes render the configured icon with emoji and default fallbacks`() {
        data class Case(val icon: String?, val emoji: String?, val expectedIcon: String?)
        val cases = listOf(
            Case("keyboard", null, "keyboard"),
            Case("palette", "🎨", "palette"),
            Case(null, "📝<&>", null),
            Case(null, null, "file-text"),
            Case(null, " ", "file-text")
        )
        for (case in cases) {
            val post = BlogRecord(
                BlogHeader("Title <&>", "Description <&>", true, "Dev", "2025-01-01",
                    emoji = case.emoji, icon = case.icon),
                ServerPath("pages/blogs/example.html")
            )
            val html = createHTML().div {
                largeBlogPostPreview(post)
                blogPostPreview(post)
            }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(html.byteInputStream())
            val cards = document.getElementsByTagName("a")
            assertEquals(2, cards.length)
            for (index in 0 until cards.length) {
                val card = cards.item(index) as Element
                assertEquals("/pages/blogs/example.html", card.getAttribute("href"))
                assertEquals("Title <&>", card.getElementsByTagName("h2").item(0).textContent)
                val containers = card.getElementsByTagName("div")
                val icon = (0 until containers.length).map { containers.item(it) as Element }
                    .single { it.getAttribute("class") == "blog_icon" }
                assertEquals("true", icon.getAttribute("aria-hidden"))
                val svgs = icon.getElementsByTagName("svg")
                if (case.expectedIcon == null) {
                    assertEquals(0, svgs.length)
                    assertEquals(case.emoji, icon.textContent.trim())
                } else {
                    assertEquals(1, svgs.length)
                    val svg = svgs.item(0) as Element
                    assertTrue(svg.getAttribute("class").split(" ").contains("lucide-${case.expectedIcon}"))
                    assertEquals("currentColor", svg.getAttribute("stroke"))
                    assertEquals("24", svg.getAttribute("width"))
                    assertEquals("24", svg.getAttribute("height"))
                    assertTrue(svg.getElementsByTagName("*").length > 0)
                }
            }
        }
    }

    @Test
    fun `invalid icon configuration fails with source context even for unpublished blogs`() {
        val root = Files.createTempDirectory("blog-icons-")
        try {
            val data = root.resolve("data")
            for (folder in listOf("blogs", "devlogs", "portfolio", "actives")) {
                data.resolve(folder).createDirectories()
            }
            val article = data.resolve("blogs/invalid.md")
            for (name in listOf("no-such-lucide-icon-for-test", "../file-text", "<svg/>", "", "FileText")) {
                for (published in listOf(true, false)) {
                    article.writeText("""
                        ```toml
                        title = "Invalid icon"
                        icon = "$name"
                        published = $published
                        class = "Dev"
                        date = "2025-01-01"
                        ```
                    """.trimIndent())
                    val failure = assertFailsWith<IllegalArgumentException>("icon=$name, published=$published") {
                        KoiroCatCafe(data, root.resolve("output").toString()).generateAll()
                    }
                    assertTrue(failure.message.orEmpty().contains(article.toString()))
                    assertTrue(failure.message.orEmpty().contains("'$name'"))
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
