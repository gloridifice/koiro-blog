package htmlgen.model.home

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import java.util.*

interface HomeElement {
    fun DIV.show()
    fun getDate(): Date
}