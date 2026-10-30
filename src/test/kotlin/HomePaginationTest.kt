import htmlgen.component.HomePagination
import htmlgen.component.homePagination
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePaginationTest {
    @Test
    fun `static pages partition the published mixed timeline without loading other pages images`() {
        data class Case(val count: Int, val pageSize: Int, val pageCounts: List<Int>)
        val cases = listOf(
            Case(0, 10, listOf(0)),
            Case(1, 10, listOf(1)),
            Case(10, 10, listOf(10)),
            Case(11, 10, listOf(10, 1)),
            Case(20, 10, listOf(10, 10)),
            Case(21, 10, listOf(10, 10, 1)),
            Case(7, 3, listOf(3, 3, 1)),
        )
        for (case in cases) {
            withSite(case.count) { data, output ->
                // A newer unpublished item must not consume a slot or produce another page.
                writeBundle(data, "actives/draft", "id = \"draft\"\ndate = \"2030-01-01\"\npublished = false", "unpublished-entry", "")
                KoiroCatCafe(data, output.toString(), case.pageSize).generateAll()
                val allIds = mutableListOf<String>()
                for ((index, count) in case.pageCounts.withIndex()) {
                    val number = index + 1
                    val document = page(output, number)
                    val elements = document.select(".elements > .element")
                    assertEquals(count, elements.size, "$case page $number")
                    val ids = elements.map { element ->
                        (element.selectFirst("h2") ?: element.selectFirst(".page_content p")!!).text()
                    }
                    allIds += ids
                    assertEquals(1, document.select(".navi_link.highlighted[href='/zh-CN/home.html']").size)
                    assertFalse(document.text().contains("unpublished-entry"))
                    for ((position, element) in elements.withIndex()) {
                        for (image in element.select("img")) {
                            assertEquals("/images/content/${ids[position]}.gif", image.attr("src"))
                            assertEquals(if (position < 2) "eager" else "lazy", image.attr("loading"))
                            assertEquals("async", image.attr("decoding"))
                            assertEquals("1", image.attr("width"))
                            assertEquals("1", image.attr("height"))
                            // Resizing must not depend on receiving the newest stylesheet.
                            assertTrue(Regex("(?:^|;)\\s*height\\s*:\\s*auto\\s*(?:;|$)").containsMatchIn(image.attr("style")))
                        }
                    }
                    if (case.pageCounts.size == 1) {
                        assertTrue(document.select(".home-pagination").isEmpty())
                    } else {
                        val footer = document.selectFirst(".home-pagination")!!
                        assertEquals(number.toString(), footer.selectFirst("[aria-current=page]")!!.text())
                        val first = case.pageCounts.take(index).sum() + 1
                        assertEquals(
                            listOf("第 $first–${first + count - 1} 条，共 ${case.count} 条", "第 $number / ${case.pageCounts.size} 页"),
                            footer.select(".home-pagination__meta > span").map { it.text() },
                        )
                        for ((label, target) in listOf("上一页" to number - 1, "下一页" to number + 1)) {
                            val control = footer.selectFirst("[aria-label='$label']")!!
                            if (target in 1..case.pageCounts.size) {
                                assertEquals("a", control.tagName())
                                assertEquals(url(target), control.attr("href"))
                            } else {
                                assertFalse(control.hasAttr("href"))
                                assertEquals("true", control.attr("aria-disabled"))
                            }
                        }
                        for (link in footer.select("a[href]")) {
                            assertTrue(Files.isRegularFile(output.resolve(link.attr("href").removePrefix("/"))))
                        }
                    }
                }
                assertEquals((0 until case.count).reversed().map(::entryId), allIds, "$case order and completeness")
                assertFalse(Files.exists(output.resolve(url(case.pageCounts.size + 1).removePrefix("/"))))
            }
        }
    }

    @Test
    fun `regenerating a shorter timeline removes obsolete pages but keeps unrelated files`() {
        withSite(21) { data, output ->
            val site = KoiroCatCafe(data, output.toString())
            site.generateAll()
            assertTrue(Files.exists(output.resolve("zh-CN/home-3.html")))
            output.resolve("zh-CN/home-notes.html").writeText("keep this file")
            for (index in 0 until 18) sourcePath(data, index).parent.toFile().deleteRecursively()

            site.generateAll()

            assertEquals(3, page(output, 1).select(".elements > .element").size)
            assertTrue(page(output, 1).select(".home-pagination").isEmpty())
            assertFalse(Files.exists(output.resolve("zh-CN/home-2.html")))
            assertFalse(Files.exists(output.resolve("zh-CN/home-3.html")))
            assertEquals("keep this file", output.resolve("zh-CN/home-notes.html").readText())
        }
    }

    @Test
    fun `home image placeholders use compressed dimensions and preserve gallery content`() {
        withSite(4) { data, output ->
            ImageIO.write(BufferedImage(4096, 2048, BufferedImage.TYPE_INT_RGB), "png", data.resolve("wide.png").toFile())
            val devlog = sourcePath(data, 1).resolveSibling("meta.toml")
            devlog.writeText(devlog.readText().replace("preview = \"../../entry_001.gif\"", "preview = \"../../wide.png\""))
            sourcePath(data, 0).writeText("""
                ```toml
                ```
                entry_000

                ![wide image](../../wide.png)

                ![small image](../../entry_000.gif)
            """.trimIndent())

            KoiroCatCafe(data, output.toString()).generateAll()

            val document = page(output, 1)
            val published = ImageIO.read(output.resolve("images/content/wide.png").toFile())
            assertEquals(2048, published.width)
            assertEquals(1024, published.height)
            val wideImages = document.select(".elements img[src='/images/content/wide.png']")
            assertEquals(2, wideImages.size, "Devlog preview and active gallery both use the published image")
            for (image in wideImages) {
                assertEquals(published.width.toString(), image.attr("width"))
                assertEquals(published.height.toString(), image.attr("height"))
                assertEquals("lazy", image.attr("loading"))
            }
            val stack = document.selectFirst(".stack-container")!!
            val smallImage = stack.selectFirst("img[src='/images/content/entry_000.gif']")!!
            assertEquals("1", smallImage.attr("width"))
            assertEquals("1", smallImage.attr("height"))
            assertEquals(listOf("wide image", "small image"), stack.select("img").map { it.attr("alt") })
            assertTrue(stack.select("img").all { it.attr("loading") == "lazy" })
            val detail = Jsoup.parse(output.resolve("zh-CN/pages/devlogs/entry_001.html").readText())
            assertEquals(1, detail.select(".page_content img").size)
            assertTrue(detail.select(".page_content img[loading]").isEmpty(), "Article loading is outside this change")
        }
    }

    @Test
    fun `large archives show bounded page links with gaps and a reachable last page`() {
        for ((number, expected) in listOf(1 to listOf(1, 2, 9), 5 to listOf(1, 4, 5, 6, 9), 9 to listOf(1, 8, 9))) {
            val config = i18n.SiteConfig.load(java.nio.file.Path.of("i18n"))
            val routes = routing.LocalizedRoutes(config, emptyList(), List(81) { index ->
                content.ContentBundle(content.ContentKey(content.ContentKind.ACTIVE, "item_$index"), java.nio.file.Path.of("data"),
                    content.ActiveMetadata("item_$index", true, "2025-01-01"), emptyMap())
            }, 10, java.nio.file.Path.of("data"), routing.MigrationMap())
            val context = routing.PageContext(config.locale("zh-CN"), routing.PageKey.Home(number), i18n.Messages(config, java.nio.file.Path.of("i18n")), routes, config)
            val html = createHTML().div { homePagination(HomePagination(number, 81, 10), context) }
            val document = Jsoup.parse(html)
            val controls = document.select(".home-pagination__pages .home-pagination__control")
            assertEquals(expected, controls.map { it.text().toInt() })
            assertEquals(if (number == 5) 2 else 1, document.select(".home-pagination__ellipsis").size)
            assertEquals(number.toString(), document.selectFirst("[aria-current=page]")!!.text())
            for (link in controls.filter { it.tagName() == "a" }) {
                assertEquals(url(link.text().toInt()), link.attr("href"))
            }
        }
    }

    private fun entryId(index: Int) = "entry_${index.toString().padStart(3, '0')}"

    private fun sourcePath(data: Path, index: Int): Path =
        data.resolve("${listOf("actives", "devlogs", "blogs")[index % 3]}/${entryId(index)}/zh-CN.md")

    private fun url(number: Int) = if (number == 1) "/zh-CN/home.html" else "/zh-CN/home-$number.html"

    private fun page(output: Path, number: Int): Document =
        Jsoup.parse(output.resolve(url(number).removePrefix("/")).readText())

    private fun withSite(count: Int, block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("home-pagination-")
        try {
            val data = root.resolve("data")
            for (folder in listOf("blogs", "devlogs", "portfolio", "actives")) data.resolve(folder).createDirectories()
            val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
            for (index in 0 until count) {
                val id = entryId(index)
                data.resolve("$id.gif").writeBytes(gif)
                val extra = when (index % 3) {
                    1 -> "work = \"Example\"\nindex = $index\npreview = \"../../$id.gif\""
                    2 -> "class = \"Dev\""
                    else -> ""
                }
                val date = LocalDate.of(2025, 1, 1).plusDays(index.toLong())
                val source = sourcePath(data, index)
                source.parent.createDirectories()
                source.resolveSibling("meta.toml").writeText("id = \"$id\"\ndate = \"$date\"\npublished = true\n$extra\n")
                source.writeText("```toml\ntitle = \"$id\"\n```\n$id\n\n![image $id](../../$id.gif)\n")
            }
            block(data, root.resolve("output"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
