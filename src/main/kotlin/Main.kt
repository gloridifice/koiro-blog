import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import htmlgen.page.home
import com.github.ajalt.mordant.rendering.TextColors.*
import htmlgen.model.*
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

const val OUT_PUT_PATH_STRING = "static/"
val STATIC_PATH = Path(OUT_PUT_PATH_STRING);

class GlobalContext(
    val blogDatabaseData: DatabaseData<BlogPostPage>,
    val devLogDatabaseData: DatabaseData<DevLogPostPage>,
    val activeDatabaseData: DatabaseData<ActivePage>
) {
    val latestPostPage: Post;
    val homeElements: ArrayList<HomeElement>;

    init {
        val postLatest = blogDatabaseData.latestData
        val devLogLatest = devLogDatabaseData.latestData
        latestPostPage = if (postLatest > devLogLatest)
            blogDatabaseData.publishedPages.first()
        else devLogDatabaseData.publishedPages.first();

        homeElements = ArrayList()
        homeElements.addAll(blogDatabaseData.publishedPages.map { BlogElement(it) })
        homeElements.addAll(devLogDatabaseData.publishedPages.map { DevLogElement(it) })
        homeElements.addAll(activeDatabaseData.publishedPages.map { ActiveElement(it) })
        homeElements.sortByDescending { it.getDate() }
    }
}


fun main(args: Array<String>) {
    val blog = readNotionDatabase(Path(NOTION_BLOG_DATABASE_ROOT_PATH), ::BlogPostPage)
    val devLog = readNotionDatabase(Path(NOTION_DEV_LOG_DATABASE_ROOT_PATH), ::DevLogPostPage)
    val active = readNotionDatabase(Path(NOTION_ACTIVE_DATABASE_ROOT_PATH), ::ActivePage)
    val context = GlobalContext(blog, devLog, active);

    createHTML("home") { home(context) }
    createHTML("about") { about(context) }
    createBlogPostPages(context)
    createDevLogPostPages(context)
}

fun createBlogPostPages(context: GlobalContext) {
    // Delete
    val path = Path("${OUT_PUT_PATH_STRING}/post")
    path.forEach { path.toFile().deleteRecursively() }

    // Gen
    context.blogDatabaseData .publishedPages.forEach { pageData ->
        if (!pageData.published) return@forEach

        createHTML(pageData.getStaticHtmlName()) {
            blogPost(pageData, context)
        }
    }
}

fun createDevLogPostPages(context: GlobalContext) {
    // Delete
    val path = Path("${OUT_PUT_PATH_STRING}/devLog")
    path.forEach { path.toFile().deleteRecursively() }

    // Gen pages
    context.devLogDatabaseData.publishedPages.forEach { post ->
        if (!post.published) return@forEach

        createHTML(post.getStaticHtmlName()) {
            devLogPost(post, context)
        }
        post.copyPreviewImageFromNotionDataToStatic()
    }
}

fun createHTML(htmlName: String, block: HTML.() -> Unit = {}) {
    val path = Path("${OUT_PUT_PATH_STRING}${htmlName}.html")
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