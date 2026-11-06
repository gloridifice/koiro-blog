import org.jsoup.Jsoup
import java.nio.file.Files
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.*
import kotlin.test.*

class KoiroCatCafeTest {
    @Test
    fun `all language pages feeds and attachments use the requested output directory`() = withSiteFixture { data, output, locales ->
        val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
        data.resolve("image.gif").writeBytes(gif)
        data.resolve("notes.txt").writeText("attachment bytes")
        writeBundle(data, "blogs/blog", """
            id = "blog"
            published = true
            class = "Dev"
            date = "2025-01-01"
        """, "![image](../../image.gif)\n\n[attachment](../../notes.txt?version=1#read)\n\n<img src=\"../../image.gif\">", "title = \"Title <&>\"\nsummary = \"Description <&> ]]>\"")
        writeBundle(data, "devlogs/log", """
            id = "log"
            published = true
            work = "Example"
            index = 1
            date = "2025-01-02"
            preview = "../../image.gif"
        """, "![image](../../image.gif)")
        writeBundle(data, "portfolio/project", """
            id = "project"
            project = "Example"
            published = true
            url = "https://example.com"
            preview = "../../image.gif"
            status = "Done"
            type = "Game"
            lite = false
        """, "", "title = \"Project\"\ndescription = \"Example project\"")
        writeBundle(data, "actives/active", """
            id = "active"
            published = true
            date = "2025-01-03"
        """, "![image](../../image.gif)", "")
        KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll()
        for (locale in listOf("en-US", "zh-CN")) {
            for (page in listOf("home.html", "blogs.html", "portfolio.html", "about.html", "pages/blogs/blog.html", "pages/devlogs/log.html", "rss.xml"))
                assertTrue(output.resolve("$locale/$page").isRegularFile(), "$locale/$page")
            val doc = Jsoup.parse(output.resolve("$locale/pages/blogs/blog.html").readText())
            assertEquals(locale, doc.selectFirst("html")!!.attr("lang"))
            assertEquals("zh-CN", doc.selectFirst(".page_content")!!.attr("lang"))
            assertEquals("Title <&>", doc.title())
            assertEquals("/images/content/image.gif", doc.selectFirst(".page_content img")!!.attr("src"))
            assertEquals("/images/content/notes.txt?version=1#read", doc.selectFirst(".page_content a")!!.attr("href"))
            assertTrue(doc.select(".page_content img").all { it.attr("src") == "/images/content/image.gif" })
            assertTrue(doc.select(".navi_link[href]").all { it.attr("href").startsWith("/$locale/") })
            assertEquals(if (locale == "en-US") 1 else 0, doc.select(".translation-fallback").size)
            val feed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(output.resolve("$locale/rss.xml").toFile())
            assertEquals("Title <&>", feed.getElementsByTagName("item").item(0).childNodes.item(0).textContent)
            assertTrue(feed.getElementsByTagName("link").item(1).textContent.endsWith("/$locale/pages/blogs/blog.html"))
            assertTrue(feed.getElementsByTagName("description").item(1).textContent.contains("]]>"))
        }
        assertContentEquals(gif, output.resolve("images/content/image.gif").readBytes())
        assertEquals("attachment bytes", output.resolve("images/content/notes.txt").readText())
        assertEquals(output.resolve("zh-CN/rss.xml").readText(), output.resolve("rss.xml").readText())
    }

