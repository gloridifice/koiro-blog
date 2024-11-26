package htmlgen.model.home

import htmlgen.component.notionBlocks
import htmlgen.page.PostContext
import kotlinx.html.DIV
import kotlinx.html.classes
import htmlgen.model.PageData
import kotlinx.html.div
import java.util.*

class ActiveElement(
    val pageData: PageData
) : HomeElement {
    override fun DIV.show() {
        classes += "active_element"
        div {
            classes += "page_content"
            pageData.dataBlocks?.let { notionBlocks(it, pageData, PostContext(), true) }
        }
    }

    override fun getDate(): Date {
        return pageData.createdTimeDate
    }
}