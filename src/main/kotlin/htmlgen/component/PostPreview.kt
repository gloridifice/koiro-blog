package htmlgen.component

import Post
import kotlinx.html.*
import htmlgen.toNormalString

fun FlowContent.postPreview(post: Post) {
    div {
        classes += "post_preview"
        classes += "regular"
        onClick = "location.href='${post.htmlServerPath}';"

        val emoji = post.getEmoji()
        div {
            classes += "emoji"
            +emoji
        }

        div {
            classes += "description"
            h2 {
                classes += "title"
                +post.pageTitle.toNormalString()
            }
            h3 {
                classes += "slug"
                +post.slug.toNormalString()
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +post.getDateDay()
            }
            p {
                classes += "type"
                +post.type.name!!
            }
        }
    }
}

fun FlowContent.largePostPreview(post: Post) {
    div {
        classes += "post_preview"
        classes += "large"
        onClick = "location.href='${post.htmlServerPath}';"

        div {
            classes += "title_part"
            val emoji = post.getEmoji()
            div {
                classes += "emoji"
                +emoji
            }

            h2 {
                classes += "title"
                +post.pageTitle.toNormalString()
            }
        }
        h3 {
            classes += "slug"
            +post.slug.toNormalString()
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +post.getDateDay()
            }
            div {
                classes += "type_tags"

                post.tags.forEach {
                    p {
                        classes += "tag"
                        +it.name.orEmpty()
                    }
                }

                p {
                    classes += "type"
                    +post.type.name!!
                }
            }

        }
    }
}
