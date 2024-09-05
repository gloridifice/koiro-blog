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
                arrayOf(
                    FriendLinkItem("拉斯普的月台", "https://blog.rasp505.top/", "海拉鲁驿站"),
                    FriendLinkItem("dyron503's", "https://career.dyron503.com/", "你知道吗？我的 ID 中「503」的出处是……"),
                    FriendLinkItem("北依的树洞", "https://hanahoshikawa092.netlify.app", "音乐是救世主"),
                )
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