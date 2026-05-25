package htmlgen.component.home_element

import kotlinx.datetime.LocalDateTime
import kotlinx.html.DIV

interface HomeElement {
    fun DIV.show()
    fun getDate(): LocalDateTime
}