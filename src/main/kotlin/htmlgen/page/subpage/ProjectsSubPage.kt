package htmlgen.page.subpage

import GlobalContext
import STATIC_PATH
import childPath
import kotlinx.html.DIV
import java.nio.file.Path

class ProjectsSubPage(context: GlobalContext): SubPage() {
    override fun DIV.show() {
        TODO("Not yet implemented")
    }

    override fun getHtmlFilePath(): Path {
        return STATIC_PATH.childPath("projects.html")
    }

    override fun displayName(): String {
        return "项目"
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("projects_page", "scroll_animation")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }
}