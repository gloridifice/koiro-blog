import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import htmlgen.page.home
import com.github.ajalt.mordant.rendering.TextColors.*
import htmlgen.page.about
import htmlgen.page.blogPost
import htmlgen.page.devLogPost
import notiondata.DataDatabase
import notiondata.NOTION_BLOG_DATABASE_ROOT_PATH
import notiondata.NOTION_DEV_LOG_DATABASE_ROOT_PATH
import notiondata.readNotionDatabase
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories

class BlogContext(
    val blogDataDatabase: DataDatabase,
    val devLogDataDatabase: DataDatabase,
    ){
    val latestPostPage: Post;

    init {
        val postLatest = blogDataDatabase.latestData
        val devLogLatest = devLogDataDatabase.latestData
        latestPostPage = if (postLatest > devLogLatest)
            blogDataDatabase.publishedPages.first().blogPost()
        else devLogDataDatabase.publishedPages.first().devLogPost();
    }
}



const val OUT_PUT_PATH = "static/"
fun main(args: Array<String>) {
    val blog = readNotionDatabase(Path(NOTION_BLOG_DATABASE_ROOT_PATH))
    val devLog = readNotionDatabase(Path(NOTION_DEV_LOG_DATABASE_ROOT_PATH))
    val context = BlogContext(blog, devLog);

    createHTML("home") { home(context) }
    createHTML("about"){ about(context) }
    createBlogPostPages(context)
    createDevLogPostPages(context)
}

fun createBlogPostPages(context: BlogContext) {
    context.blogDataDatabase.publishedPages.forEach { page ->
        val post = BlogPost(page.page)
        if (!post.published) return@forEach

        createHTML(post.htmlName) {
            blogPost(page, context)
        }
    }
}

fun createDevLogPostPages(context: BlogContext) {
    context.devLogDataDatabase.publishedPages.forEach { page ->
        val post = page.devLogPost()
        if (!post.published) return@forEach

        createHTML(post.htmlName) {
            devLogPost(page, context)
        }
        post.genPreview()
    }
}

fun createHTML(htmlName: String, block: HTML.() -> Unit = {}) {
    val path = Path("${OUT_PUT_PATH}${htmlName}.html")
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