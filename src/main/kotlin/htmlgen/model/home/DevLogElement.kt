package htmlgen.model.home

import htmlgen.model.DevLogPostPage
import kotlinx.html.*
import serverPathString
import java.util.*

class DevLogElement(
    val devLogPost: DevLogPostPage
) : HomeElement {

    override fun DIV.show() {
        a {
            classes += arrayOf("post_preview", "devlog", "large")
            href = devLogPost.htmlServerPath
//            onClick = "location.href='${devLogPost.htmlServerPath}';"

            val emoji = devLogPost.getEmoji()
            val imageUrl = devLogPost.previewImagePath?.serverPathString();
            if (imageUrl != null) {
                div {
                    classes += "preview"
                    img {
                        src = imageUrl
                    }
                }
            }

            div {
                classes += "description"
                h2 {
                    classes += "title"
                    +devLogPost.getPlainTitle()
                }
                p {
                    classes += "info"
                    +devLogPost.getDevLogInfoText();
                }
            }
        }
    }

    override fun getDate(): Date {
        return devLogPost.publishedDate ?: devLogPost.createdTimeDate
    }
}