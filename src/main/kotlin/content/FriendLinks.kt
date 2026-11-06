package content

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.*
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlArray
import i18n.SiteConfig
import i18n.SiteLocale
import i18n.isLocaleTag
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText

private data class FriendTranslation(val name: String?, val description: String)
private data class FriendLink(val id: String, val name: String, val url: String,
    val translations: Map<String, FriendTranslation>)

data class ResolvedFriendLink(val name: String, val url: String, val description: String,
    val actualLocale: SiteLocale, val nameLocale: SiteLocale?)

/** Structured about-page content, separate from article routes and UI message templates. */
class FriendLinks private constructor(private val byLocale: Map<String, List<ResolvedFriendLink>>) {
    fun resolve(locale: SiteLocale): List<ResolvedFriendLink> = byLocale.getValue(locale.tag)

    companion object {
        fun load(root: Path, config: SiteConfig): FriendLinks {
            val path = root.resolve("friends.toml")
            try {
                val document = TomlParser(TomlInputConfig.compliant()).parseString(path.readText())
                val rootFields = fields(document, "root", setOf("friends"))
                val node = rootFields["friends"]
                val entries = when (node) {
                    is TomlTable -> {
                        require(node.type == TableType.ARRAY && node.children.all { it is TomlArrayOfTablesElement }) {
                            "friends: expected [[friends]] entries or friends = []"
                        }
                        node.children.toList()
                    }
                    is TomlKeyValueArray -> {
                        require((node.value as? TomlArray)?.parse()?.isEmpty() == true) {
                            "friends: expected [[friends]] entries or friends = []"
                        }
                        emptyList()
                    }
                    else -> throw IllegalArgumentException("friends: expected [[friends]] entries or friends = []")
                }
                val friends = entries.mapIndexed { index, entry -> readFriend(entry, index, config) }
                val ids = mutableSetOf<String>()
                for (friend in friends) require(ids.add(friend.id)) { "friends[${friend.id}].id: Duplicate friend ID" }
                // Resolve once per build so each missing ID/locale pair warns only once.
                return FriendLinks(config.locales.associate { locale ->
                    locale.tag to friends.map { friend ->
                        val tag = (listOf(locale.tag) + config.contentFallbacks.map { it.tag }).distinct()
                            .first { it in friend.translations }
                        val translation = friend.translations.getValue(tag)
                        val actual = config.locale(tag)
                        if (tag != locale.tag) println("$path: friends[${friend.id}]: missing ${locale.tag}; using $tag")
                        ResolvedFriendLink(translation.name ?: friend.name, friend.url, translation.description,
                            actual, actual.takeIf { translation.name != null })
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("$path: ${e.message}", e)
            }
        }
    }
}

private fun readFriend(node: TomlNode, index: Int, config: SiteConfig): FriendLink {
    val id = text(node.children.firstOrNull { it.name == "id" }, "friends[${index + 1}].id")
    val context = "friends[$id]"
    val values = fields(node, context, setOf("id", "name", "url", "translations"))
    try { validateId(id) }
    catch (e: IllegalArgumentException) { throw IllegalArgumentException("$context.id: ${e.message}", e) }
    val name = text(values["name"], "$context.name")
    val url = text(values["url"], "$context.url")
    val uri = try { URI(url).parseServerAuthority() }
    catch (e: Exception) { throw IllegalArgumentException("$context.url: invalid HTTP/HTTPS URL", e) }
    require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.port in -1..65535) {
        "$context.url: expected an absolute HTTP/HTTPS URL"
    }
    val translationsContext = "$context.translations"
    val translations = fields(table(values["translations"], translationsContext), translationsContext).mapValues { (tag, node) ->
        val at = "$translationsContext.$tag"
        require(isLocaleTag(tag) && config.locales.any { it.tag == tag }) { "$at: noncanonical or unregistered locale" }
        val translation = fields(table(node, at), at, setOf("name", "description"))
        FriendTranslation(translation["name"]?.let { text(it, "$at.name") }, text(translation["description"], "$at.description"))
    }
    require(config.baseLocale.tag in translations) { "$translationsContext.${config.baseLocale.tag}: description required" }
    return FriendLink(id, name, url, translations)
}

private fun table(node: TomlNode?, context: String): TomlTable {
    require(node is TomlTable && node.type == TableType.PRIMITIVE) { "$context: expected a table" }
    return node
}

private fun fields(node: TomlNode, context: String, allowed: Set<String>? = null): Map<String, TomlNode> {
    val result = linkedMapOf<String, TomlNode>()
    for (child in node.children.filterNot { it is TomlStubEmptyNode }) {
        require(allowed == null || child.name in allowed) { "$context.${child.name}: unknown field" }
        val previous = result[child.name]
        // ktoml repeats implicit parent tables for sibling subtables inside [[friends]].
        // Merge only those parents; explicit table redefinitions and duplicate values remain errors.
        if (previous is TomlTable && child is TomlTable &&
            previous.type == TableType.PRIMITIVE && child.type == TableType.PRIMITIVE &&
            (previous.isSynthetic || child.isSynthetic)) {
            previous.children.addAll(child.children)
            previous.isSynthetic = previous.isSynthetic && child.isSynthetic
        } else {
            require(previous == null) { "$context.${child.name}: duplicate field" }
            result[child.name] = child
        }
    }
    return result
}

private fun text(node: TomlNode?, context: String): String {
    val value = (node as? TomlKeyValuePrimitive)?.value?.content
    require(value is String && value.isNotBlank()) { "$context: nonblank text required" }
    return value
}
