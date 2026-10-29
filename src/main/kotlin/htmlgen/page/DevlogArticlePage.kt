package htmlgen.page

import DevlogRecord
import htmlgen.ImageAssets
import java.nio.file.Path


class DevlogArticlePage(val record: DevlogRecord, val markdownText: String, val filePath: Path, images: ImageAssets) : ArticlePage(
    record.header.title, record.date,
    {
        with(ArticleContent(markdownText, filePath, images)) {
            showPostContent()
        }
    }
)