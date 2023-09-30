import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import notion.api.v1.NotionClient
import page.home
import com.github.ajalt.mordant.rendering.TextColors.*
import page.post
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories

data class BlogContext(val client: NotionClient, val blogPages: List<BlogPage>) {

}

val outputDirectory = "static/"
fun main(args: Array<String>) {
    notionClient { client ->
        val blogPages = getBlogPages(client)
        val context = BlogContext(client, blogPages);

        createHomePage(context)
        createPostPages(context)
    }
}
fun copyDir(src: Path, dest: Path) {
    Files.walk(src).forEach {
        Files.copy(it, dest.resolve(src.relativize(it)),
            StandardCopyOption.REPLACE_EXISTING)
    }
}

fun createHomePage(blogContext: BlogContext) {
    createHTML("home") {
        home(blogContext)
    }
}

fun createPostPages(blogContext: BlogContext) {
    blogContext.blogPages.forEach { page ->
        if (!page.published) return@forEach

        createHTML(page.htmlName) {
            post(page, blogContext.client)
        }
    }
}

fun getBlogPages(client: NotionClient): List<BlogPage> {
    val databaseID = "0ed868dbb56445929e8a993ff70b1750"
    val databasePages = client.queryDatabase(databaseID).results
    databasePages[0].icon
    val blogPages: List<BlogPage> = databasePages.map {
        BlogPage(page = it)
    }
    println(yellow("NotionClient: ") + "Getting blog pages finished, page amount: ${blogPages.size}.")
    return blogPages
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

    val fileWriter = file.writer().appendHTML().html {
        block()
    }
    fileWriter.close()
}