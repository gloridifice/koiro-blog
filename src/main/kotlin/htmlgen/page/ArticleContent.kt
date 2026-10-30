package htmlgen.page

import ServerPath
import htmlgen.component.Catalogue
import htmlgen.ImageAssets
import htmlgen.contentImage
import kotlinx.html.ImgLoading
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.br
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.del
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.h5
import kotlinx.html.h6
import kotlinx.html.hr
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.space.SFMFlavourDescriptor
import org.intellij.markdown.lexer.push
import org.intellij.markdown.parser.MarkdownParser
import java.nio.file.Path
import kotlin.collections.plus

class ArticleContent(
    markdownText: String,
    val filePath: Path,
    private val images: ImageAssets,
    private val imageLoading: ImgLoading? = null,
) {
    // The Markdown parser expects LF, including for blank lines that terminate HTML blocks.
    // Keep AST offsets and all text extraction on the same normalized source.
    val markdownText = markdownText.replace("\r\n", "\n").replace('\r', '\n')
    var h1Index = 0
    var h2Index = 0
    var h3Index = 0
    var collectedData = CollectedData(arrayListOf())
    var imageStack = arrayListOf<Pair<ServerPath, String>>()
    var lastIsImage: Boolean = false

    class CollectedData(val headingInfos: ArrayList<Catalogue.HeadingInfo>)

    fun FlowContent.showPostContent(): CollectedData {
        val parser = MarkdownParser(SFMFlavourDescriptor())
        val root = parser.buildMarkdownTreeFromString(markdownText)
        markdownBlock(root)
        if (!imageStack.isEmpty()) {
            renderImageStack()
        }
        return collectedData
    }

    private fun FlowContent.markdownBlock(node: ASTNode) {
        val text = node.getTextInNode(markdownText)
        // Image region
        val isImage =
            node.type == MarkdownElementTypes.PARAGRAPH && node.children.getOrNull(0)?.type == MarkdownElementTypes.IMAGE
        val isEmpty = node.type == MarkdownTokenTypes.EOL || node.getTextInNode(markdownText).isEmpty()
        if (lastIsImage && !isImage && !isEmpty) {
            renderImageStack()
        }
        // 忽略空元素
        if (!isEmpty) {
            lastIsImage = isImage
        }

        when (node.type) {
            MarkdownElementTypes.MARKDOWN_FILE -> {
                for (child in node.children) markdownBlock(child)
            }

            MarkdownElementTypes.ATX_1 -> {
                h1 {
                    id = "heading1_${h1Index}"
                    collectedData.headingInfos.push(Catalogue.HeadingInfo(Catalogue.HeadingType.H1, "", id))
                    attributes["index-text"] = FormatUtils.intToRoman(h1Index + 1)
                    h1Index++
                    renderInlineContent(node)
                }
            }

            MarkdownElementTypes.ATX_2 -> {
                h2 {
                    id = "heading2_${h2Index}"
                    collectedData.headingInfos.push(Catalogue.HeadingInfo(Catalogue.HeadingType.H2, "", id))
                    h2Index++
                    renderInlineContent(node)
                }
            }

            MarkdownElementTypes.ATX_3 -> {
                h3 {
                    id = "heading3_${h3Index}"
                    collectedData.headingInfos.push(Catalogue.HeadingInfo(Catalogue.HeadingType.H3, "", id))
                    h3Index++
                    renderInlineContent(node)
                }
            }

            MarkdownElementTypes.ATX_4 -> h4 { renderInlineContent(node) }
            MarkdownElementTypes.ATX_5 -> h5 { renderInlineContent(node) }
            MarkdownElementTypes.ATX_6 -> h6 { renderInlineContent(node) }

            MarkdownElementTypes.SETEXT_1 -> {
                h1 {
                    id = "heading1_${h1Index}"
                    attributes["index-text"] = FormatUtils.intToRoman(h1Index + 1)
                    h1Index++
                    renderInlineContent(node)
                }
            }

            MarkdownElementTypes.SETEXT_2 -> {
                h2 {
                    id = "heading2_${h2Index}"
                    h2Index++
                    renderInlineContent(node)
                }
            }

            MarkdownElementTypes.PARAGRAPH -> p { renderInlineContent(node) }

            MarkdownElementTypes.BLOCK_QUOTE -> {
                blockQuote {
                    for (child in node.children) {
                        if (child.type != MarkdownTokenTypes.BLOCK_QUOTE && child.type != MarkdownTokenTypes.EOL) {
                            markdownBlock(child)
                        }
                    }
                }
            }

            MarkdownElementTypes.UNORDERED_LIST -> {
                val hasCheckBox = node.children.any {
                    it.type == MarkdownElementTypes.LIST_ITEM && it.children.any { c -> c.type == GFMTokenTypes.CHECK_BOX }
                }
                ul {
                    if (hasCheckBox) classes += "todo_list"
                    for (child in node.children) {
                        if (child.type == MarkdownElementTypes.LIST_ITEM) {
                            li { renderItemContent(child, hasCheckBox) }
                        }
                    }
                }
            }

            MarkdownElementTypes.ORDERED_LIST -> {
                ol {
                    for (child in node.children) {
                        if (child.type == MarkdownElementTypes.LIST_ITEM) {
                            li { renderItemContent(child, false) }
                        }
                    }
                }
            }

            MarkdownElementTypes.CODE_FENCE -> renderCodeFence(node)
            MarkdownElementTypes.CODE_BLOCK -> renderCodeBlock(node)
            MarkdownElementTypes.IMAGE -> pushImage(node)

            MarkdownElementTypes.LINK_DEFINITION -> {}

            MarkdownElementTypes.HTML_BLOCK -> {
                div { unsafe { raw(node.getTextInNode(markdownText).toString()) } }
            }

            GFMElementTypes.TABLE -> renderTable(node)

            GFMElementTypes.BLOCK_MATH -> {
                div {
                    classes += "equation"
                    +node.getTextInNode(markdownText).toString().trim()
                }
            }

            MarkdownTokenTypes.HORIZONTAL_RULE -> {
                hr { classes += "divider_block" }
            }

            else -> {
                for (child in node.children) markdownBlock(child)
            }
        }
    }

    private fun inlineContentChildren(node: ASTNode): List<ASTNode> {
        // Delimiters belong to their formatting node, not to the rendered text.
        // Strip only matched boundaries so literal/unmatched markers survive.
        val delimiterCount = when (node.type) {
            MarkdownElementTypes.EMPH, MarkdownElementTypes.CODE_SPAN, MarkdownElementTypes.LINK_TEXT -> 1
            MarkdownElementTypes.STRONG, GFMElementTypes.STRIKETHROUGH -> 2
            else -> 0
        }
        val children = node.children.drop(delimiterCount).dropLast(delimiterCount)
        return if (node.type == MarkdownTokenTypes.ATX_CONTENT) {
            children.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
                .dropLastWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
        } else children
    }

    private fun FlowContent.renderInlineContent(node: ASTNode) {
        for (child in inlineContentChildren(node)) renderInlineNode(child)
    }

    private fun FlowContent.renderImageStack() {
        when (imageStack.size) {
            0 -> {
                return
            }

            1 -> {
                div {
                    classes += "image_wrapper"
                    img {
                        contentImage(imageStack.first().first, images, imageLoading)
                        this.alt = imageStack.first().second
                    }
                }
            }

            else -> {
                div {
                    classes += "stack-container"
                    imageStack.forEach { (path, alt) ->
                        val size = images.dimensions(path)
                        div {
                            classes += "stack-card"
                            // Size every photo before it loads, without stretching smaller originals.
                            attributes["style"] = "--image-width: ${size.width}px; --image-ratio: ${size.width} / ${size.height}"
                            img {
                                contentImage(path, images, imageLoading)
                                width = size.width.toString()
                                height = size.height.toString()
                                this.alt = alt
                            }
                        }
                    }
                }
            }
        }
        imageStack.clear()
    }

    private fun FlowContent.renderInlineNode(node: ASTNode) {
        when (node.type) {
            MarkdownTokenTypes.TEXT -> +node.getTextInNode(markdownText).toString()

            MarkdownTokenTypes.ATX_CONTENT -> {
                collectedData.headingInfos.lastOrNull()?.let {
                    if (it.content.isEmpty()) it.content = extractText(node).trim()
                }
                renderInlineContent(node)
            }

            MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2 -> {
                // Heading markers are syntax, not content.
            }

            MarkdownTokenTypes.CODE_LINE -> +node.getTextInNode(markdownText).toString()

            MarkdownTokenTypes.HARD_LINE_BREAK -> br

            MarkdownElementTypes.EMPH -> em { renderInlineContent(node) }
            MarkdownElementTypes.STRONG -> strong { renderInlineContent(node) }

            MarkdownElementTypes.CODE_SPAN -> {
                val text = inlineContentChildren(node)
                    .joinToString("") { it.getTextInNode(markdownText) }.replace('\n', ' ')
                // CommonMark removes one padding space only when the span is not all spaces.
                val content = if (text.startsWith(' ') && text.endsWith(' ') && text.any { it != ' ' }) {
                    text.substring(1, text.length - 1)
                } else text
                code { +content }
            }

            MarkdownElementTypes.IMAGE -> pushImage(node)

            MarkdownElementTypes.INLINE_LINK -> renderInlineLink(node)
            MarkdownElementTypes.FULL_REFERENCE_LINK -> renderReferenceLink(node)
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> renderReferenceLink(node)

            MarkdownElementTypes.AUTOLINK -> {
                val url = node.getTextInNode(markdownText).toString().removeSurrounding("<", ">")
                a(href = url) {
                    classes += "link"
                    +url
                }
            }

            MarkdownElementTypes.LINK_TEXT -> renderInlineContent(node)

            GFMElementTypes.STRIKETHROUGH -> del { renderInlineContent(node) }

            GFMElementTypes.INLINE_MATH -> {
                span {
                    classes += "inline_math"
                    +node.getTextInNode(markdownText).toString()
                }
            }

            MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET -> {
            }

            else -> {
                if (node.children.isNotEmpty()) renderInlineContent(node)
                else {
                    val text = node.getTextInNode(markdownText).toString()
                    if (text.isNotEmpty()) +text
                }
            }
        }

    }

    private fun LI.renderItemContent(node: ASTNode, isTodoList: Boolean) {
        if (isTodoList) classes += "todo_list_item"
        val checkBoxNode = node.children.find { it.type == GFMTokenTypes.CHECK_BOX }
        if (checkBoxNode != null) {
            label {
                input {
                    type = InputType.checkBox
                    checked = checkBoxNode.getTextInNode(markdownText).toString().contains("x")
                    disabled = true
                }
            }
        }
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER, GFMTokenTypes.CHECK_BOX, MarkdownTokenTypes.EOL -> {
                }

                else -> markdownBlock(child)
            }
        }
    }

    private fun FlowContent.renderCodeFence(node: ASTNode) {
        val lang =
            node.children.find { it.type == MarkdownTokenTypes.FENCE_LANG }?.getTextInNode(markdownText)?.toString()
        val codeLines = node.children.filter {
                it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.CODE_LINE
            }.joinToString("\n") { it.getTextInNode(markdownText).toString() }.replace("\t", "  ")
        div {
            classes += "code_block"
            div {
                classes += "code_part"
                if (lang != null) {
                    div {
                        classes += arrayOf("code_lang", "rss-ignore")
                        +lang
                    }
                }
                pre {
                    code {
                        if (lang != null) classes += "language-${lang}"
                        +codeLines
                    }
                }
            }
        }
    }

    private fun FlowContent.renderCodeBlock(node: ASTNode) {
        val codeLines = node.children.filter { it.type == MarkdownTokenTypes.CODE_LINE }
            .joinToString("\n") { it.getTextInNode(markdownText).toString() }.replace("\t", "  ")
        div {
            classes += "code_block"
            div {
                classes += "code_part"
                pre {
                    code { +codeLines }
                }
            }
        }
    }

    private fun FlowContent.pushImage(node: ASTNode) {
        val inlineLink = node.children.find { it.type == MarkdownElementTypes.INLINE_LINK }
        val alt = inlineLink!!.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.let { extractText(it) } ?: ""
        val src = inlineLink.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.let { extractText(it) } ?: ""

        val imageServerPath = images.copy(filePath, src)
        imageStack.push(Pair(imageServerPath, alt))
    }

    private fun FlowContent.renderInlineLink(node: ASTNode) {
        val href = node.children.find { it.type == MarkdownElementTypes.LINK_DESTINATION }?.getTextInNode(markdownText)
            ?.toString() ?: ""
        val linkTextNode = node.children.find { it.type == MarkdownElementTypes.LINK_TEXT }
        val linkTitleNode = node.children.find { it.type == MarkdownElementTypes.LINK_TITLE }
        a {
            this.href = href
            classes += "link"
            linkTitleNode?.getTextInNode(markdownText)?.toString()?.trim('"')?.let { title = it }
            if (linkTextNode != null) renderInlineContent(linkTextNode)
            else +href
        }
    }

    private fun FlowContent.renderReferenceLink(node: ASTNode) {
        val linkTextNode = node.children.find { it.type == MarkdownElementTypes.LINK_TEXT }
        val linkLabelNode = node.children.find { it.type == MarkdownElementTypes.LINK_LABEL }
        val href = linkLabelNode?.getTextInNode(markdownText)?.toString()?.trim('[', ']') ?: ""
        a(href = href) {
            classes += "link"
            if (linkTextNode != null) renderInlineContent(linkTextNode)
            else +href
        }
    }

    private fun FlowContent.renderTable(node: ASTNode) {
        div {
            classes += "table"
            table {
                val rows = node.children.filter {
                    it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW
                }
                for (rowNode in rows) {
                    val isHeader = rowNode.type == GFMElementTypes.HEADER
                    tr {
                        if (isHeader) classes += "table_header"
                        val cells = rowNode.children.filter { it.type == GFMTokenTypes.CELL }
                        for (cell in cells) {
                            td {
                                if (isHeader) classes += "table_header"
                                renderInlineContent(cell)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractText(node: ASTNode): String {
        return if (node.children.isEmpty()) {
            node.getTextInNode(markdownText).toString()
        } else {
            inlineContentChildren(node).joinToString("") { extractText(it) }
        }
    }
}