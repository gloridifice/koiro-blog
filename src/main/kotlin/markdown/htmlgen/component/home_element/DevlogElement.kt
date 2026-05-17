package markdown.htmlgen.component.home_element

import kotlinx.datetime.LocalDate
import kotlinx.html.*
import markdown.DevlogRecord
import java.util.*

class DevlogElement(
    val devlogRecord: DevlogRecord,
) : HomeElement {

    override fun DIV.show() {
        a {
            classes += arrayOf("post_preview", "devlog", "large")
            href = devlogRecord.serverPath.serverPath()
//            onClick = "location.href='${devLogPost.htmlServerPath}';"

            val emoji = ""//todo
            val imageUrl = devlogRecord.header.previewImagePath;//todo
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
                    +devlogRecord.header.title
                }
                p {
                    classes += "info"
                    +"info text" //todo
                }
            }
        }
    }

    override fun getDate(): LocalDate {
        return devlogRecord.header.date
    }
}