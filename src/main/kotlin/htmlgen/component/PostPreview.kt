package htmlgen.component

import htmlgen.model.BlogPostPage
import htmlgen.model.DevLogPostPage
import htmlgen.model.home.BlogElement
import kotlinx.html.*
import htmlgen.toNormalString
import serverPathString


fun FlowContent.largeBlogPostPreview(post: BlogPostPage) {
    div {
        with(BlogElement(post)) {
            show()
        }
    }
}

fun FlowContent.blogPostPreview(blogPost: BlogPostPage) {
    a {
        classes += arrayOf("post_preview", "regular", "reveal", "blog")
        href = blogPost.htmlServerPath

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
}

fun FlowContent.devLogPostPreview(devLogPost: DevLogPostPage) {
    a {
        classes += arrayOf("post_preview", "regular", "reveal", "devlog")
        href = devLogPost.htmlServerPath

        val emoji = devLogPost.getEmoji()
        val imageUrl = devLogPost.previewImagePath?.serverPathString();
        if (imageUrl != null) {
            img {
                src = imageUrl
            }
        } else {
            div {
                classes += "emoji"
                +emoji
            }
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +devLogPost.getPlainTitle()
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +devLogPost.getPreviewDisplayDate()
            }
        }
    }
}