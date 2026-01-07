package htmlgen.component

import childPath
import htmlgen.*
import htmlgen.page.PostContext
import kotlinx.html.*
import notion.api.v1.model.blocks.*
import notion.api.v1.model.common.Emoji
import notion.api.v1.model.pages.PageProperty
import notiondata.BookmarkDataBlock
import notiondata.DataBlock
import htmlgen.model.PageData
import htmlgen.page.LocalContext
import kotlinx.html.li
import kotlinx.html.ul
import notiondata.ImageDataBlock
import serverPathString
import java.net.URL
import java.util.*
import kotlin.io.path.createParentDirectories

fun FlowContent.tryGenerateChildren(
    dataBlock: DataBlock,
    pageData: PageData,
    postContext: PostContext, ignoreEmptyBlock: Boolean = false
) {
    dataBlock.children?.let { notionBlocks(it, pageData, postContext, ignoreEmptyBlock) }
}

fun FlowContent.notionBlocks(
    blocks: List<DataBlock>,
    page: PageData,
    postContext: PostContext,
    ignoreEmptyBlock: Boolean = false
) {
    val localContext = LocalContext()
    val size = blocks.size
    blocks.forEachIndexed { i, block ->
        notionBlock(
            block,
            page,
            postContext,
            ignoreEmptyBlock,
            localContext,
            i == size - 1
        )
    }
}


inline fun <reified T> FlowContent.collectListBlock(
    block: Block,
    list: MutableList<T>,
    isInState: Boolean,
    isLastBlock: Boolean,
    function: FlowContent.(MutableList<T>) -> Unit
): Boolean {
    var ret = isInState;
    if (block is T) {
        if (!isInState) {
            ret = true
        }
        list.add(block)
    }

    if(block !is T || isLastBlock){
        if (isInState) {
            function(list)

            ret = false
            list.clear()
        }
    }
    return ret
}

fun FlowContent.notionBlock(
    dataBlock: DataBlock,
    pageData: PageData,
    postContext: PostContext,
    ignoreEmptyBlock: Boolean = false,
    localContext: LocalContext,
    isLastBlock: Boolean
) {
    val block = dataBlock.block

    localContext.isInTodoList =
        collectListBlock(block, localContext.todoList, localContext.isInTodoList, isLastBlock) { list ->
            ul {
                classes += "todo_list"
                for (block in list)
                    li {
                        classes += "todo_list_item"
                        label {
                            input {
                                checked = block.toDo.checked
                                disabled = true
                                type = InputType.checkBox
                            }
                            block.toDo.richText?.let {
                                richTexts(it)
                            }
                        }
                    }
            }
        }

    localContext.isInNumberedList =
        collectListBlock(block, localContext.numberedList, localContext.isInNumberedList, isLastBlock) { list ->
            ol {
                for (block in list)
                    li {
                        block.numberedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                        richTexts(block.numberedListItem.richText)
                        tryGenerateChildren(dataBlock, pageData, postContext)
                    }
            }
        }

    localContext.isInBulletedList =
        collectListBlock(block, localContext.bulletedList, localContext.isInBulletedList, isLastBlock) { list ->
            ul {
                for (block in list)
                    li {
                        block.bulletedListItem.color?.let { color -> colorClass(color)?.let { classes += it } }
                        richTexts(block.bulletedListItem.richText)
                        tryGenerateChildren(dataBlock, pageData, postContext)
                    }
            }
        }

    when (block) {
        is ParagraphBlock -> {
            if (!(ignoreEmptyBlock && block.paragraph.richText.isEmpty())) {
                p {
                    block.paragraph.color?.let { color -> colorClass(color)?.let { classes += it } }
                    richTexts(block.paragraph.richText)
                    tryGenerateChildren(dataBlock, pageData, postContext)
                }
            }
        }

        is HeadingOneBlock -> {
            h1 {
                id = "heading1_${postContext.h1Index}"
                attributes["index-text"] = FormatUtils.intToRoman(postContext.h1Index + 1)
                postContext.h1Index++
                block.heading1.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading1.richText)
            }
        }

        is HeadingTwoBlock -> {
            h2 {
                id = "heading2_${postContext.h2Index}"
                postContext.h2Index++
                block.heading2.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading2.richText)
            }
        }

        is HeadingThreeBlock -> {
            h3 {
                id = "heading3_${postContext.h3Index}"
                postContext.h3Index++
                block.heading3.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.heading3.richText)
            }
        }

        is QuoteBlock -> {
            blockQuote {
                block.quote?.color?.let { color -> colorClass(color)?.let { classes += it } }
                block.quote?.richText?.let { richTexts(it) }
            }
        }

        is ColumnListBlock -> {
            div {
                classes += "column_list"
                tryGenerateChildren(dataBlock, pageData, postContext)
            }
        }

        is ColumnBlock -> {
            div {
                classes += "column"
                tryGenerateChildren(dataBlock, pageData, postContext)
            }
        }

        is ToggleBlock -> {

        }

        is CalloutBlock -> {
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

        is DividerBlock -> {
            hr {
                classes += "divider_block"
            }
        }

        is CodeBlock -> {
            block.code?.let { code ->
                div {
                    classes += "code_block"
                    div {
                        classes += "code_part"
                        code.language?.let {
                            div {
                                classes += arrayOf("code_lang", "rss-ignore")
                                +it
                            }
                        }
                        pre {
                            code {
                                code.language?.let {
                                    classes += "language-${it}"
                                }
                                +(code.richText?.let { it[0].plainText } ?: " ").replace("\t", "  ")
                            }
                        }
//                        div {
//                            classes += "code_part_text"
//                            code.richText?.let { richTexts(it) }
//                        }
                    }
                    div {
                        classes += "caption"
                        code.caption?.let { richTexts(it) }
                    }
                }
            }
        }

        is ImageBlock -> {
            if (dataBlock is ImageDataBlock)
                block.image?.let { image ->
                    image.file?.url?.let {
                        val path = dataBlock.image?.let {
                            val imgName = dataBlock.image.name
                            val path = pageData.getStaticAssetsDirectoryPath().childPath(imgName)
                            path.createParentDirectories()
                            path.toFile().writeBytes(dataBlock.image.byteArray)
                            path
                        }

                        div {
                            classes += "image_wrapper"
                            img {
                                src = path?.serverPathString() ?: ""
                            }
                            caption(image.caption)
                        }
                    }
                }
        }

        is BookmarkBlock if dataBlock is BookmarkDataBlock -> {
            block.bookmark?.let {
                div {
                    classes += "caption_block"
                    a {
                        classes += arrayOf("bookmark", "button")

                        val url = it.url ?: ""
                        href = it.url ?: ""

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
                    caption(it.caption)
                }
            }
        }

        is EquationBlock -> {
            block.equation?.let {
                div {
                    classes += "equation"
                    +"$$${it.expression}$$"
                }
            }
        }

        is TableBlock -> {
            div {
                classes += "table"
                val hasHeaderColumn = block.table.hasColumnHeader;
                val hasRowHeader = block.table.hasRowHeader;
                table {
                    dataBlock.children?.forEachIndexed { rowIndex, rowDataBlock ->
                        tr {
                            if (rowIndex == 0 && hasRowHeader) classes += "table_header"
                            val rowBlock = rowDataBlock.block;
                            if (rowBlock is TableRowBlock) {
                                rowBlock.tableRow.cells.forEachIndexed() { i, cell ->
                                    td {
                                        if (i == 0 && hasHeaderColumn) classes += "table_header"
                                        +cell.toPlainString()
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

fun FlowContent.bilibiliVideo(bvString: String) {
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