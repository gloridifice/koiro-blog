package routing

import ServerPath
import com.akuleshov7.ktoml.Toml
import content.*
import decodeURL
import i18n.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import kotlin.io.path.*

sealed interface PageKey {
    data class Home(val number: Int = 1) : PageKey
    data object Blogs : PageKey
    data object Portfolio : PageKey
    data object About : PageKey
    data class Article(val key: ContentKey) : PageKey
}

@Serializable
data class LegacyContent(val kind: String, @SerialName("old-source") val oldSource: String,
    val directory: String, val id: String, @SerialName("old-url") val oldUrl: String? = null)
@Serializable
data class LegacyAsset(@SerialName("old-url") val oldUrl: String, val source: String)
@Serializable
data class MigrationMap(val version: Int = 1, val contents: List<LegacyContent> = emptyList(),
    val assets: List<LegacyAsset> = emptyList()) {
    companion object {
        fun load(root: Path): MigrationMap {
            val file = root.resolve("migration-map.toml")
            if (!file.exists()) return MigrationMap()
            return Toml.decodeFromString<MigrationMap>(file.readText()).also { require(it.version == 1) { "$file: unsupported map version" } }
        }
    }
}

class LocalizedRoutes(private val config: SiteConfig, private val bundles: List<ContentBundle>,
    val timeline: List<ContentBundle>, private val pageSize: Int, private val root: Path,
    val migration: MigrationMap) {
    val pageCount = maxOf(1, (timeline.size + pageSize - 1) / pageSize)
    private val byDirectory = bundles.associateBy { it.directory.toAbsolutePath().normalize() }
    private val byKey = bundles.associateBy { it.key }
    private val oldKeys = migration.contents.associate { item ->
        val kind = ContentKind.entries.single { it.name.lowercase() == item.kind }
        item.oldUrl to ContentKey(kind, item.id)
    }.filterKeys { it != null }

    fun page(locale: SiteLocale, key: PageKey): ServerPath {
        val suffix = when (key) {
            is PageKey.Home -> {
                require(key.number in 1..pageCount) { "Invalid home page ${key.number}" }
                if (key.number == 1) "home.html" else "home-${key.number}.html"
            }
            PageKey.Blogs -> "blogs.html"
            PageKey.Portfolio -> "portfolio.html"
            PageKey.About -> "about.html"
            is PageKey.Article -> {
                require(key.key.kind in setOf(ContentKind.BLOG, ContentKind.DEVLOG))
                "pages/${key.key.kind.directory}/${key.key.id}.html"
            }
        }
        return ServerPath("${locale.tag}/$suffix")
    }
    fun feed(locale: SiteLocale) = ServerPath("${locale.tag}/rss.xml")
    fun content(locale: SiteLocale, key: ContentKey): ServerPath {
        require(byKey[key]?.metadata?.published == true) { "Unpublished or missing link target: $key" }
        return when (key.kind) {
            ContentKind.BLOG, ContentKind.DEVLOG -> page(locale, PageKey.Article(key))
            ContentKind.ACTIVE -> {
                val index = timeline.indexOfFirst { it.key == key }
                require(index >= 0)
                ServerPath("${page(locale, PageKey.Home(index / pageSize + 1)).relativePath}#active-${key.id}")
            }
            ContentKind.PORTFOLIO -> ServerPath("${page(locale, PageKey.Portfolio).relativePath}#project-${key.id}")
        }
    }
    fun legacyAssetSource(asset: LegacyAsset): Path {
        val owner = migration.contents.filter { asset.source.startsWith(it.directory + "/") }.maxByOrNull { it.directory.length }
            ?: return root.resolve(asset.source)
        val kind = ContentKind.entries.single { it.name.lowercase() == owner.kind }
        val bundle = byKey[ContentKey(kind, owner.id)] ?: error("Missing legacy asset owner: ${owner.kind}/${owner.id}")
        return bundle.directory.resolve(asset.source.removePrefix(owner.directory + "/"))
    }

    fun resolveLink(source: Path, href: String, locale: SiteLocale): String {
        if (href.isBlank() || href.startsWith('#') || href.startsWith("//")) return href
        val local = href.removePrefix("https://blog.koiro.xyz").removePrefix("http://blog.koiro.xyz")
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(local)) return href
        val index = local.indexOfFirst { it == '?' || it == '#' }.let { if (it == -1) local.length else it }
        val path = local.take(index).decodeURL()
        val suffix = local.substring(index)
        if (config.locales.any { path.startsWith("/${it.tag}/") }) return href
        val oldKey = oldKeys[path]
        if (oldKey != null) return content(locale, oldKey).serverPath + suffix
        val legacyPage = when (path) {
            "/home.html" -> PageKey.Home()
            "/blogs.html" -> PageKey.Blogs
            "/portfolio.html" -> PageKey.Portfolio
            "/about.html" -> PageKey.About
            else -> Regex("/home-(\\d+)\\.html").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()?.let { PageKey.Home(it) }
        }
        if (legacyPage != null) return page(locale, legacyPage).serverPath + suffix
        require(!path.startsWith("/pages/")) { "$source: missing legacy article link $href" }
        if (!path.endsWith(".md")) return href
        val target = source.toAbsolutePath().parent.resolve(path).normalize()
        require(target.startsWith(root.toAbsolutePath().normalize())) { "$source: escaping article link $href" }
        val bundle = byDirectory[target.parent] ?: error("$source: missing article link $href")
        return content(locale, bundle.key).serverPath + suffix
    }
}

data class PageContext(val locale: SiteLocale, val page: PageKey, val messages: Messages,
    val routes: LocalizedRoutes, val config: SiteConfig) {
    fun message(key: String, vararg args: Pair<String, Any>): MessageText =
        messages.format(locale, key, args.associate { it.first to it.second.toString() })
    fun text(key: String, vararg args: Pair<String, Any>) = message(key, *args).text
    fun languageName(tag: String) = text("languageNames.$tag")
}
