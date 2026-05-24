import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.github.ajalt.mordant.rendering.TextColors.yellow
import htmlgen.SvgIcons
import htmlgen.component.home_element.ActiveElement
import htmlgen.component.home_element.BlogElement
import htmlgen.component.home_element.DevlogElement
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlinx.serialization.decodeFromString
import htmlgen.component.home_element.HomeElement
import htmlgen.copyImageToStatic
import htmlgen.page.BlogArticlePage
import htmlgen.page.DevlogArticlePage
import htmlgen.page.home.HomeSubPageInfo
import htmlgen.page.home.homePage
import htmlgen.page.home.subpage.AboutHomeSubPage
import htmlgen.page.home.subpage.BlogsHomeSubPage
import htmlgen.page.home.subpage.MainHomeSubPage
import htmlgen.page.home.subpage.PortfolioHomeSubPage
import org.intellij.markdown.lexer.push
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk

class KoiroCatCafe(
    databasePath: Path,
    val outputPath: String
) {
    init {
        KoiroCatCafe.databasePath = databasePath
    }
    companion object {
        var databasePath: Path? = null

        private val mainSubPageInfo = HomeSubPageInfo(ServerPath("home.html"), "主页", SvgIcons.HOME, null)
        private val blogSubPageInfo = HomeSubPageInfo(ServerPath("blogs.html"), "博客", SvgIcons.BLOGS, null)
        private val portfolioSubPageInfo =
            HomeSubPageInfo(ServerPath("portfolio.html"), "项目", SvgIcons.PROJECTS, null)
        private val aboutSubPageInfo = HomeSubPageInfo(ServerPath("about.html"), "关于", SvgIcons.DOG_BARK, null)
        val homeSubPageInfos =
            arrayListOf<HomeSubPageInfo>(mainSubPageInfo, blogSubPageInfo, portfolioSubPageInfo, aboutSubPageInfo)
    }

    /// return header and content without header
    private inline fun <reified T> parseHeader(contentWithHeader: String): Pair<T, String>? {
        val metaIndex = contentWithHeader.indexOf("```toml")
        val hasMeta = metaIndex != -1 && contentWithHeader.take(metaIndex).isBlank()
        if (!hasMeta) {
            return null
        }

        val startIndex = metaIndex + 7
        // 直接从 startIndex 开始查找闭合标签，获取绝对索引
        val endIndex = contentWithHeader.indexOf("```", startIndex)

        // 校验是否存在闭合标签
        if (endIndex == -1) {
            return null
        }

        val headerString = contentWithHeader.substring(startIndex, endIndex).trim()
        val contentString = contentWithHeader.substring(endIndex + 3).trimStart()

        val toml = Toml(
            inputConfig = TomlInputConfig(
                ignoreUnknownNames = true
            )
        )

        return Pair(
            toml.decodeFromString<T>(headerString),
            contentString
        )
    }

    private fun writeHTML(path: Path, block: HTML.() -> Unit) {
        path.createParentDirectories()
        val file = path.toFile()
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
    private fun generateBlogPage(header: BlogHeader, markdownContent: String, sourceFilePath: Path): ServerPath {
        val safeTitle = header.title.toFileSystemSafe()
        val serverPath = "pages/blogs/$safeTitle.html"

        val fsPath = Path(this@KoiroCatCafe.outputPath).resolve(serverPath)

        writeHTML(fsPath) {
            BlogArticlePage(header, markdownContent, sourceFilePath).apply { showPage() }
        }

        return ServerPath(serverPath)
    }

    private fun generateDevlogPage(header: DevlogHeader, markdownContent: String, sourceFilePath: Path): ServerPath {
        val safeTitle = header.title.toFileSystemSafe()
        val serverPath = "pages/devlogs/$safeTitle.html"
        val fsPath = Path(this@KoiroCatCafe.outputPath).resolve(serverPath)

        writeHTML(fsPath) {
            DevlogArticlePage(header, markdownContent, sourceFilePath).apply { showPage() }
        }

        return ServerPath(serverPath)
    }


    private inline fun <reified T> walkDatabaseAndParseHeader(relPath: String, run: (T, String, Path) -> Unit) {
        databasePath!!.resolve(relPath).walk().forEach { path ->
            if (path.isRegularFile() && path.extension == "md") {
                val content = path.readText()
                try {
                    val result = parseHeader<T>(content)!!
                    val (header, restMdString) = result
                    run(header, restMdString, path)
                } catch (e: Exception) {
                    println(path)
                    println(e.toString())
                    throw e;
                }
            }
        }
    }


    fun generateAll() {
        val blogRecords = ArrayList<BlogRecord>()
        val portfolioRecords = ArrayList<PortfolioRecord>()
        val devlogRecords = ArrayList<DevlogRecord>()
        val activeRecords = ArrayList<ActiveRecord>()

        // 生成博客文章
        walkDatabaseAndParseHeader<BlogHeader>("blogs") { header, restMarkdown, sourcePath ->
            if (header.published) {
                val serverPath = generateBlogPage(header, restMarkdown, sourcePath)
                blogRecords.push(BlogRecord(header, serverPath))
            }
        }

        // 生成 devlog 文章
        walkDatabaseAndParseHeader<DevlogHeader>("devlogs") { header, restMarkdown, sourcePath ->
            if (header.published) {
                val previewImagePath = header.previewImagePath?.let { copyImageToStatic(sourcePath, it) }
                val serverPath = generateDevlogPage(header, restMarkdown, sourcePath)
                devlogRecords.push(DevlogRecord(header, serverPath, previewImagePath))
            }
        }

        // 收集 Portfolio
        walkDatabaseAndParseHeader<PortfolioHeader>("portfolio") { header, _, sourcePath ->
            if (header.published) {
                val previewImagePath = copyImageToStatic(sourcePath, header.previewImagePath)
                portfolioRecords.push(PortfolioRecord(header, previewImagePath))
            }
        }

        // 收集 Actives
        walkDatabaseAndParseHeader<ActiveHeader>("actives") { header, restMarkdown, sourcePath ->
            if (header.published) {
                activeRecords.push(ActiveRecord(header, restMarkdown, sourcePath))
            }
        }

        blogRecords.sortByDescending { it.header.date }
        devlogRecords.sortByDescending { it.header.date }
        activeRecords.sortByDescending { it.header.date }

        // 生成 Home Pages
        val elements = arrayListOf<HomeElement>()
        elements += blogRecords.map { BlogElement(it) }
        elements += devlogRecords.map { DevlogElement(it) }
        elements += activeRecords.map { ActiveElement(it) }

        elements.sortByDescending { it.getDate() }

        mainSubPageInfo.page = MainHomeSubPage(elements);
        blogSubPageInfo.page = BlogsHomeSubPage(blogRecords);
        portfolioSubPageInfo.page = PortfolioHomeSubPage(portfolioRecords, devlogRecords);
        aboutSubPageInfo.page = AboutHomeSubPage();

        for (info in homeSubPageInfos) {
            writeHTML(Path(info.serverPath.staticPath)) {
                homePage(info)
            }
        }
    }
}