package htmlgen.component.home_element

import kotlinx.datetime.LocalDateTime
import kotlinx.html.DIV
import kotlinx.html.ImgLoading

interface HomeElement {
    fun DIV.show(imageLoading: ImgLoading = ImgLoading.eager)
    fun getDate(): LocalDateTime
}