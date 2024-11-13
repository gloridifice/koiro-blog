package htmlgen

import childPath
import kotlinx.html.*
import notion.api.v1.model.common.BlockColor
import notion.api.v1.model.common.RichTextColor
import notion.api.v1.model.common.RichTextLinkType
import notion.api.v1.model.pages.PageProperty
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun copyDir(src: Path, dest: Path) {
    Files.walk(src).forEach {
        Files.copy(
            it, dest.resolve(src.relativize(it)),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun HEAD.universalHeadSetting() {
    lang = "zh_CN"
    meta {
        httpEquiv = "Content-Type"
        content = "charset=utf-8"
    }
    meta {
        name ="viewport"
        content = "width=device-width, initial-scale=1.0"
    }
    script {
        src = "/assets/js/header.js"
    }
    linkCSS("reset", "root","color_scheme_v2.dark_mode")
    linkGoogleFont()
}

fun HEAD.linkGoogleFont() {
    link {
        rel = "preconnect"
        href = "https://fonts.googleapis.com"
    }
    link {
        rel = "preconnect"
        href = "https://fonts.gstatic.com"
    }
    link {
        href = "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@300;400;600;700&display=swap"
        rel = "stylesheet"
    }
}

fun HTMLTag.unsafeSVG(svgString: String) {
    unsafe {
        +svgString
    }
}

fun FlowContent.useSVG(svgHref: String) {
    svg {
        unsafe {
            +"<use xlink:href='${svgHref}' />"
        }
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
        richText.mention?.let { mention ->
//            if (mention.type  ) {
//            }
        }
        //todo add css for rich text
        if (richText.href != null) {
            classes += "href"
            onClick = "window.open('${richText.href}')"
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
        +richText.text?.content.orEmpty().replace("\t", "  ")
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

fun downloadImage(urlStr: String, parentPath: Path, name: String): String {
    parentPath.createDirectories()
    val imageData = URL(urlStr).readBytes()
    val suffixName = urlStr.split('?').first().split('.').last()

    val file = parentPath.childPath("$name.$suffixName").toFile()
    file.writeBytes(imageData)

    return file.name
}

fun downloadImageAutoName(urlStr: String, outputPath: String): String {
    val outputPathFinal = if (!outputPath.endsWith('/')) outputPath else outputPath + "/"

    val imageData = URL(urlStr).readBytes()
    val path = Path(outputPathFinal)

    path.createDirectories()

    val nameRaw = urlStr.split('/').last().split('?')
    var imgName = if (nameRaw[0].length > 125) nameRaw[0].substring(0, 125) else nameRaw[0]
    val find = findFile(outputPathFinal + imgName)
    imgName = find.second.split('/').last().split('?').first()
    find.first.writeBytes(imageData)

    return imgName
}

fun findFile(filePath: String): Pair<File, String> {
    if (File(filePath).exists()) {
        val split = filePath.split(".")
        val suffixName = split.last()
        val preName = split.dropLast(1).joinToString("", "", "")

        val splitByUnderline = preName.split('_');
        val countStr = splitByUnderline.last()

        var rest = preName
        var index = 1;
        countStr.toIntOrNull()?.let {
            index = it + 1
            rest = splitByUnderline.dropLast(1).joinToString("")
        }
        return findFile("${rest}_$index.${suffixName}")
    } else {
        var thePath = filePath.replace('%', '_')
        return Pair(File(thePath), thePath);
    }
}

private fun String.asRichText(): List<PageProperty.RichText> =
    listOf(PageProperty.RichText(text = PageProperty.RichText.Text(content = this)))

fun List<PageProperty.RichText>.toNormalString(): String {
    var str = ""
    this.forEach { str += it.plainText }
    return str;
}