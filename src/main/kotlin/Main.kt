import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import htmlgen.page.home
import com.github.ajalt.mordant.rendering.TextColors.*
import htmlgen.page.about
import htmlgen.page.post
import notiondata.DataDatabase
import notiondata.NOTION_ROOT_DATA_PATH
import notiondata.readNotionData
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories

data class BlogContext(val dataDatabase: DataDatabase) {

}

val outputDirectory = "static/"
fun main(args: Array<String>) {
    val dataDatabase = readNotionData(Path(NOTION_ROOT_DATA_PATH))
    val context = BlogContext(dataDatabase);

    createHTML("home") { home(context) }
    createHTML("about"){ about(context) }
    createPostPages(context)
}

fun createPostPages(context: BlogContext) {
    context.dataDatabase.dataPages.forEach { page ->
        if (!page.post.published) return@forEach

        createHTML(page.post.htmlName) {
            post(page, context)
        }
    }
}

fun createHTML(htmlName: String, block: HTML.() -> Unit = {}) {
    val path = Path("${outputDirectory}${htmlName}.html")
    path.createParentDirectories()
    val file = File(path.toUri())
    val isExist = !file.createNewFile()
    val log =
        if (isExist) "File $path already exists."
        else "Generating file $path success."
    println(yellow("CreateHtmlFile: ") + log)

    val fileWriter = file.writer().append("<!DOCTYPE html>").appendHTML().html {
        block()
    }
    fileWriter.close()
}