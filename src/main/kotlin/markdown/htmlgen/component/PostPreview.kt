package markdown.htmlgen.component

import kotlinx.html.*
import markdown.BlogRecord
import markdown.DevlogRecord
import markdown.htmlgen.component.home_element.BlogElement
import serverPathString


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
        href = blogPost.serverPath.serverPath()

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
            h3 {
                classes += "slug"
                +blogPost.header.slug
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +blogPost.header.displayDate()
            }
            p {
                classes += "type"
                +blogPost.header.blogClass
            }
        }
    }
}

fun FlowContent.devLogPostPreview(devLogPost: DevlogRecord) {
    a {
        classes += arrayOf("post_preview", "regular", "reveal", "devlog")
        href = devLogPost.serverPath.serverPath()

        val imageUrl = devLogPost.header.previewImagePath;
        if (imageUrl != null) {
            img {
                src = imageUrl
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
                +devLogPost.header.title
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