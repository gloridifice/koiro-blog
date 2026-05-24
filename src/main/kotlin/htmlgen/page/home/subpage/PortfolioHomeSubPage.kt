package htmlgen.page.home.subpage

import htmlgen.SvgIcons
import htmlgen.component.devlogPostPreview
import kotlinx.html.*
import DevlogRecord
import PortfolioRecord
import kotlin.collections.forEach

class PortfolioHomeSubPage(val pages: Iterable<PortfolioRecord>, val devlogs: List<DevlogRecord>) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "projects"
            pages.forEach { project ->
                val relatedDevLogs = devlogs.filter {
                    it.header.workName == project.header.workName
                }

                div {
                    classes += "project"

                    div {
                        classes += "info"
                        div {
                            classes += "headings"
                            h3 {
                                classes += "title"
                                +project.header.title
                            }

                            a {
                                classes += arrayOf("button", "row")

                                href = project.header.url
                                target = "_blank"
                                SvgIcons.EXTERNAL_LINK.apply { showSvg() }
                                div {
                                    + "前往主页"
                                }
                            }
                        }
                        p {
                            +project.header.description
                        }
                    }


                    project.previewImagePath.let {
                        img {
                            src = it.serverPath
                        }
                    }


                    div {
                        classes += "previews"
                        relatedDevLogs.forEach {
                            devlogPostPreview(it)
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