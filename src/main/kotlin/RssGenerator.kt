import java.text.SimpleDateFormat
import java.util.Date

val FORMATTER = SimpleDateFormat("EEE, MMM dd yyyy")
private val TOML_DATETIME_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
private val TOML_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd")

fun parseTomlDateTime(dateString: String): Date =
    try {
        TOML_DATETIME_FORMAT.parse(dateString)
    } catch (_: Exception) {
        TOML_DATE_FORMAT.parse(dateString)
    }

fun genRssXmlString(pages: List<BlogRecord>, lastBuildDate: Date): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<rss version="2.0">""")
        appendLine("<channel>")
        appendLine("<title>Koiro's Cat Café</title>")
        appendLine("<link>https://blog.koiro.xyz</link>")
        appendLine("<description>这里是宏楼 Koiro 的个人博客。宏楼是人类、学生、平面设计爱好者、图形学爱好者和游戏开发者。</description>")
        appendLine("<language>zh-cn</language>")
        appendLine("<lastBuildDate>${FORMATTER.format(lastBuildDate)}</lastBuildDate>")

        pages.forEach { post ->
            appendLine("<item>")
            appendLine("<title>${post.header.title}</title>")
            appendLine("<link>https://blog.koiro.xyz/${post.serverPath}.html</link>")
            // appendLine("<guid>${post.}</guid>")
            appendLine("<pubDate>${FORMATTER.format(parseTomlDateTime(post.header.date))}</pubDate>")
            var desc = post.header.slug
            desc += " #${post.header.blogClass}"
            appendLine("<description><![CDATA[$desc]]></description>")
            appendLine("</item>")
        }

        appendLine("</channel>")
        appendLine("</rss>")
    }
