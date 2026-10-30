package htmlgen.page.home.subpage

import content.*
import htmlgen.ImageAssets
import htmlgen.SvgIcons
import htmlgen.preview
import htmlgen.component.devlogPostPreview
import htmlgen.component.message
import htmlgen.component.translationLines
import kotlinx.html.*
import routing.PageContext

class PortfolioHomeSubPage(private val projects: List<ResolvedContent>, private val devlogs: List<ResolvedContent>,
    private val context: PageContext, private val images: ImageAssets) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div("top_gap_space")
        div("projects") {
            for (project in projects) {
                val meta = project.bundle.metadata as PortfolioMetadata
                div("project") {
                    id = "project-${project.bundle.key.id}"
                    div("info") {
                        div("headings") {
                            h3("title") { lang = project.variant.localeTag; +project.title }
                            a(meta.url, target = "_blank", classes = "button row") {
                                rel = "noopener noreferrer"; with(SvgIcons.EXTERNAL_LINK) { showSvg() }
                                div { message(context, "site.visitProject") }
                            }
                        }
                        p { lang = project.variant.localeTag; +project.variant.header.description.orEmpty() }
                        translationLines(project, context, "project")
                    }
                    project.preview(images)?.let { img(src = it.serverPath, alt = "") }
                    div("previews") {
                        devlogs.filter { (it.bundle.metadata as DevlogMetadata).work == meta.project }
                            .forEach { devlogPostPreview(it, context, images) }
                    }
                }
            }
        }
    }
    override fun getCssNames() = arrayOf("post_preview", "subpage/portfolio", "scroll_animation")
}