    @Test
    fun `default generation includes drafts and release removes them from pages feeds links and aliases`() = withSiteFixture { data, output, locales ->
        data.resolve("image.gif").writeBytes(Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"))
        for ((id, published) in listOf("live" to true, "draft" to false)) {
            writeBundle(data, "blogs/$id", "id = \"$id\"\npublished = $published\nclass = \"Dev\"\ndate = \"2025-01-01\"",
                "[self](zh-CN.md)", "title = \"$id\"")
        }
        writeBundle(data, "devlogs/draft", "id = \"draft\"\npublished = false\nwork = \"Example\"\nindex = 1\ndate = \"2025-01-02\"", "log")
        writeBundle(data, "actives/draft", "id = \"draft\"\npublished = false\ndate = \"2025-01-03\"",
            "[draft article](../../blogs/draft/zh-CN.md)", "")
        writeBundle(data, "portfolio/draft", """
            id = "draft"
            published = false
            project = "Example"
            url = "https://example.com"
            preview = "../../image.gif"
            status = "WIP"
            type = "Game"
            lite = true
        """, "", "title = \"Draft project\"\ndescription = \"Work in progress\"")
        data.resolve("migration-map.toml").writeText("""
            [[contents]]
            kind = "blog"
            old-source = "blogs/old.md"
            directory = "blogs/draft"
            id = "draft"
            old-url = "/pages/blogs/Old.html"
        """.trimIndent())
        val directories = arrayOf(data.toString(), output.toString(), locales.toString())
        for (release in listOf(false, true, false)) {
            main(directories + if (release) arrayOf("--release") else emptyArray())
            for (locale in listOf("en-US", "zh-CN")) {
                assertTrue(output.resolve("$locale/pages/blogs/live.html").isRegularFile())
                for (kind in listOf("blogs", "devlogs")) {
                    val article = output.resolve("$locale/pages/$kind/draft.html")
                    assertEquals(!release, article.exists())
                    if (!release && kind == "blogs") {
                        assertEquals("/$locale/pages/blogs/draft.html", Jsoup.parse(article.readText()).selectFirst(".page_content a")!!.attr("href"))
                    }
                }
                val home = Jsoup.parse(output.resolve("$locale/home.html").readText())
                assertEquals(if (release) 1 else 4, home.select(".elements > .element").size)
                assertEquals(!release, home.selectFirst("#active-draft a[href='/$locale/pages/blogs/draft.html']") != null)
                val projects = Jsoup.parse(output.resolve("$locale/portfolio.html").readText())
                assertEquals(!release, projects.selectFirst("#project-draft") != null)
                val feed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(output.resolve("$locale/rss.xml").toFile())
                val items = feed.getElementsByTagName("item")
                assertEquals(if (release) 1 else 2, items.length)
                assertEquals(!release, feed.documentElement.textContent.contains("/$locale/pages/blogs/draft.html"))
            }
            assertEquals(!release, output.resolve("pages/blogs/Old.html").exists())
        }
        assertTrue(data.resolve("blogs/draft/meta.toml").readText().contains("published = false"))
        data.resolve("blogs/live/zh-CN.md").appendText("\n\n[draft](../draft/zh-CN.md)")
        val failure = assertFailsWith<IllegalArgumentException> { main(directories + "--release") }
        assertTrue(failure.message.orEmpty().contains("Unpublished or missing link target"))
    }

    @Test
    fun `variant selection is atomic and AI and missing translation states follow the selected variant`() = withSiteFixture { data, output, locales ->
        locales.resolve("locales.toml").appendText("\n[locales.ja-JP]\nname = \"日本語\"\nlanguage = \"ja\"\nmatch-default = true\n")
        val base = locales.resolve("zh-CN.toml")
        base.writeText(base.readText().replace("[languageNames]", "[languageNames]\nja-JP = \"日语\""))
        val source = writeBundle(data, "blogs/post", """
            id = "post"
            published = true
            class = "Dev"
            date = "2025-01-01"
        """, "CHINESE-BODY", "title = \"中文标题\"\nsummary = \"中文摘要\"")
        val english = source.resolveSibling("en-US.md")
        english.writeText("```toml\ntitle = \"English title\"\nsummary = \"English summary\"\nai-translation = true\n```\nENGLISH-BODY")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        for ((locale, title, fallback, ai) in listOf(
            listOf("zh-CN", "中文标题", "false", "false"), listOf("en-US", "English title", "false", "true"), listOf("ja-JP", "English title", "true", "true"))) {
            val doc = Jsoup.parse(output.resolve("$locale/pages/blogs/post.html").readText())
            assertEquals(title, doc.selectFirst(".title")!!.text())
            assertEquals(if (fallback == "true") 1 else 0, doc.select(".translation-fallback").size)
            assertEquals((if (fallback == "true") 1 else 0) + (if (ai == "true") 1 else 0), doc.select(".translation-line").size)
            val card = Jsoup.parse(output.resolve("$locale/home.html").readText()).selectFirst(".post_preview")!!
            assertEquals(if (ai == "true" || fallback == "true") 1 else 0, card.select(".translation-marker").size)
        }
        english.writeText(english.readText().replace("title =", "published = false\ntitle ="))
        site.generateAll()
        val fallback = Jsoup.parse(output.resolve("en-US/pages/blogs/post.html").readText())
        assertEquals("中文标题", fallback.title())
        assertFalse(fallback.text().contains("ENGLISH-BODY"))
        assertEquals(1, fallback.select(".translation-line").size)
        assertTrue(fallback.selectFirst(".page_description + .article-translation-notices") != null)
        assertEquals("https://blog.koiro.xyz/zh-CN/pages/blogs/post.html", fallback.selectFirst("link[rel=canonical]")!!.attr("href"))
        assertEquals("noindex,follow", fallback.selectFirst("meta[name=robots]")!!.attr("content"))
        assertTrue(fallback.select("link[hreflang=en-US]").isEmpty())
    }

    @Test
    fun `article links and legacy aliases use stable IDs and an unsafe output manifest cannot delete outside files`() = withSiteFixture { data, output, locales ->
        val source = writeBundle(data, "blogs/post", "id = \"post\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\"",
            "[self](zh-CN.md#heading1_0)", "title = \"Original\"")
        data.resolve("migration-map.toml").writeText("""
            version = 1
            [[contents]]
            kind = "blog"
            old-source = "blogs/old.md"
            directory = "blogs/post"
            id = "post"
            old-url = "/pages/blogs/Old.html"
            [[assets]]
            old-url = "/images/blogs/Art/attachment.txt"
            source = "blogs/post/attachment.txt"
        """.trimIndent())
        source.resolveSibling("attachment.txt").writeText("same bytes")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        var page = Jsoup.parse(output.resolve("en-US/pages/blogs/post.html").readText())
        assertEquals("/en-US/pages/blogs/post.html#heading1_0", page.selectFirst(".page_content a")!!.attr("href"))
        val legacy = Jsoup.parse(output.resolve("pages/blogs/Old.html").readText())
        assertEquals("/zh-CN/pages/blogs/post.html", legacy.selectFirst("#legacy-target")!!.attr("href"))
        source.writeText(source.readText().replace("Original", "Renamed title"))
        site.generateAll()
        page = Jsoup.parse(output.resolve("en-US/pages/blogs/post.html").readText())
        assertEquals("Renamed title", page.title())
        source.parent.moveTo(data.resolve("blogs/renamed_storage"))
        site.generateAll()
        assertEquals("Renamed title", Jsoup.parse(output.resolve("en-US/pages/blogs/post.html").readText()).title())
        assertEquals("same bytes", output.resolve("images/blogs/Art/attachment.txt").readText())
        assertEquals("same bytes", output.resolve("images/content/blogs/renamed_storage/attachment.txt").readText())
        val sentinel = output.parent.resolve("keep.txt"); sentinel.writeText("must survive")
        output.resolve(".generated-site-files.json").writeText("[\"../keep.txt\"]")
        assertFailsWith<IllegalArgumentException> { site.generateAll() }
        assertEquals("must survive", sentinel.readText())
    }

    @Test
    fun `plugin sources are not content but stray Markdown elsewhere still fails`() = withSiteFixture { data, output, locales ->
        writeBundle(data, "blogs/post", "id = \"post\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\"", "article")
        val plugin = data.resolve("component/plugins/koiro_blog").createDirectories()
        plugin.resolve("README.md").writeText("Plugin documentation, not a content package")
        plugin.resolve("node_modules/library").createDirectories().resolve("README.md").writeText("Dependency docs")
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        assertTrue(output.resolve("en-US/pages/blogs/post.html").isRegularFile())
        assertFalse(output.resolve("component").exists())
        val stray = data.resolve("component/README.md")
        stray.writeText("Still invalid outside the excluded plugins subtree")
        val failure = assertFailsWith<IllegalArgumentException> { site.generateAll() }
        assertTrue(failure.message.orEmpty().contains(stray.toString()))
    }

    @Test
    fun `duplicate IDs across categories cannot silently overwrite a page`() = withSiteFixture { data, output, locales ->
        val meta = "id = \"same\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\""
        writeBundle(data, "blogs/one", meta, "one")
        writeBundle(data, "blogs/category/two", meta, "two")
        val failure = assertFailsWith<IllegalArgumentException> { KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll() }
        assertTrue(failure.message.orEmpty().contains("Duplicate"))
        assertTrue(failure.message.orEmpty().contains(data.resolve("blogs/one").toString()))
        assertFalse(output.resolve("en-US/home.html").exists())
    }

    @Test
    fun `invalid published content fails before writing pages`() = withSiteFixture { data, output, locales ->
        val source = writeBundle(data, "blogs/post", """
            id = "post"
            published = true
            class = "Dev"
            date = "2025-01-01"
        """, "content")
        for ((file, replacement) in listOf(
            source to "```toml\ntitle = \"Valid title\"\nai-translation = \"false\"\n```\nbody",
            source to "```toml\npublished = false\n```\nbody",
            source.resolveSibling("meta.toml") to "id = \"bad-id\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\"")) {
            val original = file.readText(); file.writeText(replacement)
            val failure = assertFailsWith<IllegalArgumentException> { KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll() }
            assertTrue(failure.message.orEmpty().contains(source.parent.toString()))
            assertFalse(output.resolve("en-US/home.html").exists())
            file.writeText(original)
        }
        source.deleteExisting()
        assertFailsWith<IllegalArgumentException> { KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll() }
    }
}
