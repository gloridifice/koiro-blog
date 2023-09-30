package page

import BlogPage
import colorClass
import kotlinx.html.*
import linkCSS
import notion.api.v1.NotionClient
import notion.api.v1.model.blocks.*
import richTexts
import universalHeadSetting

fun HTML.post(page: BlogPage, client: NotionClient){
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
            div{
                classes += "page_description"
                h1 {
                    classes += "title"
                    +"${page.getEmoji()} ${page.getPlainTitle()}"
                }
                div {
                    classes += "sub_info"
                    p{
                        classes += "date"
                        +page.getDate()
                    }
                    p {
                        classes += "type"
                        +page.type.name!!
                    }
                }
            }
            div {
                classes += "page_contents"
                notionBlocks(blocks)
            }
        }
    }
}

fun FlowContent.notionBlocks(block: List<Block>){
    block.forEach {
        notionBlock(it)
    }
}
fun FlowContent.notionBlock(block: Block) {
    when {
        //todo
        block is ParagraphBlock -> {
            p {
                block.paragraph.children?.forEach { notionBlock(it) }
                block.paragraph.color?.let { color -> colorClass(color)?.let { classes += it } }
                richTexts(block.paragraph.richText)
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

        }

        block is NumberedListItemBlock -> {

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

        block is ImageBlock -> {

        }

        block is BookmarkBlock -> {

        }

        block is EquationBlock -> {

        }
    }
}
