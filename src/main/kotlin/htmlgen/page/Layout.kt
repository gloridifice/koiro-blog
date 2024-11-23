package htmlgen.page

import htmlgen.SVGIcons
import htmlgen.component.ContactBarItem
import htmlgen.component.contactBar
import kotlinx.html.*
import htmlgen.linkCSS
import htmlgen.universalHeadSetting

fun BODY.footer() {
    footer {
        contactBar(
            listOf(
                ContactBarItem(
                    SVGIcons.GITHUB, "github", "https://github.com/gloridifice"
                ),
                ContactBarItem(
                    SVGIcons.TWITTER, "twitter", "https://twitter.com/gloridifice"
                ),
                ContactBarItem(
                    SVGIcons.EMAIL,
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