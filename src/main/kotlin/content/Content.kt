package content

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import i18n.SiteConfig
import i18n.SiteLocale
import i18n.isLocaleTag
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

enum class ContentKind(val directory: String) { BLOG("blogs"), DEVLOG("devlogs"), ACTIVE("actives"), PORTFOLIO("portfolio") }
data class ContentKey(val kind: ContentKind, val id: String)

sealed interface CommonMetadata { val id: String; val published: Boolean }
@Serializable
data class BlogMetadata(override val id: String, override val published: Boolean, val date: String,
    @SerialName("class") val blogClass: String, val tags: List<String> = emptyList(),
    val icon: String? = null, val emoji: String? = null) : CommonMetadata
@Serializable
data class DevlogMetadata(override val id: String, override val published: Boolean, val date: String,
    val work: String, val index: Int, val preview: String? = null) : CommonMetadata
@Serializable
data class ActiveMetadata(override val id: String, override val published: Boolean, val date: String) : CommonMetadata
@Serializable
data class PortfolioMetadata(override val id: String, override val published: Boolean, val project: String,
    val url: String, val preview: String, val status: String, val type: String, val lite: Boolean,
    val date: String? = null) : CommonMetadata

@Serializable
data class VariantHeader(val title: String? = null, val summary: String = "", val description: String? = null,
    val published: Boolean = true, @SerialName("ai-translation") val aiTranslation: Boolean = false,
    val preview: String? = null)
data class ContentVariant(val localeTag: String, val header: VariantHeader, val markdown: String, val sourcePath: Path)
data class ContentBundle(val key: ContentKey, val directory: Path, val metadata: CommonMetadata,
    val variants: Map<String, ContentVariant>) {
    val date: LocalDateTime? get() = when (val m = metadata) {
        is BlogMetadata -> parseDate(m.date)
        is DevlogMetadata -> parseDate(m.date)
        is ActiveMetadata -> parseDate(m.date)
        is PortfolioMetadata -> null
    }
}
data class ResolvedContent(val bundle: ContentBundle, val requested: SiteLocale, val variant: ContentVariant) {
    val fallback get() = requested.tag != variant.localeTag
    val title get() = variant.header.title.orEmpty()
    val date get() = requireNotNull(bundle.date)
}

fun parseDate(value: String): LocalDateTime = try { LocalDateTime.parse(value) }
    catch (_: IllegalArgumentException) { LocalDate.parse(value).atTime(0, 0) }

fun validateId(value: String) {
    require(value.matches(Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")) &&
        !value.matches(Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])"))) { "Invalid snake_case id: $value" }
}

private val input = TomlInputConfig(ignoreUnknownNames = true)
private val toml = Toml(inputConfig = input)

private fun validateHeader(text: String, source: Path, known: Set<String>) {
    val children = TomlParser(input).parseString(text).children
    val nodes = children.filterIsInstance<TomlKeyValuePrimitive>()
    val keys = children.map { it.name }
    require(keys.distinct().size == keys.size) { "$source: duplicate TOML keys" }
    for (key in keys) if (key !in known) println("$source: preserved unrecognized metadata $key")
    for (node in nodes) {
        val key = node.key.toString()
        if (key in setOf("published", "ai-translation", "lite")) {
            require(node.value.content is Boolean) { "$source: $key must be a boolean" }
        }
        if (key in setOf("id", "date", "class", "icon", "emoji", "work", "project", "url", "preview", "status", "type", "title", "summary", "description")) {
            require(node.value.content is String) { "$source: $key must be text" }
        }
        if (key == "index") require(node.value.content is Long) { "$source: index must be an integer" }
    }
}

fun loadContent(root: Path): List<ContentBundle> {
    val normalized = root.toAbsolutePath().normalize()
    Files.walk(normalized).use { paths -> paths.forEach { require(!it.isSymbolicLink()) { "Symlink in content: $it" } } }
    val result = mutableListOf<ContentBundle>()
    for (kind in ContentKind.entries) {
        val folder = normalized.resolve(kind.directory)
        if (!folder.exists()) continue
        val metas = Files.walk(folder).use { it.filter { p -> p.name == "meta.toml" }.sorted().toList() }
        for (path in metas) try {
            require(metas.none { it != path && path.parent.startsWith(it.parent) }) { "Nested content package: $path" }
            validateId(path.parent.name)
            val text = path.readText()
            val common = setOf("id", "published", "date")
            val metadata = when (kind) {
                ContentKind.BLOG -> {
                    validateHeader(text, path, common + setOf("class", "tags", "icon", "emoji"))
                    toml.decodeFromString<BlogMetadata>(text)
                }
                ContentKind.DEVLOG -> { validateHeader(text, path, common + setOf("work", "index", "preview")); toml.decodeFromString<DevlogMetadata>(text) }
                ContentKind.ACTIVE -> { validateHeader(text, path, common); toml.decodeFromString<ActiveMetadata>(text) }
                ContentKind.PORTFOLIO -> {
                    validateHeader(text, path, common + setOf("project", "url", "preview", "status", "type", "lite"))
                    toml.decodeFromString<PortfolioMetadata>(text)
                }
            }
            validateId(metadata.id)
            val variants = path.parent.listDirectoryEntries("*.md").mapNotNull { file ->
                val tag = file.nameWithoutExtension
                require(!tag.matches(Regex("[a-z]{2,3}_[A-Z]{2}"))) { "$file: use a hyphen in locale filenames" }
                if (!isLocaleTag(tag)) return@mapNotNull null
                val match = Regex("\\A\\s*```toml[^\\S\\r\\n]*\\r?\\n(.*?)^```[^\\S\\r\\n]*(?:\\r?\\n|\\z)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
                    .find(file.readText()) ?: error("$file: missing fenced TOML header")
                val headerText = match.groupValues[1]
                validateHeader(headerText, file, setOf("title", "summary", "description", "published", "ai-translation", "preview"))
                val h = toml.decodeFromString<VariantHeader>(headerText)
                if (metadata.published && h.published) {
                    if (kind != ContentKind.ACTIVE) require(!h.title.isNullOrBlank()) { "$file: title required" }
                    if (kind == ContentKind.PORTFOLIO) require(!h.description.isNullOrBlank()) { "$file: description required" }
                }
                tag to ContentVariant(tag, h, file.readText().substring(match.range.last + 1), file)
            }.toMap()
            if (metadata.published) require(variants["zh-CN"]?.header?.published == true) { "$path: published content requires zh-CN.md" }
            val bundle = ContentBundle(ContentKey(kind, metadata.id), path.parent, metadata, variants)
            bundle.date // Validate even unpublished date metadata.
            val previous = result.firstOrNull { it.key == bundle.key }
            require(previous == null) { "Duplicate ${bundle.key}: ${previous?.directory} and ${bundle.directory}" }
            result += bundle
        } catch (e: Exception) { throw IllegalArgumentException("$path: ${e.message}", e) }
    }
    Files.walk(normalized).use { paths -> paths.filter { it.extension == "md" }.forEach { file ->
        require(result.any { file.startsWith(it.directory) }) { "$file: legacy Markdown outside a content package; run scripts/migrate_content.py" }
    } }
    return result
}

fun resolveContent(bundle: ContentBundle, locale: SiteLocale, config: SiteConfig): ResolvedContent {
    val variant = (listOf(locale.tag) + config.contentFallbacks.map { it.tag }).distinct()
        .firstNotNullOfOrNull { bundle.variants[it]?.takeIf { v -> v.header.published } }
        ?: error("${bundle.directory}: no available variant for ${locale.tag}")
    return ResolvedContent(bundle, locale, variant)
}
