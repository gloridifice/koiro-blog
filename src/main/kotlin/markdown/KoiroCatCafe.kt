package markdown

import ServerPath
import com.akuleshov7.ktoml.Toml
import com.github.ajalt.mordant.rendering.TextColors.yellow
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlinx.serialization.decodeFromString
import markdown.htmlgen.SvgIcons
import markdown.htmlgen.component.home_element.ActiveElement
import markdown.htmlgen.component.home_element.BlogElement
import markdown.htmlgen.component.home_element.DevlogElement
import markdown.htmlgen.component.home_element.HomeElement
import markdown.htmlgen.page.BlogHtml
import markdown.htmlgen.page.home.HomeSubPageInfo
import markdown.htmlgen.page.home.homePage
import markdown.htmlgen.page.home.subpage.AboutHomeSubPage
import markdown.htmlgen.page.home.subpage.BlogsHomeSubPage
import markdown.htmlgen.page.home.subpage.MainHomeSubPage
import markdown.htmlgen.page.home.subpage.PortfolioHomeSubPage
import org.intellij.markdown.lexer.push
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk

class KoiroCatCafe(
    val databasePath: Path,
    val outputPath: String
) {
    companion object {
        private val mainSubPageInfo = HomeSubPageInfo(ServerPath("home.html"), "主页", SvgIcons.HOME, null)
        private val blogSubPageInfo = HomeSubPageInfo(ServerPath("blogs.html"), "博客", SvgIcons.BLOGS, null)
        private val portfolioSubPageInfo =
            HomeSubPageInfo(ServerPath("portfolio.html"), "项目", SvgIcons.PROJECTS, null)
        private val aboutSubPageInfo = HomeSubPageInfo(ServerPath("about.html"), "关于", SvgIcons.DOG_BARK, null)
        val homeSubPageInfos =
            arrayListOf<HomeSubPageInfo>(mainSubPageInfo, blogSubPageInfo, portfolioSubPageInfo, aboutSubPageInfo)
    }

    /// return header and content without header
    private inline fun<reified T> parseHeader(contentWithHeader: String): Pair<T, String>? {
        val metaIndex = contentWithHeader.indexOf("```toml")
        val hasMeta = metaIndex != -1 && contentWithHeader.take(metaIndex).isBlank()
        if (!hasMeta) {
            return null
        }

        val startIndex = metaIndex + 7;
        val endIndex = contentWithHeader.substring(startIndex, contentWithHeader.length).indexOf("```")
        val metaInfoString = contentWithHeader.substring(startIndex, endIndex)
        return Pair(
            Toml.decodeFromString<T>(metaInfoString),
            contentWithHeader.substring(endIndex + 3, contentWithHeader.length)
        )
    }


    private fun writeHTML(path: Path, block: HTML.() -> Unit) {
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

    /// return server path
    private fun generateBlogPage(header: BlogHeader, contentWithoutHeader: String): ServerPath {
        val serverPath = "pages/blogs/${header.title}.html"
        val fsPath = Path(this@KoiroCatCafe.outputPath).resolve(serverPath)
        if (!fsPath.exists()) fsPath.createFile()

        writeHTML(fsPath) {
            BlogHtml(header, contentWithoutHeader).apply { show() }
        }

        return ServerPath(serverPath)
    }

    private fun generateDevlogPage(header: DevlogHeader, contentWithoutHeader: String): ServerPath {
        val serverPath = "pages/devlogs/${header.title}.html"
        val fsPath = Path(this@KoiroCatCafe.outputPath).resolve(serverPath)
        if (!fsPath.exists()) fsPath.createFile()

        writeHTML(fsPath) {
            //todo
            //BlogHtml(header, contentWithoutHeader).apply { show() }
        }

        return ServerPath(serverPath)
    }


    private inline fun<reified T> walkDatabaseAndParseHeader(relPath: String, run: (T, String) -> Unit) {
        databasePath.resolve(relPath).walk().forEach {
            if (it.isRegularFile() && it.extension == "md") {
                val content = it.readText()
                val result = parseHeader<T>(content) ?: return@forEach
                val (header, restMdString) = result
                run(header, restMdString)
            }
        }
    }


    fun generateAll() {
        val blogRecords = ArrayList<BlogRecord>()
        val portfolios = ArrayList<Portfolio>()
        val devlogRecords = ArrayList<DevlogRecord>()
        val activeRecords = ArrayList<ActiveRecord>()

        // 生成博客文章
        walkDatabaseAndParseHeader<BlogHeader>("blogs") { header, restMarkdown ->
            val serverPath = generateBlogPage(header, restMarkdown)
            blogRecords.push(BlogRecord(header, serverPath))
        }

        // 生成 devlog 文章
        walkDatabaseAndParseHeader<DevlogHeader>("devlogs") { header, restMarkdown ->
            val serverPath = generateDevlogPage(header, restMarkdown)
            devlogRecords.push(DevlogRecord(header, serverPath))
        }

        // 收集 Portfolio
        walkDatabaseAndParseHeader<Portfolio>("portfolios") { header, _  ->
            portfolios.push(header)
        }

        // 收集 Actives
        walkDatabaseAndParseHeader<ActiveHeader>("actives") { header, restMarkdown  ->
            activeRecords.push(ActiveRecord(header, restMarkdown))
        }

        // 生成 Home Pages
        val elements = arrayListOf<HomeElement>()
        elements += blogRecords.map { BlogElement(it) }
        elements += devlogRecords.map { DevlogElement(it) }
        elements += activeRecords.map { ActiveElement(it) }

        mainSubPageInfo.page = MainHomeSubPage(elements);
        blogSubPageInfo.page = BlogsHomeSubPage(blogRecords);
        portfolioSubPageInfo.page = PortfolioHomeSubPage(portfolios, devlogRecords);
        aboutSubPageInfo.page = AboutHomeSubPage();

        for (info in homeSubPageInfos) {
            writeHTML(Path(info.serverPath.staticPath())) {
                homePage(info)
            }
        }
    }
}