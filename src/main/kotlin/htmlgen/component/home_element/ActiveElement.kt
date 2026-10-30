package htmlgen.component.home_element

import htmlgen.page.ArticleContent
import htmlgen.ImageAssets
import kotlinx.html.DIV
import kotlinx.html.ImgLoading
import kotlinx.html.classes
import kotlinx.html.div
import ActiveRecord
import kotlinx.datetime.LocalDateTime

class ActiveElement(
    val record: ActiveRecord,
    private val images: ImageAssets
) : HomeElement {
    override fun DIV.show(imageLoading: ImgLoading) {
        classes += "active_element"
        div {
            classes += "page_content"

            ArticleContent(record.markdownContent, record.sourcePath, images, imageLoading).apply { showPostContent() }
        }
    }

    override fun getDate(): LocalDateTime {
        return record.date
    }
}