package markdown.htmlgen.page

import markdown.DevlogHeader
import parseTomlDateTime
import java.nio.file.Path


class DevlogArticlePage(val header: DevlogHeader, val markdownText: String, val filePath: Path) : ArticlePage(
    header.title, parseTomlDateTime(header.date),
    {
        with(ArticleContent(markdownText, filePath)) {
            showPostContent()
        }
    }
)