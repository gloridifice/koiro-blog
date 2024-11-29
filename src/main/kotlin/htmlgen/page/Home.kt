package htmlgen.page

import GlobalContext
import HOME_SELECTIONS
import htmlgen.*
import htmlgen.component.*
import htmlgen.page.subpage.SubPage
import kotlinx.html.*

fun HTML.homePage(subPage: SubPage, show: DIV.() -> Unit) {
    layout(
        siteTitle = "Koiro's Cat Café",
        cssNames = arrayOf("home") + subPage.getCssNames(),
        jsNames = subPage.getJsNames(),
        headFont = "你好",
    ) {
        div {
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
                        unsafeSVG(SVGIcons.ACCOUNT_CIRCLE);
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
                    naviWithHighlightedItem(HOME_SELECTIONS, subPage)
                }
            }
        }
        div {
            classes += "contents_wrapper"
            div {
                classes += "contents"

                show()

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
            div {
                onClick = "window.open('${it.link}')"
                classes += "outside_page_item"

                unsafeSVG(SVGIcons.EXTERNAL_LINK)

                div {
                    classes += "desc"
                    +it.desc
                }
            }
        }
    }
}

fun FlowContent.devLogPreviews(context: GlobalContext) {
    div {
        classes += "dev_log_previews_wrapper"
        div {
            classes += "reveal"
            h3 {
                +"开发日志"
            }
        }
        div {
            classes += "dev_log_previews"
            val sort = context.devLogDatabaseData.publishedPages.map { it }.sortedBy {
                it.createdTimeDate
            }.reversed()
            for (i in 0..<6) {
                sort.getOrNull(i)?.let {
                    devLogPostPreview(it)
                }
            }
        }
    }
}

