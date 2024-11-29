package htmlgen.page.subpage

import htmlgen.page.HomeSelection
import kotlinx.html.DIV
import serverPathString
import java.nio.file.Path

abstract class SubPage : HomeSelection {
    /** This method shows at the div of 'content' class
     * ```
     *  <div class="contents_wrapper">
     *      <div class="contents">
     *          this.show()
     * ```
     */
    abstract fun DIV.show()
    abstract fun getHtmlFilePath(): Path
    override fun url(): String {
        return getHtmlFilePath().serverPathString()
    }

    open fun getCssNames(): Array<String> = emptyArray()
    open fun getJsNames(): Array<String> = emptyArray()
}