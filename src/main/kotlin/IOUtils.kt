import java.io.File
import java.io.FileWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

fun String.decodeURL() =  URLDecoder.decode(this.replace("+", "%2B"), "UTF-8")
fun String.encodeURL() = URLEncoder.encode(this, "UTF-8")

fun String.toFileSystemSafe(): String {
    if (this.isBlank()) return "_"

    // 移除系统保留字符: \ / : * ? " < > | \u0000
    val reservedChars = Regex("[\\\\/:*?\"<>|\\u0000]")
    var safe = this.replace(reservedChars, "")

    // 移除控制字符及不可见字符
    safe = safe.replace(Regex("[\\p{Cntrl}&&[^\\r\\n\\t]]"), "")

    // 移除 Windows 保留文件名 (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
    val windowsReservedNames = Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$", RegexOption.IGNORE_CASE)
    if (windowsReservedNames.matches(safe)) {
        safe = "_$safe"
    }

    // 限制长度 (主流文件系统单文件名前限 255 字节，此处保守限制 120 字符)
    if (safe.length > 120) {
        safe = safe.substring(0, 120)
    }

    // 确保不以点或空格结尾 (Windows 限制)
    safe = safe.trimEnd('.', ' ')

    return safe.ifBlank { "_" }
}

fun Path.childPath(child: String): Path {
    return Path(
        this.pathString + if (this.pathString.endsWith('/')) "" else {
            "/"
        } + child
    )
}

/// remove the "static/" ahead
fun Path.serverPathString(): String {
    return "/" + this.toString().removePrefix("static/")
}

fun Path.hasChildren(): Boolean{
    val files = this.toFile().listFiles()
    return files != null && files.isNotEmpty()
}

fun writeJson(path: Path, content: String) {
    path.createParentDirectories()
    FileWriter(path.pathString).append(content).close()
}

fun File.isImage(): Boolean{
    val ext = arrayOf("jpg", "jpeg", "png", "gif", "webp")
    return this.isFile && ext.contains(this.extension)
}