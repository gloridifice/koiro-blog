package htmlgen.page

import DevlogRecord
import java.nio.file.Path


class DevlogArticlePage(val record: DevlogRecord, val markdownText: String, val filePath: Path) : ArticlePage(
    record.header.title, record.date,
    {
        with(ArticleContent(markdownText, filePath)) {
            showPostContent()
        }
    }
)