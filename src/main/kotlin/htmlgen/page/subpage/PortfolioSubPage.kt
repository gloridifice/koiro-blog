package htmlgen.page.subpage

import GlobalContext
import STATIC_PATH
import childPath
import htmlgen.SVGIcons
import htmlgen.component.devLogPostPreview
import htmlgen.richTexts
import htmlgen.unsafeSVG
import kotlinx.html.*
import serverPathString
import java.nio.file.Path

class PortfolioSubPage(val context: GlobalContext) : SubPage() {
    override fun DIV.show() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "projects"
            context.portfolioDatabaseData.publishedPages.forEach { project ->
                val relatedDevLogs = context.devLogDatabaseData.publishedPages.filter {
                    it.work?.name == project.projectID
                }

                div {
                    classes += "project"
                    project.previewImagePath?.let {
                        img {
                            src = it.serverPathString()
                        }
                    }

                    div {
                        classes += "info"
                        h3 {
                            classes += "title"
                            val text = project.title.first().plainText!!
                            +text
                        }
                        p {
                            richTexts(project.desc)
                        }
                    }

                    project.url?.let {
                        a {
                            classes += arrayOf("button", "row")

                            href = project.url
                            target = "_blank"
                            unsafeSVG(SVGIcons.EXTERNAL_LINK)
                            div {
                                + "主页"
                            }
                        }
                    }

                    div {
                        classes += "previews"
                        relatedDevLogs.forEach {
                            devLogPostPreview(it)
                        }
                    }
                }
            }
        }
    }

    override fun getHtmlFilePath(): Path {
        return STATIC_PATH.childPath("portfolio.html")
    }

    override fun displayName(): String {
        return "项目"
    }

    override fun icon(): String? {
        return SVGIcons.PROJECTS
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("post_preview", "subpage/portfolio", "scroll_animation")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }
}