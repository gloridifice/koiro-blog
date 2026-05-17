package markdown.htmlgen.page.home.subpage

import kotlinx.html.*
import markdown.DevlogRecord
import markdown.Portfolio
import markdown.htmlgen.SvgIcons
import markdown.htmlgen.component.devLogPostPreview
import markdown.htmlgen.unsafeSVG

class PortfolioHomeSubPage(val pages: Iterable<Portfolio>, val devlogs: List<DevlogRecord>) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "projects"
            pages.forEach { project ->
                val relatedDevLogs = devlogs.filter {
                    it.header.workName == project.workName
                }

                div {
                    classes += "project"
                    project.previewImage.let {
                        img {
                            //todo
                            //src = it.serverPathString()
                        }
                    }

                    div {
                        classes += "info"
                        h3 {
                            classes += "title"
                            +project.title
                        }
                        p {
                            +project.description
                        }
                    }

                    a {
                        classes += arrayOf("button", "row")

                        href = project.url
                        target = "_blank"
                        SvgIcons.EXTERNAL_LINK.apply { showSvg() }
                        div {
                            + "前往主页"
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


    override fun getCssNames(): Array<String> {
        return arrayOf("post_preview", "subpage/portfolio", "scroll_animation")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }
}