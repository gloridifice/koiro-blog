package component

import BlogPage
import kotlinx.html.*
import toNormalString

fun FlowContent.postPreview(blogPage: BlogPage) {
    div {
        classes += "post_preview"
        onClick = "location.href='${blogPage.htmlServerPath}';"

        val emoji = blogPage.getEmoji()
        div {
            classes += "emoji"
            +emoji
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +blogPage.pageTitle.toNormalString()
            }
            h3 {
                classes += "slug"
                +blogPage.slug.toNormalString()
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +blogPage.getDate()
            }
            p {
                classes += "type"
                +blogPage.type.name!!
            }
        }
    }
}
