import org.jsoup.Jsoup
import kotlin.io.path.*
import kotlin.test.*

class FriendLinksTest {
    @Test
    fun `friend links keep configured order and select complete translations with literal escaped text`() = withSiteFixture { data, output, locales ->
        locales.resolve("locales.toml").appendText("\n[locales.ja-JP]\nname = \"日本語\"\nlanguage = \"ja\"\nmatch-default = true\n")
        data.resolve("friends.toml").writeText("""
            [[friends]]
            id = "zebra"
            name = "默认专名"
            url = "https://example.com/zebra?a=1&b=2"
            [friends.translations.zh-CN]
            description = "中文简介"
            [friends.translations.en-US]
            name = "English <&>"
            description = "English {name} <script>alert(1)</script>"

            [[friends]]
            id = "alpha"
            name = "共用专名"
            url = "http://example.com/alpha"
            [friends.translations.zh-CN]
            name = "中文别名"
            description = "仅中文简介"
            [friends.translations.ja-JP]
            description = "日本語の紹介"
        """.trimIndent())
        KoiroCatCafe(data, output.toString(), i18nPath = locales).generateAll()

        data class Expected(val names: List<String>, val descriptions: List<String>, val languages: List<String>, val nameLanguages: List<String>)
        val englishDescription = "English {name} <script>alert(1)</script>"
        for ((locale, expected) in mapOf(
            "zh-CN" to Expected(listOf("默认专名", "中文别名"), listOf("中文简介", "仅中文简介"), listOf("zh-CN", "zh-CN"), listOf("", "zh-CN")),
            "en-US" to Expected(listOf("English <&>", "中文别名"), listOf(englishDescription, "仅中文简介"), listOf("en-US", "zh-CN"), listOf("en-US", "zh-CN")),
            "ja-JP" to Expected(listOf("English <&>", "共用专名"), listOf(englishDescription, "日本語の紹介"), listOf("en-US", "ja-JP"), listOf("en-US", "")),
        )) {
            val page = Jsoup.parse(output.resolve("$locale/about.html").readText())
            val links = page.select(".friends .friend_link")
            assertEquals(listOf("https://example.com/zebra?a=1&b=2", "http://example.com/alpha"), links.map { it.attr("href") }, locale)
            assertEquals(expected.names, links.select(".icon > div").map { it.text() }, locale)
            assertEquals(expected.nameLanguages, links.select(".icon > div").map { it.attr("lang") }, locale)
            assertEquals(expected.descriptions, links.select(".desc").map { it.text() }, locale)
            assertEquals(expected.languages, links.select(".desc").map { it.attr("lang") }, locale)
            assertTrue(links.select("script").isEmpty(), "Descriptions must be escaped text")
            assertTrue(links.all { it.attr("target") == "_blank" && it.attr("rel") == "noopener noreferrer" })
            assertTrue(page.select(".translation-marker, .translation-line").isEmpty(), "Friend links do not use article notices")
        }
    }

    @Test
    fun `friend edits reorder and remove links on rebuild without invalidating articles`() = withSiteFixture { data, output, locales ->
        writeBundle(data, "blogs/post", "id = \"post\"\npublished = true\nclass = \"Dev\"\ndate = \"2025-01-01\"", "Article body")
        val config = data.resolve("friends.toml")
        config.writeText(friend("first") + friend("second"))
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        val article = output.resolve("en-US/pages/blogs/post.html")
        val articleTime = article.getLastModifiedTime()
        for ((configuration, ids) in listOf(
            (friend("second", "Updated") + friend("first")) to listOf("second", "first"),
            friend("second", "Updated") to listOf("second"),
            "friends = []\n" to emptyList(),
        )) {
            config.writeText(configuration)
            site.generateAll()
            for (locale in listOf("en-US", "zh-CN")) {
                val page = Jsoup.parse(output.resolve("$locale/about.html").readText())
                val links = page.select(".friends .friend_link")
                assertEquals(ids.map { "https://example.com/$it" }, links.map { it.attr("href") })
                assertEquals(ids.isNotEmpty(), page.select(".friends").isNotEmpty())
                if (ids.isNotEmpty()) assertEquals("Updated", links.first()!!.selectFirst(".desc")!!.text())
            }
            assertEquals(articleTime, article.getLastModifiedTime(), "Friend configuration must not invalidate article atoms")
        }
    }

