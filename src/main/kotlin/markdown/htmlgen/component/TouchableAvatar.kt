package markdown.htmlgen.component

import kotlinx.html.*

fun FlowContent.touchableAvatar(imagePath: String) {
    div {
        classes += "touchable-avatar"
        img {
            src = imagePath
        }
    }
}