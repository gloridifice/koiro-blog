import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

fun Path.childPath(child: String): Path {
    return Path(
        this.pathString + if (this.pathString.endsWith('/')) "" else {
            "/"
        } + child
    )
}

fun Path.hasChildren(): Boolean{
    val files = this.toFile().listFiles()
    return files != null && files.isNotEmpty()
}

fun writeJson(path: Path, content: String) {
    assert(path.endsWith(".json"))
    path.createParentDirectories()
    FileWriter(path.pathString).append(content).close()
}