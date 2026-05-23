package markdown.htmlgen.page

import parseTomlDateTime
import markdown.BlogHeader
import java.nio.file.Path

class BlogArticlePage(val header: BlogHeader, val markdownText: String, val filePath: Path) : ArticlePage(
    header.title, parseTomlDateTime(header.date),
    {
        with(ArticleContent(markdownText, filePath)) {
            showPostContent()
        }
    }
)