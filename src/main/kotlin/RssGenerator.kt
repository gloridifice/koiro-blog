import htmlgen.model.PostPage
import htmlgen.toNormalString
import java.text.SimpleDateFormat
import java.util.Date

val FORMATTER = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z")

fun genRssXmlString(pages: List<PostPage>, lastBuildDate: Date): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<rss version="2.0">""")
        appendLine("<channel>")
        appendLine("<title>我的博客</title>")
        appendLine("<link>https://example.com</link>")
        appendLine("<description>我的 Kotlin 静态博客</description>")
        appendLine("<language>zh-cn</language>")
        appendLine("<lastBuildDate>${FORMATTER.format(lastBuildDate)}</lastBuildDate>")

        pages.forEach { post ->
            appendLine("<item>")
            appendLine("<title>${post.getPlainTitle()}</title>")
            appendLine("<link>https://blog.koiro.xyz/${post.getStaticHtmlName()}.html</link>")
            appendLine("<guid>${post.uuid}</guid>")
            appendLine("<pubDate>${FORMATTER.format(post.publishedDate ?: post.lastEditedTimeDate)}</pubDate>")
            appendLine("<description><![CDATA[${post.slug?.toNormalString() ?: "No description."}]]></description>")
            appendLine("</item>")
        }

        appendLine("</channel>")
        appendLine("</rss>")
    }
