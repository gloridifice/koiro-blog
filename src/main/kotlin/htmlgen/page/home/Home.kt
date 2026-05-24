package htmlgen.page.home

import htmlgen.SvgIcons
import htmlgen.asLoc
import htmlgen.component.naviWithHighlightedItem
import htmlgen.component.touchableAvatar
import htmlgen.page.layout
import htmlgen.resourcesServerPath
import kotlinx.html.*
import KoiroCatCafe.Companion.homeSubPageInfos

fun HTML.homePage(subPage: HomeSubPageInfo) {
    layout(
        siteTitle = "Koiro's Cat Café",
        cssNames = arrayOf("home") + subPage.page!!.getCssNames(),
        jsNames = subPage.page!!.getJsNames(),
        headFont = "你好",
    ) {
        div {
            // ========== Sidebar =========
            classes += "about"
            div {
                classes += "up"
                img {
                    src = "/assets/resources/about_icon.png"
                }
                outSidePageButtons(
                    arrayOf(
                        OutSidePageItem("itch.io", "https://gloridifice.itch.io/", "一些游戏开发作品"),
                        OutSidePageItem(
                            "Source",
                            "https://github.com/gloridifice/kotlin-notion-blog",
                            "博客仓库"
                        )
                    )
                )
                div {
                    classes += "introduction"
                    div {
                        classes += "icon"
                        touchableAvatar(resourcesServerPath("doiro.png".asLoc()))
//                        unsafeSVG(SVGIcons.ACCOUNT_CIRCLE);
                    }
                    div {
                        classes += "texts"
                        h2 {
                            +"这里是宏楼的猫咖！"
                        }
                        p {
                            +"人类、学生、平面设计爱好者、图形学爱好者和游戏开发者。"
                        }
                    }

                }
            }
            div {
                classes += "down"
                div {
                    classes += "navi"
                    naviWithHighlightedItem(homeSubPageInfos, subPage)
                }
            }
        }
        div {
            classes += "contents_wrapper"
            div {
                classes += "contents"
                subPage.page!!.apply { showSubPage() }
                footer()
            }
        }
    }
}

data class OutSidePageItem(val name: String, val link: String, val desc: String)

fun FlowContent.outSidePageButtons(items: Array<OutSidePageItem>) {
    div {
        classes += "outside_pages"
        items.forEach {
            a {
                href = it.link
                target = "_blank"
                classes += arrayOf("outside_page_item", "button", "row")
                SvgIcons.EXTERNAL_LINK.apply { showSvg() }
                div {
                    classes += "desc"
                    +it.desc
                }
            }
        }
    }
}


