package htmlgen.component.home_element

import kotlinx.html.*
import BlogRecord
import kotlinx.datetime.LocalDateTime

class BlogElement(
    val blogRecord: BlogRecord
) : HomeElement {
    override fun DIV.show() {
        a {
            classes += arrayOf("post_preview", "blog", "large")
            href = blogRecord.serverPath.serverPath

            val emoji = blogRecord.header.emoji

            div {
                classes += "description"
                div {
                    classes += "title"
                    h2 {
                        +blogRecord.header.title
                    }
                    div {
                        classes += "emoji"
                        +emoji.orEmpty()
                    }
                }
                p {
                    classes += "slug"
                    +blogRecord.header.slug
                }
            }
        }
    }

    override fun getDate(): LocalDateTime {
        return blogRecord.date
    }
}