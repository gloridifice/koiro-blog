import kotlinx.html.*
import notion.api.v1.model.common.BlockColor
import notion.api.v1.model.common.RichTextColor
import notion.api.v1.model.pages.PageProperty

fun HEAD.universalHeadSetting() {
    lang = "zh_CN"
    meta {
        httpEquiv = "Content-Type"
        content = "charset=utf-8"
    }
    script {
        src = "/assets/js/header.js"
    }
    linkCSS("root")
}

fun HTMLTag.unsafeSVG(svgString: String) {
    unsafe {
        +svgString
    }
}

fun HEAD.linkCSS(vararg cssNames: String) {
    for (cssName in cssNames) {
        link {
            rel = "stylesheet"
            href = cssServerPath(cssName.asLoc())
        }
    }
}

fun resourcesServerPath(loc: Loc): String = "/assets/resources/$loc"
fun cssServerPath(loc: Loc): String = "/assets/css/$loc" + if (loc.loc.contains(".css")) "" else ".css"
fun htmlServerPath(loc: Loc): String = "/$loc" + if (loc.loc.contains(".html")) "" else ".html"
fun jsServerPath(loc: String): String = "/assets/js/$loc" + if (loc.contains(".js")) "" else ".js"

fun String.asLoc(): Loc = Loc(this)

data class Loc(val loc: String) {
    override fun toString(): String {
        return loc
    }
}

fun FlowContent.richTexts(richTexts: List<PageProperty.RichText>) {
    richTexts.forEach {
        richText(it)
    }
}

fun FlowContent.richText(richText: PageProperty.RichText) {
    t {
        //todo add css for rich text
        if (richText.href != null) {
            classes += "href"
            onClick += "window.open('${richText.href}')"
        }
        val annotation = richText.annotations
        if (annotation != null) {
            when {
                annotation.bold == true -> {
                    classes += "bold"
                }

                annotation.code == true -> {
                    classes += "code"
                }

                annotation.color != null -> {
                    colorClass(annotation.color)?.let { classes += it }
                }

                annotation.italic == true -> {
                    classes += "italic"
                }

                annotation.underline == true -> {
                    classes += "underline"
                }

                annotation.strikethrough == true -> {
                    classes += "strikethrough"
                }
            }
        }
        +richText.plainText.orEmpty()
    }
}

fun colorClass(color: RichTextColor?): String? {
    if (color != null && color.name != "Default") {
        return "color_${color.name.lowercase()}"
    }
    return null;
}

fun colorClass(color: BlockColor?): String? {
    if (color != null && color.name != "Default") {
        return "color_${color.name.lowercase()}"
    }
    return null;
}

@HtmlTagMarker
inline fun FlowContent.t(classes: String? = null, crossinline block: T.() -> Unit = {}): Unit =
    T(attributesMapOf("class", classes), consumer).visit(block)

@Suppress("unused")
open class T(initialAttributes: Map<String, String>, override val consumer: TagConsumer<*>) :
    HTMLTag("t", consumer, initialAttributes, null, false, false), HtmlBlockInlineTag {

}
