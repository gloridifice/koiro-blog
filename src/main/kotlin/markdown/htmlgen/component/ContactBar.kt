package markdown.htmlgen.component

import kotlinx.html.*
import markdown.htmlgen.SvgIcon
import markdown.htmlgen.unsafeSVG

class ContactBarItem(val svgIcon: SvgIcon, val name: String, val link: String) {

}

fun FlowContent.contactBar(contactItems: List<ContactBarItem>) {
    div {
        classes += "contact"
        h3 {
            +"Contact"
        }
        div {
            classes += "items"
            contactItems.forEach {
                div {
                    classes += "item"
                    onClick = "window.open('${it.link}');"
                    it.svgIcon.apply { showSvg() }
                }
            }
        }
    }
}
