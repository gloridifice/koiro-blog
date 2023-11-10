package htmlgen.component

import BlogContext
import htmlgen.SVGIcons
import htmlgen.colorClass
import htmlgen.page.PostContext
import htmlgen.richTexts
import htmlgen.unsafeSVG
import kotlinx.html.*
import notion.api.v1.model.blocks.*
import notion.api.v1.model.common.Emoji
import notion.api.v1.model.pages.PageProperty
import notiondata.BookmarkDataBlock
import notiondata.DataBlock
import notiondata.DataPage
import notiondata.ImageDataBlock
import outputDirectory
import java.net.URL
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories

fun FlowContent.tryGenChildren(
    dataBlock: DataBlock,
    dataPage: DataPage,
    context: BlogContext,
    postContext: PostContext
) {
    dataBlock.children?.let { notionBlocks(it, dataPage, context, postContext) }
}

fun FlowContent.notionBlocks(block: List<DataBlock>, page: DataPage, context: BlogContext, postContext: PostContext) {
    block.forEach { notionBlock(it, page, context, postContext) }
}

fun FlowContent.notionBlock(dataBlock: DataBlock, dataPage: DataPage, context: BlogContext, postContext: PostContext) {
    val block = dataBlock.block
    val post = dataPage.post
    when {
        //todo
        block is ParagraphBlock -> {
            p {
                block.paragraph.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.paragraph.richText)
                tryGenChildren(dataBlock, dataPage, context, postContext)
            }
        }

        block is HeadingOneBlock -> {
            h1 {
                id = "heading1_${postContext.h1Index}"
                postContext.h1Index++
                block.heading1.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading1.richText)
            }
        }

        block is HeadingTwoBlock -> {
            h2 {
                id = "heading2_${postContext.h2Index}"
                postContext.h2Index++
                block.heading2.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading2.richText)
            }
        }

        block is HeadingThreeBlock -> {
            h3 {
                id = "heading3_${postContext.h3Index}"
                postContext.h3Index++
                block.heading3.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading3.richText)
            }
        }

        block is QuoteBlock -> {
            blockQuote {
                block.quote?.color?.let { color -> colorClass(color)?.let { classes += it } }
                block.quote?.richText?.let { richTexts(it) }
            }
        }

        block is BulletedListItemBlock -> {
            ul {
                li {
                    block.bulletedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                    richTexts(block.bulletedListItem.richText)
                    tryGenChildren(dataBlock, dataPage, context, postContext)
                }
            }
        }

        //todo
        block is NumberedListItemBlock -> {
            ol {
                li {
                    block.numberedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                    richTexts(block.numberedListItem.richText)
                    tryGenChildren(dataBlock, dataPage, context, postContext)
                }
            }
        }

        block is ColumnListBlock -> {
            div {
                classes += "column_list"
                tryGenChildren(dataBlock, dataPage, context, postContext)
            }
        }

        block is ColumnBlock -> {
            div {
                classes += "column"
                tryGenChildren(dataBlock, dataPage, context, postContext)
            }
        }

        block is ToDoBlock -> {
            div {
                classes += "todo_block"
                if (block.toDo.checked) classes += "checked"
                div {
                    classes += "icon"
                    val icon = if (block.toDo.checked) SVGIcons.CHECK_BOX_CHECKED else SVGIcons.CHECK_BOX_EMPTY
                    unsafeSVG(icon)
                }
                div {
                    classes += "text"
                    block.toDo.richText?.let {
                        richTexts(it)
                    }
                }
            }
        }

        block is ToggleBlock -> {

        }

        block is CalloutBlock -> {
            block.callout?.let { callout ->
                div {
                    classes += "callout"
                    val emoji = callout.icon;
                    if (emoji is Emoji) {
                        div {
                            classes += "icon"
                            +emoji.emoji.orEmpty()
                        }
                    }
                    div {
                        classes += "text"
                        callout.richText?.let { richTexts(it) }
                    }
                }
            }
        }

        block is DividerBlock -> {
            hr {
                classes += "divider_block"
            }
        }

        block is CodeBlock -> {
            block.code?.let { code ->
                div {
                    classes += "code_block"
                    div {
                        classes += "code_part"
                        code.language?.let {
                            div {
                                classes += "code_lang"
                                +it
                            }
                        }
                        div {
                            classes += "code_part_text"
                            code.richText?.let { richTexts(it) }
                        }
                    }
                    div {
                        classes += "caption"
                        code.caption?.let { richTexts(it) }
                    }
                }
            }
        }

        //todo
        block is ImageBlock -> {
            if (dataBlock is ImageDataBlock)
                block.image?.let { image ->
                    image.file?.url?.let {
                        val imgName = dataBlock.image.name
                        val path = Path("${outputDirectory}${post.htmlName}/${imgName}")
                        path.createParentDirectories()
                        path.toFile().writeBytes(dataBlock.image.byteArray)

                        div {
                            classes += "image_wrapper"
                            img {
                                src = "/${post.htmlName}/${imgName}"
                            }
                            caption(image.caption)
                        }
                    }
                }
        }

        //todo
        block is BookmarkBlock && dataBlock is BookmarkDataBlock -> {
            block.bookmark?.let {
                div {
                    classes += "bookmark_block"
                    div {
                        classes += "bookmark"

                        it.url?.let { url ->
                            onClick = "window.open('${url}')"

                            val title = dataBlock.title
                            val titleText = title ?: url
                            div {
                                classes += "title"
                                +titleText
                            }
                            div {
                                classes += "url"
                                +url
                            }
                        }
                    }
                    caption(it.caption)
                }

            }
        }

        block is EquationBlock -> {

        }
    }
}
fun FlowContent.bilibiliVideo(bvString: String){
    div {
        classes += "bilibili_video"
        iframe {
            src = "//player.bilibili.com/player.html?bvid=${bvString}&page=1&danmaku=0&high_quality=1"
        }
    }
    //todo
}
fun FlowContent.caption(caption: List<PageProperty.RichText>?) {
    div {
        classes += "caption"
        caption?.let { richTexts(it) }
    }
}

fun getBookmarkTitle(urlString: String): String? {
    return urlString
    val response = URL(urlString).openStream()
    val scanner = Scanner(response)
    val responseBody = scanner.useDelimiter("\\A").next()
    val ret = responseBody.substring(responseBody.indexOf("<title>") + 7, responseBody.indexOf("</title>"))

    return ret
}