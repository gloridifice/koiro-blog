package htmlgen.model.home

import htmlgen.component.notionBlocks
import htmlgen.page.PostContext
import kotlinx.html.DIV
import kotlinx.html.classes
import notiondata.PageData
import java.util.*

class ActiveElement(
    val pageData: PageData
) : HomeElement {
    override fun DIV.show() {
        classes += "active_section"
        pageData.dataBlocks?.let { notionBlocks(it, pageData, PostContext()) }
    }

    override fun getDate(): Date {
        return pageData.createdTimeDate
    }
}