package htmlgen.component

import BlogPost
import DevLogPost
import kotlinx.html.*
import htmlgen.toNormalString

fun FlowContent.blogPostPreview(blogPost: BlogPost) {
    div {
        classes += "post_preview"
        classes += "regular"
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
                + if (blogPost.slug != null) blogPost.slug.toNormalString() else "没有介绍"
            }
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +blogPost.getLastEditedTimeDay()
            }
            p {
                classes += "type"
                +blogPost.type.name!!
            }
        }
    }
}

fun FlowContent.largePostPreview(post: BlogPost) {
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
                +post.getPlainTitle()
            }
        }
        h3 {
            classes += "slug"
            + if (post.slug != null) post.slug.toNormalString() else "没有介绍"
        }

        div {
            classes += "info"
            p {
                classes += "date"
                +post.getLastEditedTimeDay()
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

fun FlowContent.devLogPostPreview(devLogPost: DevLogPost){
    div {
        classes += "dev_log_post_preview"
        classes += "regular"
        onClick = "location.href='${devLogPost.htmlServerPath}';"

        val emoji = devLogPost.getEmoji()
        val imageUrl = devLogPost.previewImageUrl;
        if (imageUrl != null){
            div {
                classes += "preview"
                img {
                    src = imageUrl
                }
            }
        }else{
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
                +devLogPost.getCreatedTimeDay()
            }
        }
    }
}