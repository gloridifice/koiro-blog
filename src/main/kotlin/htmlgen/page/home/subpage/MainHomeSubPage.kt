package htmlgen.page.home.subpage

import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p
import htmlgen.component.home_element.HomeElement
import htmlgen.component.HomePagination
import htmlgen.component.homePagination
import kotlinx.html.ImgLoading
import htmlgen.fmtThisYear

class MainHomeSubPage(
    private val elements: List<HomeElement>,
    private val pagination: HomePagination,
) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "elements"
            for ((index, element) in elements.withIndex()) {
                div {
                    classes += "element"
                    classes += "reveal"
                    div {
                        classes += "time"
                        p {
                            +fmtThisYear.format(element.getDate())
                        }
                    }
                    div {
                        classes += "content"
                        // Keep the leading cards eager; defer images further down this page.
                        element.apply { show(if (index < 2) ImgLoading.eager else ImgLoading.lazy) }
                    }
                }
            }
        }
        homePagination(pagination)
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("scroll_animation", "subpage/main", "post_preview", "page_content", "home_pagination")
    }
}