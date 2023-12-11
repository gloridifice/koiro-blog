package htmlgen.page

import BlogContext
import BlogPost
import DevLogPost
import htmlgen.component.notionBlocks
import htmlgen.richTexts
import kotlinx.html.*
import notion.api.v1.model.blocks.*
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

fun FlowContent.sidebarContent(page: DataPage, context: BlogContext) {
    div {
        classes += "sidebar_wrapper"
        div {
            classes += "catalogue"
            h2 {
                +"目录"
            }
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
                            if(block is HeadingTwoBlock) {
                                li {
                                    classes += "h2"
                                    a {
                                        href = "#${headingId(2, pCtx.h3Index)}"
                                        pCtx.h2Index++

                                        richTexts(block.heading2.richText)
                                    }
                                }
                            }

                            if(block is HeadingThreeBlock) {
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
}

@OptIn(ExperimentalPathApi::class)
fun HTML.blogPost(dataPage: DataPage, context: BlogContext) {
    val blogPost = BlogPost(dataPage.page)

    Path(blogPost.assetsDirectoryPath).deleteRecursively()
    val postContext = PostContext()

    layout(
        siteTitle = blogPost.getEmoji() + " " + blogPost.getPlainTitle(),
        cssNames = arrayOf("post")
    ) {
        div {
            classes += "post"
            div {
                classes += "sidebar_wrapper"
            }
            div {
                classes += "contents"
                div {
                    classes += "page_description"
                    h1 {
                        classes += "title"
                        +"${blogPost.getEmoji()} ${blogPost.getPlainTitle()}"
                    }
                    div {
                        classes += "sub_info"
                        p {
                            classes += "date"
                            +blogPost.getLastEditedTimeDay()
                        }
                        div {
                            classes += "type_tags"
                            blogPost.tags.forEach {
                                p {
                                    classes += "tag"
                                    +it.name.orEmpty()
                                }
                            }
                            p {
                                classes += "type"
                                +blogPost.type.name!!
                            }
                        }
                    }
                }
                div {
                    classes += "page_contents"
                    dataPage.dataBlocks?.let { notionBlocks(it, dataPage, context, postContext) }
                }
            }
            sidebarContent(dataPage, context)
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
        div {
            classes += "post"
            div {
                classes += "sidebar_wrapper"
            }
            div {
                classes += "contents"
                div {
                    classes += "page_description"
                    h1 {
                        classes += "title"
                        +"${devLogPost.getEmoji()} ${devLogPost.getPlainTitle()}"
                    }
                    div {
                        classes += "sub_info"
                        p {
                            classes += "date"
                            +devLogPost.getCreatedTimeDay()
                        }
                        div {
                            classes += "type_tags"
                            p {
                                classes += "type"
                                +devLogPost.work?.name!!
                            }
                        }
                    }
                }
                div {
                    classes += "page_contents"
                    dataPage.dataBlocks?.let { notionBlocks(it, dataPage, context, postContext) }
                }
            }
            sidebarContent(dataPage, context)
        }
    }
}
