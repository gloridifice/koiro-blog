package markdown.htmlgen.component.home_element

import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import markdown.ActiveRecord
import markdown.htmlgen.page.ArticleContent

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

    override fun getDate(): String {
        return record.header.date
    }
}