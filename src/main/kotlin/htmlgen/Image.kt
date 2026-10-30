package htmlgen

import ImageCompressor
import ServerPath
import decodeURL
import kotlinx.html.IMG
import kotlinx.html.ImgLoading
import javax.imageio.ImageIO
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
    private val imageSizes = mutableMapOf<ServerPath, ImageSize>()

    /** Read the published image header, not the uncompressed source dimensions. */
    fun dimensions(path: ServerPath): ImageSize = imageSizes.getOrPut(path) {
        val file = outputPath.resolve(path.relativePath).toFile()
        ImageIO.createImageInputStream(file).use { stream ->
            requireNotNull(stream) { "Cannot read image dimensions: $file" }
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Unsupported image format: $file" }
            val reader = readers.next()
            try {
                reader.input = stream
                ImageSize(reader.getWidth(0), reader.getHeight(0))
            } finally {
                reader.dispose()
            }
        }
    }

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
            imageSizes.remove(serverPath)
            println("Write Image: <$output>")
        }
        return serverPath
    }
}

data class ImageSize(val width: Int, val height: Int)

/** A null loading mode preserves the existing rendering outside the home timeline. */
fun IMG.contentImage(path: ServerPath, images: ImageAssets, imageLoading: ImgLoading? = null) {
    src = path.serverPath
    if (imageLoading != null) {
        loading = imageLoading
        attributes["decoding"] = "async"
        val size = images.dimensions(path)
        width = size.width.toString()
        height = size.height.toString()
        // Keep the intrinsic ratio even when an older stylesheet is cached.
        attributes["style"] = "height: auto;"
    }
}
