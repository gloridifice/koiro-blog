import i18n.Messages
import i18n.SiteConfig
import kotlin.io.path.*
import kotlin.test.*

class MessagesTest {
    @Test
    fun `messages fall back to Chinese and format parameters once without changing word order`() = withSiteFixture { _, _, directory ->
        directory.resolve("zh-CN.toml").writeText("[example]\nmessage = \"{{{first}}} / {last}\"\n")
        directory.resolve("en-US.toml").writeText("[example]\nmessage = \"{last} / {{{first}}}\"\n")
        val config = SiteConfig.load(directory)
        var messages = Messages(config, directory)
        val result = messages.format(config.defaultLocale, "example.message", mapOf("first" to "{last}", "last" to "<&>"))
        assertEquals("<&> / {{last}}", result.text)
        assertEquals("en-US", result.actualLocale.tag)
        directory.resolve("en-US.toml").writeText("[example]\n")
        val empty = Messages(config, directory).format(config.defaultLocale, "example.message", mapOf("first" to "one", "last" to "two"))
        assertEquals("zh-CN", empty.actualLocale.tag)
        directory.resolve("en-US.toml").deleteExisting()
        val warnings = mutableListOf<String>()
        messages = Messages(config, directory, warnings::add)
        repeat(2) {
            val fallback = messages.format(config.defaultLocale, "example.message", mapOf("first" to "one", "last" to "two"))
            assertEquals("{one} / two", fallback.text)
            assertEquals("zh-CN", fallback.actualLocale.tag)
        }
        assertEquals(1, warnings.count { it.contains("missing example.message") })
    }

    @Test
    fun `base keys and parameter contracts cannot be bypassed by a translated key`() = withSiteFixture { _, _, directory ->
        val config = SiteConfig.load(directory)
        directory.resolve("zh-CN.toml").writeText("[test]\nmessage = \"{page}\"\n")
        for (translated in listOf("message = \"{other}\"", "message = \"unclosed {page\"")) {
            directory.resolve("en-US.toml").writeText("[test]\n$translated\n")
            assertFailsWith<IllegalArgumentException> { Messages(config, directory) }
        }
        directory.resolve("en-US.toml").writeText("[test]\nextra = \"present only in English\"\n")
        val messages = Messages(config, directory)
        assertFailsWith<IllegalStateException> { messages.format(config.defaultLocale, "test.extra") }
        for (args in listOf(emptyMap(), mapOf("page" to "1", "extra" to "2")))
            assertFailsWith<IllegalArgumentException> { messages.format(config.defaultLocale, "test.message", args) }
        directory.resolve("zh-CN.toml").deleteExisting()
        assertFailsWith<IllegalArgumentException> { Messages(config, directory) }
    }
}
