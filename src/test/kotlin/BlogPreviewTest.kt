import org.jsoup.Jsoup
import kotlin.io.path.*
import kotlin.test.*

class BlogPreviewTest {
    @Test
    fun `both preview sizes render the configured icon with emoji and default fallbacks`() = withSiteFixture { data, output, locales ->
        data class Case(val icon: String?, val emoji: String?, val expectedIcon: String?)
        val cases = listOf(Case("keyboard", null, "keyboard"), Case("palette", "🎨", "palette"),
            Case(null, "📝<&>", null), Case(null, null, "file-text"), Case(null, " ", "file-text"))
        for (case in cases) {
            val meta = "id = \"post\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\"" +
                (case.icon?.let { "\nicon = \"$it\"" } ?: "") + (case.emoji?.let { "\nemoji = \"$it\"" } ?: "")
            writeBundle(data, "blogs/post", meta, "", "title = \"Title <&>\"\nsummary = \"Description <&>\"")
            KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll()
            val document = Jsoup.parse(output.resolve("zh-CN/blogs.html").readText())
            val cards = document.select(".post_preview.blog")
            assertEquals(2, cards.size)
            for (card in cards) {
                assertEquals("/zh-CN/pages/blogs/post.html", card.attr("href"))
                assertEquals("Title <&>", card.selectFirst("h2")!!.text())
                val icon = card.selectFirst(".blog_icon")!!
                assertEquals("true", icon.attr("aria-hidden"))
                if (case.expectedIcon == null) {
                    assertTrue(icon.select("svg").isEmpty()); assertEquals(case.emoji, icon.text())
                } else {
                    val svg = icon.selectFirst("svg")!!
                    assertTrue(svg.hasClass("lucide-${case.expectedIcon}"))
                    assertEquals("currentColor", svg.attr("stroke"))
                    assertTrue(svg.children().isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `invalid icon configuration fails with source context even for unpublished blogs`() = withSiteFixture { data, output, locales ->
        for (name in listOf("no-such-lucide-icon-for-test", "../file-text", "<svg/>", "", "FileText")) {
            for (published in listOf(true, false)) {
                val source = writeBundle(data, "blogs/invalid", """
                    id = "invalid"
                    icon = "$name"
                    published = $published
                    class = "Dev"
                    date = "2025-01-01"
                """, "")
                val error = assertFailsWith<IllegalArgumentException> { KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll() }
                assertTrue(error.message.orEmpty().contains(source.resolveSibling("meta.toml").toString()))
                assertTrue(error.message.orEmpty().contains("'$name'"))
            }
        }
    }
}
