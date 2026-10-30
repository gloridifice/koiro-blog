import htmlgen.ImageAssets
import htmlgen.page.ArticleContent
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import org.w3c.dom.Element
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleContentTest {
    private fun renderHtml(markdown: String, root: Path = Path.of(".").toAbsolutePath()): String {
        val article = ArticleContent(markdown, root.resolve("post.md"), ImageAssets(root, root.resolve("output")))
        return createHTML(prettyPrint = false).div {
            with(article) { showPostContent() }
        }
    }

    private fun render(markdown: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(renderHtml(markdown).byteInputStream()).documentElement

    @Test
    fun `only consecutive image paragraphs in the same block form a gallery`() {
        data class Case(val markdown: String, val galleries: List<List<String>>, val locations: List<String>)
        val a = "![a](a.png)"
        val b = "![b](b.png)"
        val cases = listOf(
            Case("$a\n\n$b", listOf(listOf("a", "b")), listOf(".stack-container img")),
            Case("$a $b", listOf(listOf("a", "b")), listOf(".stack-container img")),
            Case("$a\n\n正文\n\n$b", emptyList(), listOf("div > .image_wrapper img")),
            Case("$a\n\n# 标题\n\n$b", emptyList(), listOf("div > .image_wrapper img")),
            Case("- 问题\n\n    $a\n\n    [说明](https://example.com)\n\n- 其他问题\n\n正文\n\n$b",
                emptyList(), listOf("ul img[alt=a]", "div > .image_wrapper img[alt=b]")),
            Case("- $a\n\n- $b", emptyList(), listOf("li:first-child img[alt=a]", "li:last-child img[alt=b]")),
            Case("> $a\n>\n> $b\n\n$a", listOf(listOf("a", "b")),
                listOf("blockquote .stack-container img", "div > .image_wrapper img[alt=a]")),
            Case("前文 $a 后文\n\n$b", emptyList(), listOf("p img[alt=a]", "div > .image_wrapper img[alt=b]"))
        )
        val root = Files.createTempDirectory("article-images")
        try {
            for (name in listOf("a", "b")) {
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", root.resolve("$name.png").toFile())
            }
            for (case in cases) {
                val doc = Jsoup.parseBodyFragment(renderHtml(case.markdown, root))
                assertEquals(case.markdown.count { it == '!' }, doc.select("img").size, case.markdown)
                assertEquals(case.galleries, doc.select(".stack-container").map { gallery ->
                    gallery.select("img").map { it.attr("alt") }
                }, case.markdown)
                for (selector in case.locations) {
                    assertTrue(doc.select(selector).isNotEmpty(), "${case.markdown}: $selector")
                }
                if (case.markdown.startsWith("前文")) {
                    assertEquals("前文 <img> 后文", doc.selectFirst("p")!!.childNodes().joinToString("") {
                        if (it.nodeName() == "img") "<img>" else it.toString()
                    })
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `literal bracket expressions are not broken links and reference definitions provide real destinations`() {
        val doc = render("[0, 1] and [优先] and [reference][target]\n\n[target]: https://example.com/resource")
        val links = doc.getElementsByTagName("a")
        assertEquals(1, links.length)
        assertEquals("https://example.com/resource", (links.item(0) as Element).getAttribute("href"))
        assertEquals("[0, 1] and [优先] and reference", doc.getElementsByTagName("p").item(0).textContent)
    }

    @Test
    fun `inline formatting removes only matched delimiters and preserves nesting`() {
        data class Case(val markdown: String, val text: String, val tags: List<String>)
        val cases = listOf(
            Case("**加粗**", "加粗", listOf("strong")),
            Case("__加粗__", "加粗", listOf("strong")),
            Case("*斜体*", "斜体", listOf("em")),
            Case("_斜体_", "斜体", listOf("em")),
            Case("~~删除线~~", "删除线", listOf("del")),
            Case("***粗斜体***", "粗斜体", listOf("em", "strong")),
            Case("**加粗 *斜体* 和 ~~删除线~~**", "加粗 斜体 和 删除线", listOf("strong", "em", "del")),
            Case("**a * b**", "a * b", listOf("strong")),
            Case("*a ** b*", "a ** b", listOf("em")),
            Case("~~a ~ b~~", "a ~ b", listOf("del")),
            Case("未闭合 ** 和 * 与 ~~", "未闭合 ** 和 * 与 ~~", emptyList()),
            Case("snake_case 和 a_b_c", "snake_case 和 a_b_c", emptyList())
        )
        for (case in cases) {
            val element = render(case.markdown).getElementsByTagName("p").item(0) as Element
            assertEquals(case.text, element.textContent, case.markdown)
            val tags = element.getElementsByTagName("*")
            assertEquals(case.tags.sorted(), (0 until tags.length).map { tags.item(it).nodeName }.sorted(), case.markdown)
        }
    }

    @Test
    fun `inline formatting is rendered in headings lists quotes tables and links`() {
        val content = "**加粗** *斜体* ~~删除线~~"
        val cases = listOf(
            content to "p",
            "# $content" to "h1",
            "#### $content" to "h4",
            "$content\n======" to "h1",
            "- $content" to "li",
            "1. $content" to "li",
            "> $content" to "blockquote",
            "| $content |\n| --- |\n| body |" to "td",
            "[$content](https://example.com)" to "a"
        )
        for ((markdown, tag) in cases) {
            val element = render(markdown).getElementsByTagName(tag).item(0) as Element
            assertEquals("加粗 斜体 删除线", element.textContent.trim(), markdown)
            for (inlineTag in listOf("strong", "em", "del")) {
                assertEquals(1, element.getElementsByTagName(inlineTag).length, markdown)
            }
            if (tag == "a") assertEquals("https://example.com", element.getAttribute("href"))
        }
    }

    @Test
    fun `HTML blocks do not swallow subsequent Markdown with Windows or Unix line endings`() {
        val markdown = """
            <aside>文章说明</aside>

            # **正文标题**

            **加粗** *斜体* ~~删除线~~

            ```rust
            let value = "<b>**literal**</b>";
            let next = value;
            ```

            ## 后续章节

            最后一个段落。
        """.trimIndent()
        for (lineEnding in listOf("\r\n", "\n", "\r")) {
            val element = render(markdown.replace("\n", lineEnding))
            val context = "lineEnding=${lineEnding.toByteArray().toList()}"
            assertEquals(1, element.getElementsByTagName("aside").length, context)
            assertEquals(1, element.getElementsByTagName("h1").length, context)
            assertEquals("正文标题", element.getElementsByTagName("h1").item(0).textContent, context)
            assertEquals(1, element.getElementsByTagName("h2").length, context)
            assertEquals(2, element.getElementsByTagName("strong").length, context)
            assertEquals(1, element.getElementsByTagName("em").length, context)
            assertEquals(1, element.getElementsByTagName("del").length, context)
            val code = element.getElementsByTagName("pre").item(0) as Element
            assertEquals("let value = \"<b>**literal**</b>\";\nlet next = value;", code.textContent, context)
            assertEquals(0, code.getElementsByTagName("b").length, context)
            assertEquals("最后一个段落。", element.getElementsByTagName("p").item(1).textContent, context)
        }
    }

    @Test
    fun `code spans preserve literal markup and backticks while normalizing padding`() {
        val cases = listOf(
            "`**加粗** *斜体* ~~删除线~~`" to "**加粗** *斜体* ~~删除线~~",
            "`` `tick` ``" to "`tick`",
            "`` a  b ``" to "a  b",
            "`   `" to "   ",
            "`a\nb`" to "a b",
            "`<b>&</b>`" to "<b>&</b>"
        )
        for ((markdown, expected) in cases) {
            val element = render(markdown).getElementsByTagName("code").item(0) as Element
            assertEquals(expected, element.textContent, markdown)
            assertEquals(0, element.getElementsByTagName("*").length, markdown)
        }
    }
}
