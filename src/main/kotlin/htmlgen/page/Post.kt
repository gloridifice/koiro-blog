package htmlgen.page

import BlogContext
import htmlgen.colorClass
import htmlgen.downloadImageAutoName
import kotlinx.html.*
import notion.api.v1.model.blocks.*
import htmlgen.richTexts
import notion.api.v1.model.pages.PageProperty
import notiondata.DataBlock
import notiondata.DataPage
import notiondata.ImageDataBlock
import outputDirectory
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.util.Scanner
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun HTML.post(page: DataPage, context: BlogContext) {
    Path(page.post.assetsDirectoryPath).deleteRecursively()

    val post = page.post

    layout(
        siteTitle = post.getEmoji() + " " + post.getPlainTitle(),
        cssNames = arrayOf("post")
    ) {
        div {
            classes += "contents"
            div {
                classes += "page_description"
                h1 {
                    classes += "title"
                    +"${post.getEmoji()} ${post.getPlainTitle()}"
                }
                div {
                    classes += "sub_info"
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
            div {
                classes += "page_contents"
                page.dataBlocks?.let { notionBlocks(it, page, context) }
            }
        }
    }
}

fun FlowContent.tryGenChildren(dataBlock: DataBlock, dataPage: DataPage, context: BlogContext) {
    dataBlock.children?.let { notionBlocks(it, dataPage, context) }

}

fun FlowContent.notionBlocks(block: List<DataBlock>, page: DataPage, context: BlogContext) {
    block.forEach { notionBlock(it, page, context) }
}

fun FlowContent.notionBlock(dataBlock: DataBlock, dataPage: DataPage, context: BlogContext) {
    val block = dataBlock.block
    val post = dataPage.post
    when {
        //todo
        block is ParagraphBlock -> {
            p {
                block.paragraph.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.paragraph.richText)
                tryGenChildren(dataBlock, dataPage, context)
            }
        }

        block is HeadingOneBlock -> {
            h1 {
                block.heading1.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading1.richText)
            }
        }

        block is HeadingTwoBlock -> {
            h2 {
                block.heading2.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading2.richText)
            }
        }

        block is HeadingThreeBlock -> {
            h3 {
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
                    tryGenChildren(dataBlock, dataPage, context)
                }
            }
        }

        //todo
        block is NumberedListItemBlock -> {
            ol {
                li {
                    block.numberedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                    richTexts(block.numberedListItem.richText)
                    tryGenChildren(dataBlock, dataPage, context)
                }
            }
        }

        block is ColumnListBlock -> {
            div {
                classes += "column_list"
                tryGenChildren(dataBlock, dataPage, context)
            }
        }

        block is ColumnBlock -> {
            div {
                classes += "column"
                tryGenChildren(dataBlock, dataPage, context)
            }
        }

        block is ToDoBlock -> {

        }

        block is ToggleBlock -> {

        }

        block is CalloutBlock -> {

        }

        block is DividerBlock -> {

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
        block is BookmarkBlock -> {
            block.bookmark?.let {
                div {
                    classes += "bookmark_block"
                    div {
                        classes += "bookmark"

                        it.url?.let { url ->
                            val title = getBookmarkTitle(url)
                            val titleText = title ?: url
                            a {
                                href = url
                                +titleText
                            }

                            if (title != null) {
                                div {
                                    classes += "url_detail"
                                }
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
