import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import com.github.ajalt.mordant.rendering.TextColors.*
import htmlgen.model.*
import htmlgen.page.*
import htmlgen.page.HomeSelection
import htmlgen.page.subpage.*
import notiondata.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

const val OUT_PUT_PATH_STRING = "static/"
val STATIC_PATH = Path(OUT_PUT_PATH_STRING);
val GLOBAL_CONTEXT: GlobalContext = readData();

val HOME_SELECTIONS: Array<HomeSelection> = arrayOf(
    MainSubPage(GLOBAL_CONTEXT),
    BlogsSubPage(GLOBAL_CONTEXT),
    PortfolioSubPage(GLOBAL_CONTEXT),
    AboutSubPage()
)

fun readData(): GlobalContext{
    val blog = readNotionDatabase(Path(NOTION_BLOG_DATABASE_ROOT_PATH), ::BlogPostPage)
    val devLog = readNotionDatabase(Path(NOTION_DEV_LOG_DATABASE_ROOT_PATH), ::DevLogPostPage)
    val active = readNotionDatabase(Path(NOTION_ACTIVE_DATABASE_ROOT_PATH), ::ActivePage)
    val portfolio = readNotionDatabase(Path(NOTION_PORTFOLIO_DATABASE_ROOT_PATH), ::ProjectPage)
    return GlobalContext(blog, devLog, active, portfolio)
}

fun main(args: Array<String>) {
    // Create home pages
    HOME_SELECTIONS.mapNotNull { it as? SubPage }.forEach { subpage ->
        createHTML(subpage.getHtmlFilePath()) {
            homePage(subpage) {
                with(subpage) {
                    this@homePage.show()
                }
            }
        }
    }

    createBlogPostPages(GLOBAL_CONTEXT)
    createDevLogPostPages(GLOBAL_CONTEXT)
}

fun createBlogPostPages(context: GlobalContext) {
    // Delete
    val path = Path("${OUT_PUT_PATH_STRING}/post")
    path.forEach { path.toFile().deleteRecursively() }

    // Gen
    context.blogDatabaseData.publishedPages.forEach { pageData ->
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

fun createHTML(path: Path, block: HTML.() -> Unit) {
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

fun createHTML(htmlName: String, block: HTML.() -> Unit) {
    val path = Path("${OUT_PUT_PATH_STRING}${htmlName}.html")
    createHTML(path, block)
}