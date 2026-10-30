package i18n

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.readText

@Serializable
data class LocaleSettings(
    val name: String,
    val language: String,
    val script: String? = null,
    @SerialName("match-default") val matchDefault: Boolean = false,
)

data class SiteLocale(val tag: String, val name: String, val language: String,
    val script: String?, val matchDefault: Boolean)

@Serializable
private data class ConfigDocument(
    val default: String,
    val base: String,
    @SerialName("content-fallbacks") val contentFallbacks: List<String>,
    val locales: Map<String, LocaleSettings>,
)

data class SiteConfig(val defaultLocale: SiteLocale, val baseLocale: SiteLocale,
    val locales: List<SiteLocale>, val contentFallbacks: List<SiteLocale>) {
    fun locale(tag: String): SiteLocale = locales.singleOrNull { it.tag == tag }
        ?: error("Unregistered locale: $tag")

    companion object {
        fun load(directory: Path): SiteConfig {
            val path = directory.resolve("locales.toml")
            return try {
                val doc = Toml.decodeFromString<ConfigDocument>(path.readText())
                require(doc.locales.isNotEmpty()) { "No locales registered" }
                val locales = doc.locales.map { (tag, settings) ->
                    require(isLocaleTag(tag)) { "Noncanonical locale tag: $tag" }
                    val parsed = Locale.forLanguageTag(tag)
                    require(settings.name.isNotBlank() && settings.language == parsed.language) {
                        "Invalid locale settings for $tag"
                    }
                    require(settings.script == null || settings.script.matches(Regex("[A-Z][a-z]{3}")))
                    val script = settings.script ?: parsed.script.takeIf { it.isNotBlank() }
                    require(parsed.script.isBlank() || parsed.script == script) { "Conflicting script for $tag" }
                    SiteLocale(tag, settings.name, settings.language, script, settings.matchDefault)
                }
                require(locales.filter { it.matchDefault }.groupBy { it.language to it.script }.values.all { it.size == 1 }) {
                    "Multiple match-default locales for a language/script"
                }
                fun get(tag: String) = locales.singleOrNull { it.tag == tag } ?: error("Unregistered locale: $tag")
                require(doc.base == "zh-CN" && doc.default == "en-US") { "Base must be zh-CN; default must be en-US" }
                require(doc.contentFallbacks == listOf("en-US", "zh-CN")) { "Invalid content fallback order" }
                SiteConfig(get(doc.default), get(doc.base), locales, doc.contentFallbacks.map(::get))
            } catch (e: Exception) {
                throw IllegalArgumentException("$path: ${e.message}", e)
            }
        }
    }
}

fun isLocaleTag(tag: String): Boolean = tag.matches(Regex("[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?")) &&
    Locale.forLanguageTag(tag).toLanguageTag() == tag
