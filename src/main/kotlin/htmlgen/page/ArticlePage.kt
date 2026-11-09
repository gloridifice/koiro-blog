package htmlgen.page

import content.ResolvedContent
import htmlgen.ImageAssets
import htmlgen.component.Catalogue
import htmlgen.component.languageTools
import htmlgen.component.translationLines
import htmlgen.page.home.navigation
import kotlinx.html.*
import routing.PageContext

fun HTML.articlePage(record: ResolvedContent, context: PageContext, images: ImageAssets) {
    var collected: ArticleContent.CollectedData? = null
    layout(context, article = record, siteTitle = record.title,
        jsNames = arrayOf("highlightjs/highlight"),
        cssNames = arrayOf("post", "color_scheme_v2.dark_mode", "highlightjs/github-dark")) {
        script { unsafe { +"hljs.highlightAll();" } }
        div("post") {
            div("sidebar_wrapper_left sidebar_wrapper")
            div("contents") {
                div("top_gap_space")
                div("header") {
                    for (item in context.navigation()) a(item.serverPath.serverPath, classes = "navi_link button") {
                        attributes["aria-label"] = item.name; item.svgIcon.apply { showSvg() }
                    }
                    languageTools(context, menuId = "site-language-menu-header")
                }
                div("page_description") {
                    h1("title") { lang = record.variant.localeTag; +record.title }
                    hr()
                    div("sub_info") { p("date") { +record.date.date.toString() } }
                }
                if (record.fallback || record.variant.header.aiTranslation) div("article-translation-notices") {
                    translationLines(record, context)
                }
                div("page_content") {
                    lang = record.variant.localeTag
                    with(ArticleContent(record.variant.markdown, record.variant.sourcePath, images,
                        linkResolver = { context.routes.resolveLink(record.variant.sourcePath, it, context.locale) },
                        componentTitle = context.text("article.componentTitle"))) {
                        collected = showPostContent()
                    }
                }
            }
            div("sidebar_wrapper_right sidebar_wrapper") {
                navi(context) { languageTools(context, menuId = "site-language-menu-navi", placement = "left") }
            }
            collected?.let {
                with(Catalogue(it.headingInfos)) { show(context.text("article.contents"), record.variant.localeTag) }
            }
        }
        script { src = "/assets/js/article-toc.js"; defer = true }
    }
}
