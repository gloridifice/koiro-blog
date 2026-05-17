package markdown.htmlgen.page

import kotlinx.html.HTML
import markdown.DevlogRecord

class DevlogArticle(val record: DevlogRecord) {
    fun HTML.page() {
        layout(
            siteTitle = record.header.title,
            jsNames = arrayOf("highlightjs/highlight"),
            cssNames = arrayOf("post", "color_scheme_v2.dark_mode", "highlightjs/github-dark")
        ) {
            //todo
        }
    }
}