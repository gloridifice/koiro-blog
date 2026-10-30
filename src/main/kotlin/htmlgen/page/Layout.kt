package htmlgen.page

import htmlgen.SvgIcons
import htmlgen.component.ContactBarItem
import htmlgen.component.contactBar
import htmlgen.component.languageConfiguration
import htmlgen.linkCSS
import htmlgen.universalHeadSetting
import kotlinx.html.*

fun BODY.footer() {
    footer {
        contactBar(
            listOf(
                ContactBarItem(
                    SvgIcons.GITHUB, "github", "https://github.com/gloridifice"
                ),
                ContactBarItem(
                    SvgIcons.TWITTER, "twitter", "https://twitter.com/gloridifice"
                ),
                ContactBarItem(
                    SvgIcons.EMAIL,
                    "email", "mailto:gloridifice@gmail.com"
                )
            )
        )
        div {
            a {
                rel = "me"
                href = "https://mastodon.gamedev.place/@koiro"
                +"Mastodon"
            }
        }
    }
}

fun HTML.layout(
    context: routing.PageContext,
    article: content.ResolvedContent? = null,
    siteTitle: String = "Koiro's Cat Café",
    cssNames: Array<String> = emptyArray(),
    jsNames: Array<String> = emptyArray(),
    headFont: String? = null,
    block: BODY.() -> Unit,
) {
    lang = context.locale.tag
    head {
        meta {
            name = "darkreader-lock"
            content = "true"
        }
        universalHeadSetting(context)
        link {
            rel = "canonical"
            href = "https://blog.koiro.xyz" + if (article?.fallback == true)
                context.routes.content(context.config.locale(article.variant.localeTag), article.bundle.key).serverPath
                else context.routes.page(context.locale, context.page).serverPath
        }
        if (article?.fallback == true) meta { name = "robots"; content = "noindex,follow" }
        val alternates = context.config.locales.filter { locale ->
            article == null || article.bundle.variants[locale.tag]?.header?.published == true
        }
        for (locale in alternates) link {
            rel = "alternate"; attributes["hreflang"] = locale.tag
            href = "https://blog.koiro.xyz" + context.routes.page(locale, context.page).serverPath
        }
        jsNames.forEach {
            script { src = "/assets/js/$it.js" }
        }
        linkCSS("layout", "page_content", "language", *cssNames)
        title(siteTitle)
    }
    body {
        block()
        languageConfiguration(context)
    }
}