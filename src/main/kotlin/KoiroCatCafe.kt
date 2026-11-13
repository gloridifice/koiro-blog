import content.*
import buildsystem.*
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
import routing.*
import java.io.StringWriter
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.*

class KoiroCatCafe(private val databasePath: Path, outputPath: String,
    private val homePageSize: Int = HOME_PAGE_SIZE, private val i18nPath: Path = Path.of("i18n"),
    private val release: Boolean = false) {
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
        val friends = FriendLinks.load(databasePath, config)
        val bundles = loadContent(databasePath)
        for (bundle in bundles) (bundle.metadata as? BlogMetadata)?.icon?.let { icon ->
            try { htmlgen.LucideIcons.load(icon) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("${bundle.directory.resolve("meta.toml")}: ${e.message}", e) }
        }
        val selected = bundles.filter { !release || it.metadata.published }
        val timeline = selected.filter { it.date != null }.sortedWith(
            compareByDescending<ContentBundle> { it.date }.thenBy { it.key.kind.name }.thenBy { it.key.id })
        val routes = LocalizedRoutes(config, bundles, timeline, homePageSize, databasePath, MigrationMap.load(databasePath), release)
        val resources = SiteResources(outputRoot, databasePath)
        val code = implementationFingerprint(KoiroCatCafe::class.java, TitleExtractor::class.java)
        val presentation = fingerprint(code, directoryFingerprint(i18nPath, "toml"))
        val migrationFile = databasePath.resolve("migration-map.toml")
        val routeInput = fingerprint(homePageSize.toString(), release.toString(),
            if (migrationFile.exists()) migrationFile.readText() else "", timeline.joinToString { it.key.toString() },
            *bundles.map { fingerprint(it.key.kind.name, it.key.id, it.directory.toString(),
                it.metadata.published.toString(), it.date.toString()) }.toTypedArray())
        val images = ImageAssets(databasePath, outputRoot, resources)
        fun render(block: HTML.() -> Unit) = ("<!DOCTYPE html>\n" + createHTML().html(block = block)).toByteArray(Charsets.UTF_8)
        fun html(path: ServerPath, block: HTML.() -> Unit) = resources.put(path.relativePath, render(block))
        fun context(locale: i18n.SiteLocale, page: PageKey) = PageContext(locale, page, messages, routes, config)
        val byLocale = config.locales.associate { locale ->
            locale.tag to selected.associate { it.key to resolveContent(it, locale, config) }
        }
        val rssItems = config.locales.associate { it.tag to mutableListOf<String>() }
        for (bundle in timeline.filter { it.key.kind in setOf(ContentKind.BLOG, ContentKind.DEVLOG) }) {
            val input = fingerprint(presentation, routeInput, bundle.directory.toString(), bundle.directory.resolve("meta.toml").readText(),
                *bundle.variants.toSortedMap().entries.map { (tag, variant) -> fingerprint(tag, variant.sourcePath.readText()) }.toTypedArray())
            val paths = config.locales.map { routes.content(it, bundle.key).relativePath }.toSet()
            resources.atomic("article:${bundle.key}", input, paths, restoreSources = images::restore) {
                images.capture {
                    config.locales.associate { locale ->
                        val record = byLocale.getValue(locale.tag).getValue(bundle.key)
                        routes.content(locale, bundle.key).relativePath to render {
                            articlePage(record, context(locale, PageKey.Article(bundle.key)), images)
                        }
                    }
                }
            }
            if (bundle.key.kind == ContentKind.BLOG) {
                val records = config.locales.map { byLocale.getValue(it.tag).getValue(bundle.key) }
                val rssInput = fingerprint(presentation, *records.map { record ->
                    val ctx = context(record.requested, PageKey.Blogs)
                    val meta = record.bundle.metadata as BlogMetadata
                    fingerprint(record.requested.tag, record.title, record.variant.header.summary, record.date.toString(),
                        meta.blogClass, record.variant.localeTag, record.variant.header.aiTranslation.toString(),
                        record.fallback.toString(), ctx.routes.content(ctx.locale, bundle.key).serverPath)
                }.toTypedArray())
                val items = resources.atomic("rss:${bundle.key}", rssInput, fragmentKeys = config.locales.map { it.tag }.toSet()) {
                    ResourceOutput(fragments = records.associate { record ->
                        record.requested.tag to rssItem(record, context(record.requested, PageKey.Blogs))
                    })
                }
                for (locale in config.locales) rssItems.getValue(locale.tag) += items.getValue(locale.tag)
            }
        }
        val feeds = linkedMapOf<String, ByteArray>()
        for (locale in config.locales) {
            fun context(page: PageKey) = context(locale, page)
            val resolved = byLocale.getValue(locale.tag)
            val blogs = timeline.filter { it.key.kind == ContentKind.BLOG }.map { resolved.getValue(it.key) }
            val devlogs = timeline.filter { it.key.kind == ContentKind.DEVLOG }.map { resolved.getValue(it.key) }
            val projects = selected.filter { it.key.kind == ContentKind.PORTFOLIO }.sortedBy { it.key.id }.map { resolved.getValue(it.key) }
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
            html(routes.page(locale, aboutCtx.page)) { homePage(aboutCtx, AboutHomeSubPage(aboutCtx, friends.resolve(locale))) }
            val feed = rss(rssItems.getValue(locale.tag), context(PageKey.Blogs)).toByteArray(Charsets.UTF_8)
            feeds[locale.tag] = feed
            resources.put(routes.feed(locale).relativePath, feed)
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
            val bundle = bundles.singleOrNull { it.key == key }
            if (bundle == null) {
                System.err.println("Warning: $migrationFile: skipping legacy URL ${legacy.oldUrl} for missing or skipped content: $key")
                continue
            }
            if (release && !bundle.metadata.published) continue
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
        resources.put("rss.xml", feeds.getValue(chinese.tag))
        for (asset in routes.migration.assets) {
            require(asset.oldUrl.startsWith("/images/") && !asset.oldUrl.lowercase().startsWith("/images/content/")) {
                "Legacy asset conflicts with the shared content namespace: ${asset.oldUrl}"
            }
            val source = images.copySource(routes.legacyAssetSource(asset))
            images.alias(source, asset.oldUrl.trimStart('/'))
        }
        resources.finish()
        println("Generated site in ${config.locales.size} locales: $outputRoot")
    }
}

private fun rss(items: List<String>, context: PageContext): String {
    val string = StringWriter()
    val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(string)
    fun element(name: String, text: String) { xml.writeStartElement(name); xml.writeCharacters(text); xml.writeEndElement() }
    xml.writeStartDocument("UTF-8", "1.0")
    xml.writeStartElement("rss"); xml.writeAttribute("version", "2.0"); xml.writeStartElement("channel")
    element("title", context.text("site.title")); element("link", "https://blog.koiro.xyz" + context.routes.page(context.locale, PageKey.Home()).serverPath)
    element("description", context.text("site.rssDescription")); element("language", context.locale.tag)
    element("lastBuildDate", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC"))))
    xml.flush()
    // Items are complete, escaped XML fragments generated by the same writer below.
    items.forEach(string::write)
    xml.writeEndElement(); xml.writeEndElement(); xml.writeEndDocument(); xml.close()
    return string.toString()
}

private fun rssItem(record: ResolvedContent, context: PageContext): String {
    val string = StringWriter()
    val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(string)
    fun element(name: String, text: String) { xml.writeStartElement(name); xml.writeCharacters(text); xml.writeEndElement() }
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
    xml.writeEndElement(); xml.close()
    return string.toString()
}
