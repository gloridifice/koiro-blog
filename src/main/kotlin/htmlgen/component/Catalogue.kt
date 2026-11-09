package htmlgen.component

import kotlinx.html.*

class Catalogue(private val headingList: List<HeadingInfo>) {
    data class HeadingInfo(val level: Int, val content: String, val id: String)

    private class Section(val heading: HeadingInfo, val children: MutableList<Section> = mutableListOf())

    private fun sections(): List<Section> {
        val roots = mutableListOf<Section>()
        val ancestors = mutableListOf<Section>()
        for (heading in headingList) {
            while (ancestors.isNotEmpty() && ancestors.last().heading.level >= heading.level) {
                ancestors.removeAt(ancestors.lastIndex)
            }
            val section = Section(heading)
            (ancestors.lastOrNull()?.children ?: roots).add(section)
            ancestors.add(section)
        }
        return roots
    }

    fun FlowContent.show(label: String, contentLanguage: String) {
        val roots = sections()
        if (roots.isEmpty()) return
        nav("article-toc") {
            attributes["aria-label"] = label
            lang = contentLanguage
            column(roots, 0, null)
        }
    }

    // Sibling columns can scroll independently without clipping a descendant column.
    private fun FlowContent.column(sections: List<Section>, depth: Int, parent: HeadingInfo?) {
        ul("article-toc__column") {
            attributes["data-depth"] = depth.toString()
            if (parent != null) {
                id = "toc-children-${parent.id}"
                attributes["data-parent"] = parent.id
                attributes["aria-labelledby"] = "toc-link-${parent.id}"
                hidden = true
            }
            for (section in sections) li {
                a("#${section.heading.id}", classes = "article-toc__link") {
                    id = "toc-link-${section.heading.id}"
                    attributes["aria-label"] = section.heading.content
                    if (depth < 2 && section.children.isNotEmpty()) {
                        attributes["aria-controls"] = "toc-children-${section.heading.id}"
                        attributes["aria-expanded"] = "false"
                    }
                    span("article-toc__tick") { attributes["aria-hidden"] = "true" }
                    span("article-toc__label") { +section.heading.content }
                }
            }
        }
        if (depth < 2) for (section in sections) {
            if (section.children.isNotEmpty()) column(section.children, depth + 1, section.heading)
        }
    }
}
