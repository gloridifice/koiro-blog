import net.coobird.thumbnailator.Thumbnails
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.max

object ImageCompressor {
    fun compressFile(file: File): ByteArray {
        val imageInfo = ImageIO.read(file)
        return compress(file.inputStream(), imageInfo.width, imageInfo.height, file.length() / 1024.0)
    }
    fun compressMemory(bytes: ByteArray): ByteArray {
        val imageInfo = ImageIO.read(bytes.inputStream())
        return compress(bytes.inputStream(), imageInfo.width, imageInfo.height, bytes.size / 1024.0)
    }
    fun compress(inputStream: InputStream, width: Int, height: Int, fileSizeKB: Double): ByteArray {
        val max = max(width, height)
        val scale = if (max > 2048.0) 2048.0 / max else 1.0
        val fileSize = fileSizeKB;
        val quality = when {
            fileSize > 2048.0 -> 0.7
            fileSize > 1024.0 -> 0.8
            fileSize > 512.0 -> 0.9
            else -> 1.0
        }

        val byteArrayStream = ByteArrayOutputStream()
        Thumbnails.of(inputStream).scale(scale).outputQuality(quality).toOutputStream(byteArrayStream)

        return byteArrayStream.toByteArray()
    }
}