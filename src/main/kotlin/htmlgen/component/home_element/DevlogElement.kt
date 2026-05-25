package htmlgen.component.home_element

import kotlinx.html.*
import DevlogRecord
import kotlinx.datetime.LocalDateTime

class DevlogElement(
    val devlogRecord: DevlogRecord,
) : HomeElement {

    override fun DIV.show() {
        a {
            classes += arrayOf("post_preview", "devlog", "large")
            href = devlogRecord.serverPath.serverPath
//            onClick = "location.href='${devLogPost.htmlServerPath}';"

            val emoji = ""//todo
            devlogRecord.previewImagePath?.let {
                div {
                    classes += "preview"
                    img {
                        src = it.serverPath
                    }
                }
            }

            div {
                classes += "description"
                h2 {
                    classes += "title"
                    +devlogRecord.header.title
                }
//                p {
//                    classes += "info"
//                    +"info text" //todo
//                }
            }
        }
    }

    override fun getDate(): LocalDateTime {
        return devlogRecord.date
    }
}