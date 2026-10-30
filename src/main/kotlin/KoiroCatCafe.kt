import content.*
import htmlgen.HOME_PAGE_SIZE
import htmlgen.ImageAssets
import htmlgen.component.HomePagination
import htmlgen.component.languageConfiguration
import htmlgen.component.message
import htmlgen.component.home_element.*
import htmlgen.page.articlePage
import htmlgen.page.home.homePage
import htmlgen.page.home.subpage.*
import i18n.Messages
import i18n.SiteConfig
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import routing.*
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.*

class KoiroCatCafe(private val databasePath: Path, outputPath: String,
    private val homePageSize: Int = HOME_PAGE_SIZE, private val i18nPath: Path = Path.of("i18n")) {
    private val outputRoot = Path.of(outputPath).toAbsolutePath().normalize()
    init {
        require(homePageSize > 0) { "Home page size must be positive" }
        val input = databasePath.toAbsolutePath().normalize()
        require(!outputRoot.startsWith(input) && !input.startsWith(outputRoot)) { "Content and output directories must not overlap" }
        require(!outputRoot.isSymbolicLink()) { "Output must not be a symlink: $outputRoot" }
    }

    fun generateAll() {
        val config = SiteConfig.load(i18nPath)
        val messages = Messages(config, i18nPath)
        val bundles = loadContent(databasePath)
        for (bundle in bundles) (bundle.metadata as? BlogMetadata)?.icon?.let { icon ->
            try { htmlgen.LucideIcons.load(icon) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("${bundle.directory.resolve("meta.toml")}: ${e.message}", e) }
        }
        val published = bundles.filter { it.metadata.published }
        val timeline = published.filter { it.date != null }.sortedWith(
            compareByDescending<ContentBundle> { it.date }.thenBy { it.key.kind.name }.thenBy { it.key.id })
        val routes = LocalizedRoutes(config, bundles, timeline, homePageSize, databasePath, MigrationMap.load(databasePath))
        val images = ImageAssets(databasePath, outputRoot)
        val outputs = linkedMapOf<String, ByteArray>()
        val outputNames = mutableSetOf<String>()
        fun put(path: String, bytes: ByteArray) {
            checkedOutput(path)
            require(outputNames.add(path.lowercase())) { "Duplicate or case-colliding generated output: $path" }
            outputs[path] = bytes
        }
        fun html(path: ServerPath, block: HTML.() -> Unit) {
            put(path.relativePath, ("<!DOCTYPE html>\n" + createHTML().html(block = block)).toByteArray(Charsets.UTF_8))
        }
        for (locale in config.locales) {
            fun context(page: PageKey) = PageContext(locale, page, messages, routes, config)
            val resolved = published.associate { it.key to resolveContent(it, locale, config) }
            val blogs = timeline.filter { it.key.kind == ContentKind.BLOG }.map { resolved.getValue(it.key) }
            val devlogs = timeline.filter { it.key.kind == ContentKind.DEVLOG }.map { resolved.getValue(it.key) }
            val projects = published.filter { it.key.kind == ContentKind.PORTFOLIO }.sortedBy { it.key.id }.map { resolved.getValue(it.key) }
            for (record in blogs + devlogs) {
                val ctx = context(PageKey.Article(record.bundle.key))
                html(routes.content(locale, record.bundle.key)) { articlePage(record, ctx, images) }
            }
            for (number in 1..routes.pageCount) {
                val ctx = context(PageKey.Home(number))
                val pagination = HomePagination(number, timeline.size, homePageSize)
                val elements = timeline.subList(pagination.offset, pagination.lastItem).map { bundle ->
                    val record = resolved.getValue(bundle.key)
                    when (bundle.key.kind) {
                        ContentKind.BLOG -> BlogElement(record, ctx)
                        ContentKind.DEVLOG -> DevlogElement(record, images, ctx)
                        ContentKind.ACTIVE -> ActiveElement(record, images, ctx)
                        else -> error("Project cannot enter timeline")
                    }
                }
                html(routes.page(locale, ctx.page)) { homePage(ctx, MainHomeSubPage(elements, pagination, ctx)) }
            }
            val blogCtx = context(PageKey.Blogs)
            html(routes.page(locale, blogCtx.page)) { homePage(blogCtx, BlogsHomeSubPage(blogs, blogCtx)) }
            val portfolioCtx = context(PageKey.Portfolio)
            html(routes.page(locale, portfolioCtx.page)) { homePage(portfolioCtx, PortfolioHomeSubPage(projects, devlogs, portfolioCtx, images)) }
            val aboutCtx = context(PageKey.About)
            html(routes.page(locale, aboutCtx.page)) { homePage(aboutCtx, AboutHomeSubPage(aboutCtx)) }
            put(routes.feed(locale).relativePath, rss(blogs, context(PageKey.Blogs)).toByteArray(Charsets.UTF_8))
        }
        val defaultContext = PageContext(config.defaultLocale, PageKey.Home(), messages, routes, config)
        html(ServerPath("index.html")) {
            lang = config.defaultLocale.tag
            head {
                meta { charset = "utf-8" }; meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                title { +defaultContext.text("site.title") }
            }
            body {
                h1 { message(defaultContext, "site.chooseLanguage") }
                ul { for (locale in config.locales) li {
                    a(routes.page(locale, PageKey.Home()).serverPath) { lang = locale.tag; attributes["data-locale"] = locale.tag; +locale.name }
                } }
                languageConfiguration(defaultContext, root = true)
            }
        }
        val chinese = config.locale("zh-CN")
        val aliases = linkedMapOf<String, String>()
        for (number in 1..routes.pageCount) aliases[if (number == 1) "home.html" else "home-$number.html"] = routes.page(chinese, PageKey.Home(number)).serverPath
        for ((file, page) in listOf("blogs.html" to PageKey.Blogs, "portfolio.html" to PageKey.Portfolio, "about.html" to PageKey.About)) {
            aliases[file] = routes.page(chinese, page).serverPath
        }
        for (legacy in routes.migration.contents.filter { it.oldUrl != null }) {
            val key = ContentKey(ContentKind.entries.single { it.name.lowercase() == legacy.kind }, legacy.id)
            val bundle = bundles.singleOrNull { it.key == key } ?: error("Legacy map references missing content: $key")
            if (!bundle.metadata.published) continue
            require(legacy.oldUrl!!.startsWith("/pages/${key.kind.directory}/") && legacy.oldUrl.endsWith(".html")) { "Invalid legacy article URL: ${legacy.oldUrl}" }
            val alias = legacy.oldUrl.trimStart('/')
            require(alias !in aliases) { "Duplicate legacy URL: $alias" }
            aliases[alias] = routes.content(chinese, key).serverPath
        }
        for ((path, target) in aliases) html(ServerPath(path)) {
            lang = "zh-CN"
            head {
                meta { charset = "utf-8" }; meta { name = "robots"; content = "noindex,follow" }
                meta { httpEquiv = "refresh"; content = "0; url=$target" }
                link { rel = "canonical"; href = "https://blog.koiro.xyz$target" }
                title { +"Koiro's Cat Café" }
                script { src = "/assets/js/legacy-redirect.js"; defer = true }
            }
            body { a(target) { id = "legacy-target"; +target } }
        }
        put("rss.xml", outputs.getValue(routes.feed(chinese).relativePath))
        for (asset in routes.migration.assets) {
            require(asset.oldUrl.startsWith("/images/") && !asset.oldUrl.lowercase().startsWith("/images/content/")) {
                "Legacy asset conflicts with the shared content namespace: ${asset.oldUrl}"
            }
            val source = images.copySource(routes.legacyAssetSource(asset))
            val file = outputRoot.resolve(source.relativePath)
            val alias = asset.oldUrl.trimStart('/')
            val bytes = if (file.extension.equals(Path.of(alias).extension, true)) file.readBytes() else {
                val image = requireNotNull(ImageIO.read(file.toFile())) { "Unsupported legacy image format: $file" }
                java.io.ByteArrayOutputStream().use { stream ->
                    require(ImageIO.write(image, Path.of(alias).extension, stream)) { "Cannot encode legacy image: $alias" }
                    stream.toByteArray()
                }
            }
            if (alias == source.relativePath) continue
            put(alias, bytes)
        }
        val manifest = outputRoot.resolve(".generated-site-files.json")
        val previous = if (manifest.exists()) Json.decodeFromString<List<String>>(manifest.readText()) else emptyList()
        previous.forEach(::checkedOutput)
        // All page generation and content/link validation above must succeed before replacing HTML.
        for ((relative, bytes) in outputs) checkedOutput(relative).createParentDirectories().writeBytes(bytes)
        for (obsolete in previous.toSet() - outputs.keys) checkedOutput(obsolete).deleteIfExists()
        manifest.writeText(Json.encodeToString(outputs.keys.toList()))
        println("Generated ${outputs.size} files in ${config.locales.size} locales: $outputRoot")
    }

    private fun checkedOutput(relative: String): Path {
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains('\\')) { "Unsafe output: $relative" }
        val path = outputRoot.resolve(relative).normalize()
        require(path.startsWith(outputRoot) && path != outputRoot) { "Escaping output: $relative" }
        var parent: Path? = path
        while (parent != null && parent.startsWith(outputRoot)) {
            require(!parent.isSymbolicLink()) { "Output symlink: $parent" }; parent = parent.parent
        }
        return path
    }
}

