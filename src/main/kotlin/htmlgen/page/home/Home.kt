package htmlgen.page.home

import htmlgen.SvgIcons
import htmlgen.component.languageTools
import htmlgen.component.message
import htmlgen.component.naviWithHighlightedItem
import htmlgen.component.touchableAvatar
import htmlgen.page.layout
import htmlgen.page.home.subpage.HomeSubPage
import kotlinx.html.*
import routing.PageContext
import routing.PageKey

fun HTML.homePage(context: PageContext, page: HomeSubPage) {
    layout(context, siteTitle = if (context.page is PageKey.Home)
        context.text("site.homeTitle", "page" to context.page.number) else context.text("site.title"),
        cssNames = arrayOf("home") + page.getCssNames(), jsNames = page.getJsNames()) {
        div("about") {
            div("up") {
                img(src = "/assets/resources/about_icon.png", alt = "Koiro's Cat Café")
                div("outside_pages") {
                    for ((url, label) in listOf("https://gloridifice.itch.io/" to "site.games",
                        "https://github.com/gloridifice/kotlin-notion-blog" to "site.source")) a(url, target = "_blank", classes = "outside_page_item button row") {
                        rel = "noopener noreferrer"; with(SvgIcons.EXTERNAL_LINK) { showSvg() }
                        div("desc") { message(context, label) }
                    }
                }
                div("introduction") {
                    div("icon") { touchableAvatar("/assets/resources/doiro.png") }
                    div("texts") {
                        h2 { message(context, "site.welcome") }
                        p { message(context, "site.introduction") }
                    }
                }
                languageTools(context, social = true)
            }
            div("down") { div("navi") {
                val items = context.navigation()
                val selected = items.single { context.page == it.key || context.page is PageKey.Home && it.key is PageKey.Home }
                naviWithHighlightedItem(items, selected)
            } }
        }
        div("contents_wrapper") { div("contents") { page.apply { showSubPage() } } }
    }
}
