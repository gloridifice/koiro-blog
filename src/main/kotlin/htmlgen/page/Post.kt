package htmlgen.page

import kotlinx.html.*
import KoiroCatCafe

fun FlowContent.navi() {
    div {
        classes += "navi"
        for (item in KoiroCatCafe.homeSubPageInfos) {
            a {
                classes += arrayOf("navi_link", "button")
                href = item.serverPath.serverPath
                item.svgIcon.apply { showSvg() }
            }
        }
    }
}






