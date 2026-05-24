package htmlgen

import ServerPath
import decodeURL
import KoiroCatCafe
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes

/**
 * @param sourceFilePath 源路径
 * @param imageRelPath 图片相对于源文件的路径 url
 */
fun copyImageToStatic(sourceFilePath: Path, imageRelPath: String): ServerPath {
    val imageDatabasePath = sourceFilePath.resolveSibling(imageRelPath.decodeURL())
    val imageServerPath = ServerPath("images/${KoiroCatCafe.databasePath!!.relativize(imageDatabasePath)}")
    if (imageDatabasePath.exists() && !imageDatabasePath.isDirectory()) {
        val outputFile = File(imageServerPath.staticPath)
        if (!outputFile.exists() || imageDatabasePath.toFile().lastModified() > outputFile.lastModified()) {
            val compressedImage = if (outputFile.extension != "gif") {
                ImageCompressor.compressMemory(imageDatabasePath.readBytes())
            } else {
                // 不压缩 gif
                imageDatabasePath.readBytes()
            }
            Path(imageServerPath.staticPath).createParentDirectories()
            outputFile.writeBytes(compressedImage)
            println("Write Image: <${imageServerPath.staticPath}>")
        }
    }
    return imageServerPath
}