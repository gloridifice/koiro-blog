package htmlgen.page

import BlogHeader
import BlogRecord
import java.nio.file.Path

class BlogArticlePage(val header: BlogRecord, val markdownText: String, val filePath: Path) : ArticlePage(
    header.header.title, header.date,
    {
        with(ArticleContent(markdownText, filePath)) {
            showPostContent()
        }
    }
)