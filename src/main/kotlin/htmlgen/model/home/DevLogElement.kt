package htmlgen.model.home

import htmlgen.model.DevLogPost
import kotlinx.html.*
import java.util.*

class DevLogElement(
    val devLogPost: DevLogPost
): HomeElement {

    override fun DIV.show() {
        classes += "dev_log_post_preview"
        classes += "regular"
        classes += "reveal"
        onClick = "location.href='${devLogPost.htmlServerPath}';"

        val emoji = devLogPost.getEmoji()
        val imageUrl = devLogPost.previewImageUrl;
        if (imageUrl != null){
            div {
                classes += "preview"
                img {
                    src = imageUrl
                }
            }
        }else{
            div {
                classes += "emoji"
                +emoji
            }
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +devLogPost.getPlainTitle()
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +devLogPost.getPreviewDisplayDate()
            }
        }
    }

    override fun getDate(): Date {
        return devLogPost.pageData.createdTimeDate
    }
}