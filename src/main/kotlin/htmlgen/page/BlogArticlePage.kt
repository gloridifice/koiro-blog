package htmlgen.page

import htmlgen.ImageAssets
import BlogRecord
import java.nio.file.Path

class BlogArticlePage(val header: BlogRecord, val markdownText: String, val filePath: Path, images: ImageAssets) : ArticlePage(
    header.header.title, header.date,
    {
        with(ArticleContent(markdownText, filePath, images)) {
            showPostContent()
        }
    }
)