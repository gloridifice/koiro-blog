package htmlgen.page

import htmlgen.page.home.navigation
import kotlinx.html.*
import routing.PageContext

fun FlowContent.navi(context: PageContext, trailing: (DIV.() -> Unit)? = null) {
    div("navi") {
        for (item in context.navigation()) a(item.serverPath.serverPath, classes = "navi_link button") {
            attributes["aria-label"] = item.name
            item.svgIcon.apply { showSvg() }
        }
        trailing?.invoke(this)
    }
}
