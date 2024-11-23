import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import htmlgen.page.home
import com.github.ajalt.mordant.rendering.TextColors.*
import htmlgen.model.BlogPost
import htmlgen.model.Post
import htmlgen.model.home.ActiveElement
import htmlgen.model.home.BlogElement
import htmlgen.model.home.DevLogElement
import htmlgen.model.home.HomeElement
import htmlgen.page.about
import htmlgen.page.blogPost
import htmlgen.page.devLogPost
import notiondata.*
import java.io.File
import kotlin.io.path.*

class GlobalContext(
    val blogDataDatabase: DataDatabase,
    val devLogDataDatabase: DataDatabase,
    val activeDataDatabase: DataDatabase
) {
    val latestPostPage: Post;
    val homeElements: ArrayList<HomeElement>;

    init {
        val postLatest = blogDataDatabase.latestData
        val devLogLatest = devLogDataDatabase.latestData
        latestPostPage = if (postLatest > devLogLatest)
            blogDataDatabase.publishedPages.first().blogPost()
        else devLogDataDatabase.publishedPages.first().devLogPost();

        homeElements = ArrayList()
        homeElements.addAll(blogDataDatabase.publishedPages.map { BlogElement(it.blogPost()) })
        homeElements.addAll(devLogDataDatabase.publishedPages.map { DevLogElement(it.devLogPost()) })
        homeElements.addAll(activeDataDatabase.publishedPages.map { ActiveElement(it) })
        homeElements.sortBy { it.getDate() }
    }
}


const val OUT_PUT_PATH = "static/"
fun main(args: Array<String>) {
    val blog = readNotionDatabase(Path(NOTION_BLOG_DATABASE_ROOT_PATH))
    val devLog = readNotionDatabase(Path(NOTION_DEV_LOG_DATABASE_ROOT_PATH))
    val active = readNotionDatabase(Path(NOTION_ACTIVE_DATABASE_ROOT_PATH))
    val context = GlobalContext(blog, devLog, active);

    createHTML("home") { home(context) }
    createHTML("about") { about(context) }
    createBlogPostPages(context)
    createDevLogPostPages(context)
}

fun createBlogPostPages(context: GlobalContext) {
    // Delete
    val path = Path("${OUT_PUT_PATH}/post")
    path.forEach { path.toFile().deleteRecursively() }

    // Gen
    context.blogDataDatabase.publishedPages.forEach { pageData ->
        val post = BlogPost(pageData)
        if (!post.published) return@forEach

        createHTML(post.htmlName) {
            blogPost(pageData, context)
        }
    }
}

fun createDevLogPostPages(context: GlobalContext) {
    // Delete
    val path = Path("${OUT_PUT_PATH}/devLog")
    path.forEach { path.toFile().deleteRecursively() }

    // Gen pages
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