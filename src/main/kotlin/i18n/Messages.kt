package i18n

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import com.akuleshov7.ktoml.tree.nodes.TomlStubEmptyNode
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

data class MessageText(val text: String, val actualLocale: SiteLocale)

private sealed interface Part {
    data class Text(val text: String) : Part
    data class Argument(val name: String) : Part
}

private class Template(source: String) {
    val parts: List<Part> = buildList {
        val text = StringBuilder()
        fun flush() { if (text.isNotEmpty()) { add(Part.Text(text.toString())); text.clear() } }
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                (c == '{' || c == '}') && source.getOrNull(i + 1) == c -> { text.append(c); i += 2 }
                c == '{' -> {
                    flush()
                    val end = source.indexOf('}', i + 1)
                    require(end >= 0) { "Unclosed placeholder" }
                    val name = source.substring(i + 1, end)
                    require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Invalid placeholder: $name" }
                    add(Part.Argument(name)); i = end + 1
                }
                c == '}' -> error("Unmatched closing brace")
                else -> { text.append(c); i++ }
            }
        }
        flush()
    }
    val arguments = parts.filterIsInstance<Part.Argument>().map { it.name }.toSet()
    fun format(args: Map<String, String>): String {
        require(args.keys == arguments) { "Expected parameters $arguments, received ${args.keys}" }
        return parts.joinToString("") { when (it) { is Part.Text -> it.text; is Part.Argument -> args.getValue(it.name) } }
    }
}

class Messages(private val config: SiteConfig, directory: Path, private val warning: (String) -> Unit = ::println) {
    private val warned = mutableSetOf<String>()
    private fun warn(message: String) { if (warned.add(message)) warning(message) }
    private val dictionaries: Map<String, Map<String, Template>> = config.locales.associate { locale ->
        val path = directory.resolve("${locale.tag}.toml")
        val values = if (!path.exists() && locale != config.baseLocale) {
            warn("Missing dictionary: $path; using ${config.baseLocale.tag}")
            emptyMap()
        } else try {
            val values = linkedMapOf<String, String>()
            fun collect(node: TomlNode, prefix: String) {
                for (child in node.children) {
                    val key = if (prefix.isEmpty()) child.name else "$prefix.${child.name}"
                    when (child) {
                        is TomlTable -> collect(child, key)
                        is TomlStubEmptyNode -> Unit // ktoml represents an empty table with a placeholder node.
                        is TomlKeyValuePrimitive -> {
                            require(child.value.content is String) { "$key must contain text" }
                            require(values.put(key, child.value.content as String) == null) { "Duplicate key $key" }
                        }
                        else -> require(child.name.isBlank()) { "Invalid dictionary value: $key" }
                    }
                }
            }
            collect(TomlParser(TomlInputConfig()).parseString(path.readText()), "")
            values
        } catch (e: Exception) { throw IllegalArgumentException("$path: ${e.message}", e) }
        locale.tag to values.mapValues { (key, value) ->
            try { Template(value) } catch (e: Exception) { throw IllegalArgumentException("$path: $key: ${e.message}", e) }
        }
    }
    init {
        val base = dictionaries.getValue(config.baseLocale.tag)
        require(base.isNotEmpty()) { "Empty base dictionary: ${config.baseLocale.tag}" }
        for ((tag, dictionary) in dictionaries) for ((key, template) in dictionary) {
            val expected = base[key]
            if (expected == null) warn("$tag: unknown message key $key; ignored")
            else require(template.arguments == expected.arguments) { "$tag: $key: parameter mismatch" }
        }
    }
    fun format(locale: SiteLocale, key: String, args: Map<String, String> = emptyMap()): MessageText {
        val base = dictionaries.getValue(config.baseLocale.tag)[key]
            ?: error("${config.baseLocale.tag}: missing base message key $key")
        val local = dictionaries.getValue(locale.tag)[key]
        if (local == null) warn("${locale.tag}: missing $key; using ${config.baseLocale.tag}")
        return try {
            MessageText((local ?: base).format(args), if (local == null) config.baseLocale else locale)
        } catch (e: Exception) { throw IllegalArgumentException("${locale.tag}: $key: ${e.message}", e) }
    }
}
