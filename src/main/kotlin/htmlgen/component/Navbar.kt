package htmlgen.component

import htmlgen.SVGIcons
import htmlgen.page.HomeSelection
import kotlinx.html.*
import htmlgen.unsafeSVG

fun FlowContent.naviWithHighlightedItem(items: Array<HomeSelection>, highlighted: HomeSelection) {
    div {
        classes += "navi"
        classes += "row"
        for (subpage in items) {
            a {
                classes += arrayOf("navi_link", "button")
                if (subpage == highlighted) {
                    classes += "highlighted"
                }

                href = subpage.url()
                subpage.icon()?.let { unsafeSVG(it) }
                +subpage.displayName()
            }
        }
    }
}