package htmlgen.component

import BlogHeader
import htmlgen.LucideIcons
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span

fun FlowContent.blogIcon(header: BlogHeader) {
    div(classes = "blog_icon") {
        attributes["aria-hidden"] = "true"
        if (header.icon == null && !header.emoji.isNullOrBlank()) {
            span(classes = "blog_icon_emoji") { +header.emoji }
        } else {
            with(LucideIcons.load(header.icon ?: "file-text")) { showSvg() }
        }
    }
}
