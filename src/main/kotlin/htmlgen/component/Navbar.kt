package htmlgen.component

import htmlgen.SVGIcons
import kotlinx.html.*
import htmlgen.unsafeSVG

class NaviItem(val title: String, val link: String, val iconSVGString: String, val isLinkOutside: Boolean = false) {

}

fun FlowContent.naviWithHighlightedItem(items: List<NaviItem>, highlighted: NaviItem) {
    div {
        classes += "navi"
        classes += "row"
        for (item in items) {
            a {
                classes += "navi_link"
                if (item == highlighted) {
                   classes += "highlighted"
                }
                href = item.link
                +item.title
            }
        }
    }
}

//todo implement link
fun FlowContent.navbar(items: List<NaviItem>) {
    div {
        classes += "header"
        div {
            classes += "navbar"
            for (item in items) {
                navbarItem(item)
            }
            div {
                classes += "dark_mode"
                classes += "switch_visual_mode_button"
                unsafeSVG(
                    SVGIcons.SUN
                )
            }
        }
    }
}

private fun FlowContent.navbarItem(item: NaviItem) {
    div {
        classes += "navbar_item"
        onClick = if (item.isLinkOutside) {
            classes += "outside"
            "window.open('${item.link}')"
        } else {
            "window.location='${item.link}'"
        }
        unsafeSVG(item.iconSVGString)
        div {
            classes += "title"
            +item.title
        }
    }
}
