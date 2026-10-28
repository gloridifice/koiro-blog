import kotlinx.datetime.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class KoiroCatCafeTest {
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
