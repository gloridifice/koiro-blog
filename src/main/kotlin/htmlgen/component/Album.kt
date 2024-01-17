package htmlgen.component

import kotlinx.html.*

class AlbumItem(
    val src: String
){

}

fun FlowContent.album(items: List<AlbumItem>){
    div {
        classes += "album_container"
        classes += "reveal"

        items.forEach {
            div {
                img {
                    classes += "album_img"
                    src = it.src
                }
//                p { +"todo here" }
            }
        }
    }
}