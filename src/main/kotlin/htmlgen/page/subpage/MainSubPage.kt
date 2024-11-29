package htmlgen.page.subpage

import GlobalContext
import STATIC_PATH
import childPath
import htmlgen.dateDisplayWithoutYearString
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p
import java.nio.file.Path

class MainSubPage(val context: GlobalContext): SubPage() {
    override fun DIV.show() {
        div {
            + "top_gap_space"
        }
        div {
            classes += "elements"
            for (element in context.homeElements) {
                div {
                    classes += "element"
                    classes += "reveal"
                    div {
                        classes += "time"
                        p {
                            + dateDisplayWithoutYearString(element.getDate())
                        }
                    }
                    div {
                        classes += "content"
                        with(element) {
                            this@div.show()
                        }
                    }
                }
            }
        }
    }

    override fun getHtmlFilePath(): Path {
        return STATIC_PATH.childPath("home.html")
    }

    override fun displayName(): String {
        return "主页"
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("scroll_animation", "main_home_page")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }
}