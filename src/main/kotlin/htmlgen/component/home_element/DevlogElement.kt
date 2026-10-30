package htmlgen.component.home_element

import content.ResolvedContent
import htmlgen.ImageAssets
import htmlgen.contentImage
import htmlgen.component.translationMarker
import htmlgen.preview
import kotlinx.html.*
import routing.PageContext

class DevlogElement(val record: ResolvedContent, private val images: ImageAssets,
    private val context: PageContext) : HomeElement {
    override fun DIV.show(imageLoading: ImgLoading) {
        a(context.routes.content(context.locale, record.bundle.key).serverPath, classes = "post_preview devlog large" +
            if (record.fallback || record.variant.header.aiTranslation) " has-translation-marker" else "") {
            lang = record.variant.localeTag
            record.preview(images)?.let { path -> div("preview") { img { contentImage(path, images, imageLoading) } } }
            div("description") { h2("title") { +record.title } }
            translationMarker(record, context)
        }
    }
    override fun getDate() = record.date
}
