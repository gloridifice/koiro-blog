package htmlgen.page

import htmlgen.SvgIcons
import htmlgen.component.ContactBarItem
import htmlgen.component.contactBar
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
    siteTitle: String = "Koiro's Cat Café",
    cssNames: Array<String> = emptyArray(),
    jsNames: Array<String> = emptyArray(),
    headFont: String? = null,
    block: BODY.() -> Unit,
) {
    head {
        meta {
            name = "darkreader-lock"
            content = "true"
        }
        universalHeadSetting()
        jsNames.forEach {
            script { src = "/assets/js/$it.js" }
        }
        linkCSS("layout", "page_content", *cssNames)
        title(siteTitle)
    }
    body {
        block()
    }
}