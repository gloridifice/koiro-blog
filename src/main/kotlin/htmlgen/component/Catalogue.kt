package htmlgen.component

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.ul
import kotlin.collections.plus


class Catalogue(
    val headingList: List<HeadingInfo>,
) {
    enum class HeadingType {
        H1, H2, H3
    }

    class HeadingInfo(val type: HeadingType, var content: String, val id: String)

    fun FlowContent.show() {
        div {
            classes += "catalogue"
            ul {
                for (index in headingList.indices) {
                    val it = headingList[index]

                    li {
                        classes += when (it.type) {
                            HeadingType.H1 -> "h1"
                            HeadingType.H2 -> "h2"
                            HeadingType.H3 -> "h3"
                        }

                        a {
                            href = "#${it.id}"
                            +it.content
                        }
                    }
                }
            }
        }
    }
}