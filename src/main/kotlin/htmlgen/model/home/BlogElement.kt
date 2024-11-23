package htmlgen.model.home

import htmlgen.model.BlogPost
import htmlgen.toNormalString
import kotlinx.html.*
import java.util.Date

class BlogElement(
    val blogPost: BlogPost
) : HomeElement {
    override fun DIV.show() {
        classes += "post_preview"
        classes += "regular"
        classes += "reveal"
        onClick = "location.href='${blogPost.htmlServerPath}';"

        val emoji = blogPost.getEmoji()
        div {
            classes += "emoji"
            +emoji
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +blogPost.getPlainTitle()
            }
            h3 {
                classes += "slug"
                +if (blogPost.slug != null) blogPost.slug.toNormalString() else "没有介绍"
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +blogPost.getPreviewDisplayDate()
            }
            p {
                classes += "type"
                +blogPost.type.name!!
            }
        }
    }

    override fun getDate(): Date {
        return blogPost.pageData.createdTimeDate
    }
}