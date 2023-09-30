package page

import BlogPage
import colorClass
import downloadImage
import kotlinx.html.*
import linkCSS
import notion.api.v1.NotionClient
import notion.api.v1.model.blocks.*
import richTexts
import universalHeadSetting
import java.net.URL
import java.util.Scanner
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun HTML.post(page: BlogPage, client: NotionClient) {
    Path(page.assetsDirectoryPath).deleteRecursively()

    val blocks = client.retrieveBlockChildren(page.page.id).results

    head {
        universalHeadSetting()
        linkCSS("layout", "post")
        title(page.getEmoji() + " " + page.getPlainTitle())
    }
    body {
        header()
        div {
            classes += "contents"
            div {
                classes += "page_description"
                h1 {
                    classes += "title"
                    +"${page.getEmoji()} ${page.getPlainTitle()}"
                }
                div {
                    classes += "sub_info"
                    p {
                        classes += "date"
                        +page.getDateDay()
                    }
                    p {
                        classes += "type"
                        +page.type.name!!
                    }
                }
            }
            div {
                classes += "page_contents"
                notionBlocks(blocks,page, client)
            }
        }
    }
}

fun FlowContent.tryGenChildren(block: Block, page: BlogPage, client: NotionClient) {
    if (block.hasChildren == true) {
        block.id?.let { blockId ->
            client.retrieveBlockChildren(blockId).results.forEach {
                notionBlock(it, page, client)
            }
        }
    }
}

fun FlowContent.notionBlocks(block: List<Block>, page: BlogPage, client: NotionClient) {
    block.forEach {
        notionBlock(it, page, client)
    }
}

fun FlowContent.notionBlock(block: Block, page: BlogPage, client: NotionClient) {
    when {
        //todo
        block is ParagraphBlock -> {
            p {
                block.paragraph.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.paragraph.richText)
                tryGenChildren(block, page, client)
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
                    tryGenChildren(block, page, client)
                }
            }
        }

        //todo
        block is NumberedListItemBlock -> {
            ol {
                li {
                    block.numberedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                    richTexts(block.numberedListItem.richText)
                    tryGenChildren(block, page, client)
                }
            }
        }

        block is ColumnListBlock -> {
            div {
                classes += "column_list"
                tryGenChildren(block, page, client)
            }
        }

        block is ColumnBlock -> {
            div {
                classes += "column"
                tryGenChildren(block, page, client)
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

        }

        //todo
        block is ImageBlock -> {
            block.image?.let { image ->
                image.file?.url?.let {
                    val imgName = downloadImage(it, page.assetsDirectoryPath)

                    div {
                        classes += "image_wrapper"
                        img {
                            src = "/${page.htmlName}/${imgName}"
                        }
                        div{
                            classes += "caption"
                            image.caption?.let { richTexts(it) }
                        }
                    }
                }
            }
        }

        //todo
        block is BookmarkBlock -> {
            block.bookmark?.let {
                div {
                    classes += "bookmark"

                    it.url?.let { url ->
                        val title = getBookmarkTitle(url)
                        val titleText = title ?: url
                        a {
                            href = url
                            +titleText
                        }

                        if (title != null){
                            div {
                                classes += "url_detail"
                            }
                        }
                    }

                    it.caption?.let { richTexts(it) }
                }
            }
        }

        block is EquationBlock -> {

        }


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
