package markdown.htmlgen.component.home_element

import kotlinx.datetime.LocalDate
import kotlinx.html.DIV
import java.util.*

interface HomeElement {
    fun DIV.show()
    fun getDate(): LocalDate
}