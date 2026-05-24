package htmlgen.page.home.subpage

import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p
import htmlgen.component.home_element.HomeElement
import htmlgen.dateDisplayWithoutYearString

class MainHomeSubPage(val elements: Iterable<HomeElement>) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "elements"
            for (element in elements) {
                div {
                    classes += "element"
                    classes += "reveal"
                    div {
                        classes += "time"
                        p {
                            +dateDisplayWithoutYearString(element.getDate())
                        }
                    }
                    div {
                        classes += "content"
                        element.apply { show() }
                    }
                }
            }
        }
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("scroll_animation", "subpage/main", "post_preview", "page_content")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }
}