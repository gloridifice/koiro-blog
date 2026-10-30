package htmlgen.component.home_element

import content.ResolvedContent
import htmlgen.ImageAssets
import htmlgen.component.translationLines
import htmlgen.page.ArticleContent
import kotlinx.html.*
import routing.PageContext

class ActiveElement(val record: ResolvedContent, private val images: ImageAssets,
    private val context: PageContext) : HomeElement {
    override fun DIV.show(imageLoading: ImgLoading) {
        classes += "active_element"
        id = "active-${record.bundle.key.id}"
        div("page_content") {
            lang = record.variant.localeTag
            with(ArticleContent(record.variant.markdown, record.variant.sourcePath, images, imageLoading,
                linkResolver = { context.routes.resolveLink(record.variant.sourcePath, it, context.locale) })) { showPostContent() }
        }
        translationLines(record, context, "active")
    }
    override fun getDate() = record.date
}
