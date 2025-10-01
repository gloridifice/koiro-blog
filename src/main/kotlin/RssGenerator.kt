import htmlgen.model.BlogPostPage
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
        appendLine("<title>Koiro's Cat Café</title>")
        appendLine("<link>https://blog.koiro.xyz</link>")
        appendLine("<description>这里是宏楼 Koiro 的个人博客。宏楼是人类、学生、平面设计爱好者、图形学爱好者和游戏开发者。</description>")
        appendLine("<language>zh-cn</language>")
        appendLine("<lastBuildDate>${FORMATTER.format(lastBuildDate)}</lastBuildDate>")

        pages.forEach { post ->
            appendLine("<item>")
            appendLine("<title>${post.getPlainTitle()}</title>")
            appendLine("<link>https://blog.koiro.xyz/${post.getStaticHtmlName()}.html</link>")
            appendLine("<guid>${post.uuid}</guid>")
            appendLine("<pubDate>${FORMATTER.format(post.publishedDate ?: post.lastEditedTimeDate)}</pubDate>")
            var desc = post.slug?.toNormalString() ?: "No description."
            if (post is BlogPostPage) {
                desc += " #${post.type.name}"
            }
            appendLine("<description><![CDATA[$desc]]></description>")
            appendLine("</item>")
        }

        appendLine("</channel>")
        appendLine("</rss>")
    }
