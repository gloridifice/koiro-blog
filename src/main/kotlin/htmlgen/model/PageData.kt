package htmlgen.model

import OUT_PUT_PATH_STRING
import STATIC_PATH
import childPath
import htmlgen.parseNotionDateString
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
    private val publishedProperty = page.properties["Published"]!!
    val published: Boolean = publishedProperty.checkbox!!
    val uuid get() = page.id;

    init {
        dataBlocks = readChildrenBlocks(notionDataPath)
        lastEditedTimeDate = parseNotionDateString(page.lastEditedTime)!!
        createdTimeDate = parseNotionDateString(page.createdTime)!!
    }

    abstract fun getStaticHtmlName(): String;
    fun getStaticAssetsDirectoryPath(): Path {
        return STATIC_PATH.childPath(getStaticHtmlName())
    }
}