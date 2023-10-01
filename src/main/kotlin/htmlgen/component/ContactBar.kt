package htmlgen.component

import kotlinx.html.*
import htmlgen.unsafeSVG

class ContactBarItem(val svgStr: String, val name: String, val link: String) {

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
                    unsafeSVG(it.svgStr)
                }
            }
        }
    }
}