private fun rss(records: List<ResolvedContent>, context: PageContext): String {
    val string = StringWriter()
    val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(string)
    fun element(name: String, text: String) { xml.writeStartElement(name); xml.writeCharacters(text); xml.writeEndElement() }
    xml.writeStartDocument("UTF-8", "1.0")
    xml.writeStartElement("rss"); xml.writeAttribute("version", "2.0"); xml.writeStartElement("channel")
    element("title", context.text("site.title")); element("link", "https://blog.koiro.xyz" + context.routes.page(context.locale, PageKey.Home()).serverPath)
    element("description", context.text("site.rssDescription")); element("language", context.locale.tag)
    element("lastBuildDate", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC"))))
    for (record in records) {
        xml.writeStartElement("item")
        element("title", record.title)
        val link = "https://blog.koiro.xyz" + context.routes.content(context.locale, record.bundle.key).serverPath
        element("link", link)
        xml.writeStartElement("guid"); xml.writeAttribute("isPermaLink", "true"); xml.writeCharacters(link); xml.writeEndElement()
        val date = java.time.LocalDateTime.parse(record.date.toString()).atZone(ZoneId.of("Asia/Shanghai"))
        element("pubDate", DateTimeFormatter.RFC_1123_DATE_TIME.format(date))
        val notices = htmlgen.component.translationMessages(record, context).joinToString(" ") { it.second.text }
        val description = "${record.variant.header.summary} #${(record.bundle.metadata as BlogMetadata).blogClass}".trim()
        element("description", listOf(description, notices).filter { it.isNotBlank() }.joinToString("\n"))
        xml.writeEndElement()
    }
    xml.writeEndElement(); xml.writeEndElement(); xml.writeEndDocument(); xml.close()
    return string.toString()
}
