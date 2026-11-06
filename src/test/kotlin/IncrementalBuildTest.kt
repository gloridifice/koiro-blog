import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.*
import kotlin.test.*

class IncrementalBuildTest {
    @Test
    fun `article languages form one atom while other pages and feeds are rebuilt`() = withSiteFixture { data, output, locales ->
        val post = writeBundle(data, "blogs/post", blogMeta("post"), "Chinese body")
        val english = post.resolveSibling("en-US.md")
        english.writeText("```toml\ntitle = \"English\"\nsummary = \"First summary\"\n```\nEnglish body")
        writeBundle(data, "blogs/other", blogMeta("other"), "Other body")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        val articles = listOf("en-US", "zh-CN").map { output.resolve("$it/pages/blogs/post.html") }
        val before = articles.map { it.getLastModifiedTime() }
        val other = output.resolve("en-US/pages/blogs/other.html")
        val otherTime = other.getLastModifiedTime()
        val home = output.resolve("en-US/home.html")
        val homeTime = home.getLastModifiedTime()
        val feed = output.resolve("en-US/rss.xml")
        val feedTime = feed.getLastModifiedTime()
        site.generateAll()
        assertEquals(before, articles.map { it.getLastModifiedTime() })
        assertNotEquals(homeTime, home.getLastModifiedTime())
        assertNotEquals(feedTime, feed.getLastModifiedTime())

        articles.first().deleteExisting()
        site.generateAll()
        assertTrue(articles.all { it.isRegularFile() })
        assertNotEquals(before.last(), articles.last().getLastModifiedTime(), "A missing language rebuilds the entire article")
        assertEquals(otherTime, other.getLastModifiedTime())
        val repaired = articles.map { it.getLastModifiedTime() }
        english.writeText(english.readText().replace("English body", "Revised body").replace("First summary", "Updated summary"))
        site.generateAll()
        assertTrue(articles.indices.all { repaired[it] != articles[it].getLastModifiedTime() })
        assertTrue(Jsoup.parse(articles.first().readText()).text().contains("Revised body"))
        assertEquals(otherTime, other.getLastModifiedTime())
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(feed.toFile())
        assertTrue(xml.documentElement.textContent.contains("Updated summary"))
        assertFalse(xml.documentElement.textContent.contains("First summary"))
    }

    @Test
    fun `cached articles retain and repair their attachments and backward source timestamps update aliases`() = withSiteFixture { data, output, locales ->
        val image = data.resolve("image.gif")
        val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
        image.writeBytes(gif)
        val post = writeBundle(data, "blogs/post", blogMeta("post"), "![image](../../image.gif)")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        val copied = output.resolve("images/content/image.gif")
        val copiedTime = copied.getLastModifiedTime()
        val article = output.resolve("en-US/pages/blogs/post.html")
        val articleTime = article.getLastModifiedTime()
        site.generateAll()
        assertEquals(copiedTime, copied.getLastModifiedTime())
        assertEquals(articleTime, article.getLastModifiedTime())
        copied.deleteExisting()
        site.generateAll()
        assertContentEquals(gif, copied.readBytes())
        assertEquals(articleTime, article.getLastModifiedTime(), "Attachment repair must not require rendering a cached article")

        data.resolve("migration-map.toml").writeText("""
            [[assets]]
            old-url = "/images/legacy.gif"
            source = "image.gif"
        """.trimIndent())
        site.generateAll()
        val alias = output.resolve("images/legacy.gif")
        val aliasTime = alias.getLastModifiedTime()
        site.generateAll()
        assertEquals(aliasTime, alias.getLastModifiedTime())
        val changed = gif + byteArrayOf(1, 2, 3)
        image.writeBytes(changed)
        image.setLastModifiedTime(FileTime.fromMillis(1_000))
        site.generateAll()
        assertContentEquals(changed, copied.readBytes())
        assertContentEquals(changed, alias.readBytes())

        data.resolve("migration-map.toml").deleteExisting()
        image.moveTo(data.resolve("renaming.gif")).moveTo(data.resolve("IMAGE.gif"))
        post.writeText(post.readText().replace("image.gif", "IMAGE.gif"))
        site.generateAll()
        assertEquals(listOf("IMAGE.gif"), output.resolve("images/content").listDirectoryEntries().map { it.name })
        assertContentEquals(changed, output.resolve("images/content/IMAGE.gif").readBytes())
        post.writeText("```toml\ntitle = \"No image\"\n```\nNo image")
        site.generateAll()
        assertFalse(copied.exists())
        assertFalse(alias.exists())
    }

    @Test
    fun `cached articles invalidate on dictionaries and route changes without committing failed generation`() = withSiteFixture { data, output, locales ->
        writeBundle(data, "blogs/post", blogMeta("post"), "[target](../target/zh-CN.md)")
        val target = writeBundle(data, "blogs/target", blogMeta("target"), "Target")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales, release = true)
        site.generateAll()
        val article = output.resolve("en-US/pages/blogs/post.html")
        val before = article.getLastModifiedTime()
        val dictionary = locales.resolve("en-US.toml")
        dictionary.appendText("\n# Dictionary revision\n")
        site.generateAll()
        assertNotEquals(before, article.getLastModifiedTime())
        val previousHtml = article.readText()
        val previousManifest = output.resolve(".generated-site-files.json").readText()
        val meta = target.resolveSibling("meta.toml")
        meta.writeText(meta.readText().replace("published = true", "published = false"))
        assertFailsWith<IllegalArgumentException> { site.generateAll() }
        assertEquals(previousHtml, article.readText())
        assertEquals(previousManifest, output.resolve(".generated-site-files.json").readText())
    }

    private fun blogMeta(id: String) = "id = \"$id\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\""
}
