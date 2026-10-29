import kotlinx.datetime.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class KoiroCatCafeTest {
    @Test
    fun `all generated pages feeds and attachments use the requested output directory`() {
        val root = Files.createTempDirectory("site-output-")
        try {
            val data = root.resolve("data")
            for (folder in listOf("blogs", "devlogs", "portfolio", "actives")) {
                data.resolve(folder).createDirectories()
            }
            val image = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
            data.resolve("image.gif").writeBytes(image)
            data.resolve("blogs/post.md").writeText("""
                ```toml
                title = "Blog"
                published = true
                class = "Programming"
                date = "2025-01-01"
                ```
                ![blog image](../image.gif)
            """.trimIndent())
            data.resolve("devlogs/post.md").writeText("""
                ```toml
                title = "Devlog"
                work = "Example"
                index = 1
                published = true
                date = "2025-01-02"
                preview = "../image.gif"
                ```
                ![devlog image](../image.gif)
            """.trimIndent())
            data.resolve("portfolio/project.md").writeText("""
                ```toml
                title = "Project"
                project = "Example"
                description = "Example project"
                url = "https://example.com"
                preview = "../image.gif"
                published = true
                status = "Done"
                type = "Game"
                lite = false
                ```
            """.trimIndent())
            data.resolve("actives/post.md").writeText("""
                ```toml
                date = "2025-01-03"
                published = true
                ```
                ![active image](../image.gif)
            """.trimIndent())

            val output = root.resolve("custom-output")
            KoiroCatCafe(data, output.toString()).generateAll()

            for (page in listOf("home.html", "blogs.html", "portfolio.html", "about.html",
                "pages/blogs/Blog.html", "pages/devlogs/Devlog.html")) {
                assertTrue(Files.isRegularFile(output.resolve(page)), "Missing generated page: $page")
            }
            assertTrue(output.resolve("images/image.gif").readBytes().contentEquals(image))
            assertTrue(output.resolve("pages/blogs/Blog.html").readText().contains("src=\"/images/image.gif\""))
            assertTrue(output.resolve("rss.xml").readText().contains("https://blog.koiro.xyz/pages/blogs/Blog.html"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `RSS article links preserve the server path without duplicate slashes or extensions`() {
        val paths = listOf("pages/blogs/example.html", "/pages/blogs/中文 标题.html")
        val posts = paths.map { path ->
            BlogRecord(
                BlogHeader(
                    title = "Example",
                    published = true,
                    blogClass = "Programming",
                    date = "2025-01-01"
                ),
                ServerPath(path)
            )
        }

        val rss = KoiroCatCafe.generateRssXmlString(posts, LocalDateTime(2025, 1, 1, 0, 0))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(rss.byteInputStream())
        val links = document.getElementsByTagName("link")

        assertEquals(
            listOf(
                "https://blog.koiro.xyz",
                "https://blog.koiro.xyz/pages/blogs/example.html",
                "https://blog.koiro.xyz/pages/blogs/中文 标题.html"
            ),
            (0 until links.length).map { links.item(it).textContent }
        )
    }
}
