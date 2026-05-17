package markdown.htmlgen.page.home.subpage

import kotlinx.html.DIV

abstract class HomeSubPage {
    /** This method shows at the div of 'content' class
     * ```
     *  <div class="contents_wrapper">
     *      <div class="contents">
     *          this.show()
     * ```
     */
    abstract fun DIV.showSubPage()
    open fun getCssNames(): Array<String> = emptyArray()
    open fun getJsNames(): Array<String> = emptyArray()
}