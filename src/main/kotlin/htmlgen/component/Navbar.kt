package htmlgen.component

import htmlgen.SVGIcons
import kotlinx.html.*
import htmlgen.unsafeSVG

class NavbarItem(val title: String, val link: String, val iconSVGString: String, val isLinkOutside: Boolean = false) {

}

//todo implement link
fun FlowContent.navbar(items: List<NavbarItem>) {
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

private fun FlowContent.navbarItem(item: NavbarItem) {
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
