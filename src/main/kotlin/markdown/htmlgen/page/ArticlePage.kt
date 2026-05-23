package markdown.htmlgen.page

import FORMATTER
import kotlinx.datetime.LocalDateTime
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.script
import markdown.KoiroCatCafe
import markdown.htmlgen.component.Catalogue
import java.util.Date
import kotlin.collections.plus

open class ArticlePage(
    val title: String,
    val date: Date,
    val content: FlowContent.() -> ArticleContent.CollectedData?
) {

    fun HTML.showPage() {
        var collectedData: ArticleContent.CollectedData? = null
        layout(
            siteTitle = "hello",
            jsNames = arrayOf("highlightjs/highlight"),
            cssNames = arrayOf("post", "color_scheme_v2.dark_mode", "highlightjs/github-dark")
        ) {
            script { +"hljs.highlightAll();" }
            div {
                classes += "post"
                div {
                    classes += "sidebar_wrapper_left"
                    classes += "sidebar_wrapper"
                    navi() //侧边导航栏
                }
                div {
                    classes += "contents"
                    div { classes += "top_gap_space" }
                    div {
                        classes += "header"
                        // 顶部导航栏
                        for (item in KoiroCatCafe.homeSubPageInfos) {
                            a {
                                classes += arrayOf("navi_link", "button")
                                href = item.serverPath.serverPath
                                item.svgIcon.apply { showSvg() }
                            }
                        }
                    }
                    div {
                        classes += "page_description"
                        h1 {
                            classes += "title"
                            +title
                        }
                        hr { }
                        div {
                            classes += "sub_info"
                            p {
                                classes += "date"
                                +FORMATTER.format(date)
                            }
                        }
                    }
                    div {
                        classes += "page_content"

                        collectedData = content()
                    }
                }
                div {
                    classes += "sidebar_wrapper_right"
                    classes += "sidebar_wrapper"
                    if (collectedData != null)
                        Catalogue(collectedData!!.headingInfos).apply { show() }
                }
            }
        }
    }
}