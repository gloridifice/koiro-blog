package markdown.htmlgen.component

import kotlinx.html.*
import markdown.htmlgen.page.home.HomeSubPageInfo

fun FlowContent.naviWithHighlightedItem(
    items: Iterable<HomeSubPageInfo>,
    highlighted: HomeSubPageInfo
) {
    div {
        classes += "navi"
        classes += "row"
        for (subpage in items) {
            a {
                classes += arrayOf("navi_link", "button")
                if (subpage == highlighted) {
                    classes += "highlighted"
                }

                href = subpage.serverPath.serverPath

                subpage.svgIcon.apply { showSvg() }
                +subpage.name
            }
        }
    }
}