    @Test
    fun `invalid friend configuration fails with source context before replacing generated pages`() = withSiteFixture { data, output, locales ->
        val file = data.resolve("friends.toml")
        val valid = friend("sample")
        file.writeText(valid)
        val site = KoiroCatCafe(data, output.toString(), i18nPath = locales)
        site.generateAll()
        val about = output.resolve("en-US/about.html").readText()
        val manifest = output.resolve(".generated-site-files.json").readText()
        data class Invalid(val text: String, val field: String, val entry: String? = "sample")
        for (case in listOf(
            Invalid(valid.replace("id = \"sample\"", "id = \"bad-id\""), "id", "bad-id"),
            Invalid(valid.replace("id = \"sample\"\n", ""), "id", null),
            Invalid(valid + valid, "Duplicate", "sample"),
            Invalid(valid.replace("name = \"sample\"", "name = \" \""), "name"),
            Invalid(valid.replace("name = \"sample\"", "name = false"), "name"),
            Invalid(valid.replace("url = \"https://example.com/sample\"\n", ""), "url"),
            Invalid(valid.replace("https://example.com/sample", "javascript:alert(1)"), "url"),
            Invalid(valid.replace("https://example.com/sample", "//example.com/sample"), "url"),
            Invalid(valid.replace("https://example.com/sample", "https:///sample"), "url"),
            Invalid(valid.replace("https://example.com/sample", "https://exa mple.com"), "url"),
            Invalid(valid.replace("https://example.com/sample", "ftp://example.com/sample"), "url"),
            Invalid(valid.replace("[friends.translations.zh-CN]", "[friends.translations.en-US]"), "zh-CN"),
            Invalid(valid.replace("zh-CN", "zh_CN"), "zh_CN"),
            Invalid(valid + "\n[friends.translations.fr-FR]\ndescription = \"French\"\n", "fr-FR"),
            Invalid(valid + "\n[friends.translations.en-US]\nname = \"English only\"\n", "description"),
            Invalid(valid.replace("description = \"sample\"", "description = \" \""), "description"),
            Invalid(valid.replace("description = \"sample\"", "description = 42"), "description"),
            Invalid(valid + "\nname = \"\"\n", "name"),
            Invalid(valid.replace("url =", "extra = true\nurl ="), "extra"),
            Invalid(valid + "\nextra = \"unknown\"\n", "extra"),
            Invalid(valid + "\ndescription = \"duplicate\"\n", "description"),
            Invalid(valid + "\n[friends.translations.zh-CN]\nname = \"Duplicate table\"\n", "zh-CN"),
            Invalid("extra = true\n$valid", "extra", null),
            Invalid("friends = false\n", "friends", null),
            Invalid("friends = [1]\n", "friends", null),
            Invalid("# No friends key\n", "friends", null),
            Invalid("[[friends]\n", "", null),
        )) {
            file.writeText(case.text)
            val failure = assertFailsWith<IllegalArgumentException>(case.toString()) { site.generateAll() }
            val diagnostic = failure.message.orEmpty()
            assertTrue(diagnostic.contains(file.toString()), diagnostic)
            assertTrue(diagnostic.contains(case.field), diagnostic)
            case.entry?.let { assertTrue(diagnostic.contains(it), diagnostic) }
            assertEquals(about, output.resolve("en-US/about.html").readText())
            assertEquals(manifest, output.resolve(".generated-site-files.json").readText())
        }
        file.deleteExisting()
        val missing = assertFailsWith<IllegalArgumentException> { site.generateAll() }
        assertTrue(missing.message.orEmpty().contains(file.toString()))
        assertEquals(about, output.resolve("en-US/about.html").readText())
    }

    private fun friend(id: String, description: String = id) = """
        [[friends]]
        id = "$id"
        name = "$id"
        url = "https://example.com/$id"
        [friends.translations.zh-CN]
        description = "$description"

    """.trimIndent() + "\n"
}
