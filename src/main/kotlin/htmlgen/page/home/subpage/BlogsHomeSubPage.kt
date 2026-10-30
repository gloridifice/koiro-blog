package htmlgen.page.home.subpage

import content.BlogMetadata
import content.ResolvedContent
import htmlgen.component.blogPostPreview
import htmlgen.component.largeBlogPostPreview
import htmlgen.component.message
import kotlinx.html.*
import routing.PageContext

class BlogsHomeSubPage(private val blogs: List<ResolvedContent>, private val context: PageContext) : HomeSubPage() {
    override fun DIV.showSubPage() {
        div("top_gap_space")
        val groups = blogs.groupBy { (it.bundle.metadata as BlogMetadata).blogClass }.toSortedMap()
        div("post_previews_wrapper") {
            blogs.firstOrNull()?.let { latest -> div {
                p { message(context, "site.latest") }; largeBlogPostPreview(latest, context)
            } }
            div {
                p { message(context, "site.categories") }
                div("post_type_buttons") {
                    for ((i, category) in groups.keys.withIndex()) button(type = ButtonType.button,
                        classes = "post_type_button button" + if (i == 0) " is_selected" else "") {
                        id = "${category.lowercase()}_type_button"
                        message(context, "categories.$category")
                    }
                }
                for ((i, entry) in groups.entries.withIndex()) div("post_type_previews" + if (i != 0) " hidden" else "") {
                    id = "${entry.key.lowercase()}_type_previews"
                    entry.value.forEach { blogPostPreview(it, context) }
                }
            }
        }
    }
    override fun getCssNames() = arrayOf("subpage/blogs", "post_preview", "scroll_animation")
}
