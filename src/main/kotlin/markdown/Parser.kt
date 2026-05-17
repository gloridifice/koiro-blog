package markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.space.SFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

class Parser {

    fun parse() {
        val parser = MarkdownParser(SFMFlavourDescriptor())
        val tree = parser.buildMarkdownTreeFromString("")
        for (node in tree.children) {
            when(node.type) {
                MarkdownElementTypes.ATX_1 -> {
                    node.getTextInNode("")

                }
            }
            node.type == MarkdownElementTypes.ATX_1



        }
    }
}