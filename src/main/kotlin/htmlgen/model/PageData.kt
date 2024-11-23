package htmlgen.model

import OUT_PUT_PATH_STRING
import STATIC_PATH
import childPath
import notion.api.v1.model.pages.Page
import notiondata.DataBlock
import notiondata.readChildrenBlocks
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

abstract class PageData(
    val page: Page,
    parentPath: Path,
) {
    val dataBlocks: List<DataBlock>?

    val lastEditedTimeDate: Date;
    val createdTimeDate: Date;
    val notionDataPath = parentPath.childPath(page.id)
    val uuid get() = page.id;

    init {
        dataBlocks = readChildrenBlocks(notionDataPath)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        lastEditedTimeDate = fmt.parse(page.lastEditedTime)
        createdTimeDate = fmt.parse(page.createdTime)
    }

    abstract fun getStaticHtmlName(): String;
    fun getStaticAssetsDirectoryPath(): Path {
        return STATIC_PATH.childPath(getStaticHtmlName())
    }
}