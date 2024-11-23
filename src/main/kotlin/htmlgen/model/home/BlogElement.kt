package htmlgen.model.home

import htmlgen.model.BlogPostPage
import htmlgen.toNormalString
import kotlinx.html.*
import java.util.Date

class BlogElement(
    val blogPost: BlogPostPage
) : HomeElement {
    override fun DIV.show() {
        classes += "post_preview"
        classes += "regular"
        onClick = "location.href='${blogPost.htmlServerPath}';"

        val emoji = blogPost.getEmoji()

        div {
            classes += "description"
            div {
                classes += "title"
                h2 {
                    +blogPost.getPlainTitle()
                }
                div {
                    classes += "emoji"
                    +emoji
                }
            }
            h3 {
                classes += "slug"
                +if (blogPost.slug != null) blogPost.slug.toNormalString() else "没有介绍"
            }
        }

//        div { classes += "info"; p { classes += "date"; +blogPost.getPreviewDisplayDate() }; p { classes += "type"; +blogPost.type.name!! } }
    }

    override fun getDate(): Date {
        return blogPost.createdTimeDate
    }
}