package notiondata

import childPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import notion.api.v1.NotionClient
import notion.api.v1.request.blocks.RetrieveBlockRequest
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists


interface TaskResult {
    fun run()
}

fun downloadImageTask(
    scope: CoroutineScope,
    urlString: String,
    parentPath: Path,
    name: String
): Deferred<DownloadImageResult> {
    return scope.async {
        val suffixName = urlString.split('?').first().split('.').last()
        DownloadImageResult(URL(urlString).readBytes(), parentPath, name, suffixName)
    }
}

class DownloadImageResult(val data: ByteArray, val parentPath: Path, val name: String, val suffixName: String) :
    TaskResult {
    override fun run() {
        parentPath.createDirectories()
        val file = parentPath.childPath("$name.$suffixName").toFile()
        file.writeBytes(data)
    }
}