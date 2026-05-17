package markdown.htmlgen.component.home_element

import kotlinx.datetime.LocalDate
import kotlinx.html.*
import markdown.BlogRecord
import java.util.Date

class BlogElement(
    val blogRecord: BlogRecord
) : HomeElement {
    override fun DIV.show() {
        a {
            classes += arrayOf("post_preview", "blog", "large")
            href = blogRecord.serverPath.serverPath()

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
                h3 {
                    classes += "slug"
                    +blogRecord.header.slug
                }
            }
        }
    }

    override fun getDate(): LocalDate {
        return blogRecord.header.date
    }
}