package htmlgen.component.home_element

import htmlgen.page.ArticleContent
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import ActiveRecord
import kotlinx.datetime.LocalDateTime

class ActiveElement(
    val record: ActiveRecord
) : HomeElement {
    override fun DIV.show() {
        classes += "active_element"
        div {
            classes += "page_content"

            ArticleContent(record.markdownContent, record.sourcePath).apply { showPostContent() }
        }
    }

    override fun getDate(): LocalDateTime {
        return record.date
    }
}