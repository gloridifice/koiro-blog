@file:OptIn(ExperimentalTime::class)

package htmlgen.component

import htmlgen.component.home_element.BlogElement
import kotlinx.html.*
import BlogRecord
import DevlogRecord
import htmlgen.formatToString
import kotlin.time.ExperimentalTime


fun FlowContent.largeBlogPostPreview(post: BlogRecord) {
    div {
        BlogElement(post).apply {
            show()
        }
    }
}

fun FlowContent.blogPostPreview(blogPost: BlogRecord) {
    a {
        classes += arrayOf("post_preview", "regular", "reveal", "blog")
        href = blogPost.serverPath.serverPath

        val emoji = blogPost.header.emoji
        div {
            classes += "emoji"
            +emoji.orEmpty()
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +blogPost.header.title
            }
            p {
                classes += "slug"
                +blogPost.header.slug
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                + blogPost.date.formatToString()
            }
            p {
                classes += "type"
                +blogPost.header.blogClass
            }
        }
    }
}

fun FlowContent.devlogPostPreview(devlog: DevlogRecord) {
    a {
        classes += arrayOf("post_preview", "regular", "reveal", "devlog")
        href = devlog.serverPath.serverPath

        val imageUrl = devlog.previewImagePath;
        if (imageUrl != null) {
            img {
                src = imageUrl.serverPath
            }
        } else {
            div {
                classes += "emoji"
                +"" //todo
            }
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +devlog.header.title
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +""//todo
            }
        }
    }
}