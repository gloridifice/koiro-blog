package markdown.htmlgen.page

import FORMATTER
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.script
import markdown.BlogHeader
import markdown.KoiroCatCafe
import markdown.htmlgen.component.Catalogue

class BlogHtml(val header: BlogHeader, val markdownText: String) {
    fun HTML.show() {
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
                                href = item.serverPath.serverPath()
                                item.svgIcon.apply { showSvg() }
                            }
                        }
                    }
                    div {
                        classes += "page_description"
                        h1 {
                            classes += "title"
                            +header.title
                        }
                        hr { }
                        div {
                            classes += "sub_info"
                            p {
                                classes += "date"
                                +FORMATTER.format(header.date)
                            }
                        }
                    }
                    div {
                        classes += "page_content"

                        ArticleContent(markdownText).apply { collectedData = showPostContent() }
                    }
                }
                div {
                    classes += "sidebar_wrapper_right"
                    classes += "sidebar_wrapper"
                    Catalogue(collectedData!!.headingInfos).apply { show() }
                }
            }
        }
    }

}