package htmlgen.page

import htmlgen.SVGIcons
import htmlgen.asLoc
import htmlgen.component.ContactBarItem
import htmlgen.component.NavbarItem
import htmlgen.component.contactBar
import htmlgen.component.navbar
import htmlgen.htmlServerPath
import kotlinx.html.*
import htmlgen.linkCSS
import htmlgen.resourcesServerPath
import htmlgen.universalHeadSetting

val navbarItems = arrayListOf(
    NavbarItem("Home", htmlServerPath("home".asLoc()), SVGIcons.HOME),
//    NavbarItem("About", htmlServerPath("about".asLoc()), SVGIcons.USERS),
    NavbarItem(
        "Notes",
        "https://gloridifice.notion.site/3659ec2ee2f7498ab744662c364b518a?v=0d711eab95f748eb82a966726ec3f757&pvs=4",
        SVGIcons.PACKAGE,
        true
    ),
);

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

// 包含了 head, body#header,footer
// block 是 body 中夹在 header 和 footer 之间的部分
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
        linkCSS("layout", *cssNames)
        title(siteTitle)
    }
    body {
        block()
    }
}