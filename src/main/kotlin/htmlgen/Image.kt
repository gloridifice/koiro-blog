package htmlgen

import ImageCompressor
import ServerPath
import decodeURL
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/** Copies article attachments into the same output tree as the generated pages. */
class ImageAssets(databasePath: Path, private val outputPath: Path) {
    private val databaseRoot = databasePath.toAbsolutePath().normalize()

    fun copy(sourceFilePath: Path, imageRelPath: String): ServerPath {
        val source = sourceFilePath.toAbsolutePath().resolveSibling(imageRelPath.decodeURL()).normalize()
        require(source.startsWith(databaseRoot)) { "Image is outside the content directory: $source" }
        require(source.isRegularFile()) { "Missing image referenced by $sourceFilePath: $imageRelPath" }

        val relativePath = databaseRoot.relativize(source).invariantSeparatorsPathString
        val serverPath = ServerPath("images/$relativePath")
        val output = outputPath.resolve(serverPath.relativePath)
        if (!output.isRegularFile() || source.toFile().lastModified() > output.toFile().lastModified()) {
            val bytes = source.readBytes()
            val image = if (source.extension.equals("gif", ignoreCase = true)) bytes
                else ImageCompressor.compressMemory(bytes)
            output.createParentDirectories().writeBytes(image)
            println("Write Image: <$output>")
        }
        return serverPath
    }
}
