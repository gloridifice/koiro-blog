package htmlgen.page

import BlogContext
import htmlgen.SVGIcons
import htmlgen.asLoc
import htmlgen.resourcesServerPath
import htmlgen.unsafeSVG
import kotlinx.html.*

val introduce = "人类、学生、游戏开发者、平面设计笨蛋和技术美术笨蛋。"
fun HTML.about(context: BlogContext) {
    layout(siteTitle = "关于", cssNames = arrayOf("about"), headFont = "ABOUT") {
        div {
            classes += "contents"

            div {
                classes += "introduce"
                div {
                    classes += "avatar_wrapper"
                    img {
                        src = resourcesServerPath("Koiro_Arc.png".asLoc())
                    }
                }
                div {
                    classes += "description_wrapper"
                    p {
                        +introduce
                    }
                }
            }

            friends(
                friendLinkItems
            )
        }
    }
}


fun FlowContent.friends(links: Array<FriendLinkItem>) {
    div {
        classes += "friends_wrapper"
        h2 {
            +"友情链接"
        }
        links.forEach { friendLink(it) }
    }
}

data class FriendLinkItem(val name: String, val link: String, val description: String = "")

fun FlowContent.friendLink(item: FriendLinkItem) {
    div {
        onClick = "window.open('${item.link}')"
        classes += "friend_link"
        div {
            classes += "icon"
            classes += "start"
            unsafeSVG(SVGIcons.EXTERNAL_LINK)
            div {
                +item.name
            }
        }
        div {
            classes += "desc"
            +item.description
        }
    }
}