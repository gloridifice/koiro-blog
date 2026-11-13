import org.jsoup.Jsoup
import java.awt.image.BufferedImage
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.imageio.ImageIO
import javax.tools.ToolProvider
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

    @Test
    fun `images and aliases survive unrelated compiled code changes but follow processing changes in directories and jars`() {
        for (packaged in listOf(false, true)) withSiteFixture { data, output, locales ->
            val classes = data.parent.resolve("classes").createDirectories()
            val original = Path.of(KoiroCatCafe::class.java.protectionDomain.codeSource.location.toURI())
            Files.walk(original).use { files -> files.filter { it.isRegularFile() }.forEach { file ->
                val destination = classes.resolve(original.relativize(file))
                destination.createParentDirectories()
                Files.copy(file, destination)
            } }
            val image = data.resolve("image.png")
            ImageIO.write(BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
            writeBundle(data, "blogs/post", blogMeta("post"), "![image](../../image.png)")
            data.resolve("migration-map.toml").writeText("""
                [[assets]]
                old-url = "/images/legacy.png"
                source = "image.png"
            """.trimIndent())

            fun generate() {
                val location = if (packaged) data.parent.resolve("site.jar").also { jar ->
                    JarOutputStream(Files.newOutputStream(jar)).use { archive ->
                        Files.walk(classes).use { files -> files.filter { it.isRegularFile() }.sorted().forEach { file ->
                            archive.putNextEntry(JarEntry(classes.relativize(file).invariantSeparatorsPathString))
                            Files.copy(file, archive)
                            archive.closeEntry()
                        } }
                    }
                } else classes
                // Isolate compiled site code without changing the checkout or introducing generator flags.
                val loader = object : URLClassLoader(arrayOf(location.toUri().toURL()), KoiroCatCafe::class.java.classLoader) {
                    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
                        val type = findLoadedClass(name) ?: if (findResource(name.replace('.', '/') + ".class") != null) findClass(name)
                            else super.loadClass(name, false)
                        if (resolve) resolveClass(type)
                        type
                    }
                }
                loader.use {
                    it.loadClass("MainKt").getMethod("main", Array<String>::class.java)
                        .invoke(null, arrayOf(data.toString(), output.toString(), locales.toString()) as Any)
                }
            }
            fun addCompiledClass(name: String) {
                val source = data.parent.resolve("$name.java")
                source.writeText("public class $name {}")
                assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()))
            }

            generate()
            val copied = output.resolve("images/content/image.png")
            val alias = output.resolve("images/legacy.png")
            val article = output.resolve("en-US/pages/blogs/post.html")
            val first = listOf(copied, alias, article).map { it.getLastModifiedTime() }
            addCompiledClass("UnrelatedPage")
            generate()
            assertEquals(first[0], copied.getLastModifiedTime(), "Page code must not rewrite images (packaged=$packaged)")
            assertEquals(first[1], alias.getLastModifiedTime(), "Page code must not rewrite aliases (packaged=$packaged)")
            assertNotEquals(first[2], article.getLastModifiedTime(), "The changed site code must still invalidate article HTML")

            addCompiledClass("ImageCompressor\$PipelineRevision")
            generate()
            assertNotEquals(first[0], copied.getLastModifiedTime(), "Processing code must invalidate images (packaged=$packaged)")
            assertNotEquals(first[1], alias.getLastModifiedTime(), "Processing code must invalidate aliases (packaged=$packaged)")
            assertEquals(32, ImageIO.read(copied.toFile()).width)
            assertContentEquals(copied.readBytes(), alias.readBytes())
        }
    }

    private fun blogMeta(id: String) = "id = \"$id\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\""
}
