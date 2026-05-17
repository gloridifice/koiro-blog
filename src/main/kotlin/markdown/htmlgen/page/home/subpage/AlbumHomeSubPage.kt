package markdown.htmlgen.page.home.subpage

import childPath
import isImage
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import markdown.htmlgen.component.AlbumItem
import markdown.htmlgen.component.album
import java.nio.file.Path

class AlbumHomeSubPage: HomeSubPage() {
    override fun DIV.showSubPage() {
        //TODO
        val albumItems = ArrayList<AlbumItem>()
//        STATIC_PATH.childPath("album").toFile().walk().iterator()
//            .asSequence().sortedBy { -it.lastModified() }.forEach {
//                if (it.isFile && it.isImage()) {
//                    albumItems.add(AlbumItem("/album/" + it.name))
//                }
//            }
        div {
            classes += "album_part"
            h2 {
                classes += "reveal"
                +"📷 🏷️"
            }
            album(albumItems)
        }
    }
}