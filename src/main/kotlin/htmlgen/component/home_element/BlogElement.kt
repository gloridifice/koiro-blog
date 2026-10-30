package htmlgen.component.home_element

import content.BlogMetadata
import content.ResolvedContent
import htmlgen.component.blogIcon
import htmlgen.component.translationMarker
import kotlinx.html.*
import routing.PageContext

class BlogElement(val record: ResolvedContent, private val context: PageContext) : HomeElement {
    override fun DIV.show(imageLoading: ImgLoading) {
        a(context.routes.content(context.locale, record.bundle.key).serverPath, classes = "post_preview blog large" +
            if (record.fallback || record.variant.header.aiTranslation) " has-translation-marker" else "") {
            lang = record.variant.localeTag
            div("description") {
                blogIcon(record.bundle.metadata as BlogMetadata)
                div("title") { h2 { +record.title } }
                p("slug") { +record.variant.header.summary }
            }
            translationMarker(record, context)
        }
    }
    override fun getDate() = record.date
}
