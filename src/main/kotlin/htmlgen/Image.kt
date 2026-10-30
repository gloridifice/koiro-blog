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
        val source = sourceFilePath.toAbsolutePath().resolveSibling(imageRelPath.substringBefore('#').substringBefore('?').decodeURL()).normalize()
        require(source.isRegularFile()) { "Missing image referenced by $sourceFilePath: $imageRelPath" }
        return copySource(source)
    }

    /** A migration-map source is a filesystem path, not an encoded Markdown URL. */
    fun copySource(path: Path): ServerPath {
        val source = path.toAbsolutePath().normalize()
        require(source.startsWith(databaseRoot)) { "Image is outside the content directory: $source" }
        require(source.isRegularFile()) { "Missing image: $source" }
        val relativePath = databaseRoot.relativize(source).invariantSeparatorsPathString
        val serverPath = ServerPath("images/content/$relativePath")
        val output = outputPath.resolve(serverPath.relativePath)
        val root = outputPath.toAbsolutePath().normalize()
        var ancestor: Path? = output.toAbsolutePath().normalize()
        while (ancestor != null && ancestor.startsWith(root)) {
            require(!java.nio.file.Files.isSymbolicLink(ancestor)) { "Output image symlink: $ancestor" }
            ancestor = ancestor.parent
        }
        if (!output.isRegularFile() || source.toFile().lastModified() > output.toFile().lastModified()) {
            val bytes = source.readBytes()
            val image = if (source.extension.lowercase() in setOf("png", "jpg", "jpeg")) ImageCompressor.compressMemory(bytes)
                else bytes // Preserve GIF animation and non-raster attachments.
            output.createParentDirectories().writeBytes(image)
            imageSizes.remove(serverPath)
            println("Write Image: <$output>")
        }
        return serverPath
    }
}

fun content.ResolvedContent.preview(images: ImageAssets): ServerPath? {
    val override = variant.header.preview
    if (override != null) return images.copy(variant.sourcePath, override)
    val common = when (val m = bundle.metadata) {
        is content.DevlogMetadata -> m.preview
        is content.PortfolioMetadata -> m.preview
        else -> null
    }
    return common?.let { images.copy(bundle.directory.resolve("meta.toml"), it) }
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
