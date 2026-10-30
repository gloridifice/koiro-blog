package htmlgen.component.home_element

import kotlinx.html.*
import BlogRecord
import htmlgen.component.blogIcon
import kotlinx.datetime.LocalDateTime

class BlogElement(
    val blogRecord: BlogRecord
) : HomeElement {
    override fun DIV.show(imageLoading: ImgLoading) {
        a {
            classes += arrayOf("post_preview", "blog", "large")
            href = blogRecord.serverPath.serverPath

            div {
                classes += "description"
                blogIcon(blogRecord.header)
                div {
                    classes += "title"
                    h2 {
                        +blogRecord.header.title
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