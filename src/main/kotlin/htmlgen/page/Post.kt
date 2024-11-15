package htmlgen.page

import BlogContext
import BlogPost
import htmlgen.component.NaviItem
import htmlgen.component.notionBlocks
import htmlgen.navbarItems
import htmlgen.richTexts
import kotlinx.html.*
import notion.api.v1.model.blocks.*
import notion.api.v1.model.databases.DatabaseProperty
import notiondata.DataPage
import kotlin.io.path.*

class PostContext(
    var h1Index: Int = 0,
    var h2Index: Int = 0,
    var h3Index: Int = 0
)

fun headingId(heading: Int, index: Int): String {
    return "heading${heading}_$index"
}

fun FlowContent.navi(array: ArrayList<NaviItem>) {
    div {
        classes += "navi"
        for (item in array) {
            a {
                classes += "navi_link"
                href = item.link
                +item.title
            }
        }
    }
}

fun FlowContent.catalogue(page: DataPage, context: BlogContext) {
    div {
        classes += "catalogue"
        div {
            val headBlocks = page.dataBlocks?.filter {
                it.block is HeadingOneBlock || it.block is HeadingTwoBlock || it.block is HeadingThreeBlock
            }
            val pCtx = PostContext()
            ul {
                headBlocks?.let {
                    for (index in it.indices) {
                        val block = it[index].block

                        if (block is HeadingOneBlock) {
                            li {
                                classes += "h1"
                                a {
                                    href = "#${headingId(1, pCtx.h1Index)}"
                                    pCtx.h1Index++

                                    richTexts(block.heading1.richText)
                                }
                            }
                        }
                        if (block is HeadingTwoBlock) {
                            li {
                                classes += "h2"
                                a {
                                    href = "#${headingId(2, pCtx.h3Index)}"
                                    pCtx.h2Index++

                                    richTexts(block.heading2.richText)
                                }
                            }
                        }

                        if (block is HeadingThreeBlock) {
                            li {
                                classes += "h3"
                                a {
                                    href = "#${headingId(3, pCtx.h3Index)}"
                                    pCtx.h3Index++

                                    richTexts(block.heading3.richText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun HTML.blogPost(dataPage: DataPage, context: BlogContext) {
    val blogPost = BlogPost(dataPage.page)

    Path(blogPost.assetsDirectoryPath).deleteRecursively()
    val postContext = PostContext()

    layout(
        siteTitle = blogPost.getEmoji() + " " + blogPost.getPlainTitle(),
        jsNames = arrayOf("highlightjs/highlight"),
        cssNames = arrayOf("post", "color_scheme_v2.dark_mode", "highlightjs/github-dark")
    ) {
        post(
            "${blogPost.getEmoji()} ${blogPost.getPlainTitle()}",
            blogPost.tags,
            blogPost.type.name,
            blogPost.getLastEditedTimeDay(),
            dataPage,
            context,
            postContext
        )
    }
}

fun FlowContent.post(
    title: String,
    tags: List<DatabaseProperty.MultiSelect.Option>?,
    typeName: String?,
    lastEditedTimeString: String,
    dataPage: DataPage,
    context: BlogContext,
    postContext: PostContext
) {
    script { +"hljs.highlightAll();" }
    div {
        classes += "post"
        catalogue(dataPage, context); //目录
        navi(navbarItems)
        div {
            classes += "sidebar_wrapper_left"
            classes += "sidebar_wrapper"
        }
        div {
            classes += "sidebar_wrapper_right"
            classes += "sidebar_wrapper"
        }
        div {
            classes += "contents"
            div {
                classes += "header"
                for (item in navbarItems) {
                    a {
                        classes += "navi_link"
                        href = item.link
                        +item.title
                    }
                }
            }
            div {
                classes += "page_description"
                h1 {
                    classes += "title"
                    +title
                }
                div {
                    classes += "sub_info"
                    p {
                        classes += "date"
                        +lastEditedTimeString
                    }
                    tags?.let {
                        div {
                            classes += "type_tags"
                            it.forEach {
                                p {
                                    classes += "tag"
                                    +it.name.orEmpty()
                                }
                            }
                            typeName?.let {
                                p {
                                    classes += "type"
                                    +it
                                }
                            }
                        }
                    }

                }
            }
            div {
                classes += "page_contents"
                dataPage.dataBlocks?.let { notionBlocks(it, dataPage, context, postContext) }
            }
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun HTML.devLogPost(dataPage: DataPage, context: BlogContext) {
    val devLogPost = dataPage.devLogPost()

    Path(devLogPost.assetsDirectoryPath).deleteRecursively()
    val postContext = PostContext()

    layout(
        siteTitle = devLogPost.getEmoji() + " " + devLogPost.getPlainTitle(),
        cssNames = arrayOf("post")
    ) {
        post(
            "${devLogPost.getEmoji()} ${devLogPost.getPlainTitle()}",
            null,
            null,
            devLogPost.getLastEditedTimeDay(),
            dataPage,
            context,
            postContext
        )
    }
}


