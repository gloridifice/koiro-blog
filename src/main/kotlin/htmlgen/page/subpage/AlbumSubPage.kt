package htmlgen.page.subpage

import STATIC_PATH
import childPath
import htmlgen.component.AlbumItem
import htmlgen.component.album
import isImage
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import java.nio.file.Path
import kotlin.io.path.Path

class AlbumSubPage: SubPage() {
    override fun DIV.show() {
        val albumItems = ArrayList<AlbumItem>()
        STATIC_PATH.childPath("album").toFile().walk().iterator()
            .asSequence().sortedBy { -it.lastModified() }.forEach {
                if (it.isFile && it.isImage()) {
                    albumItems.add(AlbumItem("/album/" + it.name))
                }
            }
        div {
            classes += "album_part"
            h2 {
                classes += "reveal"
                +"📷 🏷️"
            }
            album(albumItems)
        }
    }

    override fun getHtmlFilePath(): Path {
        TODO("Not yet implemented")
    }

    override fun displayName(): String {
        TODO("Not yet implemented")
    }
}