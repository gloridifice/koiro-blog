@file:OptIn(kotlin.time.ExperimentalTime::class)

package htmlgen.component

import content.BlogMetadata
import content.ResolvedContent
import htmlgen.ImageAssets
import htmlgen.formatToString
import htmlgen.preview
import htmlgen.component.home_element.BlogElement
import kotlinx.html.*
import routing.PageContext

fun FlowContent.largeBlogPostPreview(record: ResolvedContent, context: PageContext) {
    div { with(BlogElement(record, context)) { show() } }
}

fun FlowContent.blogPostPreview(record: ResolvedContent, context: PageContext) {
    a(context.routes.content(context.locale, record.bundle.key).serverPath, classes = "post_preview regular reveal blog" +
        if (record.fallback || record.variant.header.aiTranslation) " has-translation-marker" else "") {
        lang = record.variant.localeTag
        val metadata = record.bundle.metadata as BlogMetadata
        blogIcon(metadata)
        div("description") {
            h2 { +record.title }
            p("slug") { +record.variant.header.summary }
        }
        div("info") {
            p("date") { +record.date.formatToString() }
            p("type") { message(context, "categories.${metadata.blogClass}") }
        }
        translationMarker(record, context)
    }
}

fun FlowContent.devlogPostPreview(record: ResolvedContent, context: PageContext, images: ImageAssets) {
    a(context.routes.content(context.locale, record.bundle.key).serverPath, classes = "post_preview regular reveal devlog" +
        if (record.fallback || record.variant.header.aiTranslation) " has-translation-marker" else "") {
        lang = record.variant.localeTag
        record.preview(images)?.let { img(src = it.serverPath, alt = "") }
        div("description") { h2 { +record.title } }
        translationMarker(record, context)
    }
}
