package htmlgen.model

import childPath
import notion.api.v1.model.pages.Page
import notiondata.Image
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively

/// for portfolio
class ProjectPage(page: Page, parentPath: Path) : PageData(page, parentPath) {
    val projectID = page.properties["Project"]!!.select!!.name
    val url = page.properties["URL"]!!.url
    val title = page.properties["Name"]!!.title!!
    val desc = page.properties["Description"]!!.richText!!

    private val previewImage = notionDataPath.toFile().listFiles()?.find { it.name.startsWith("preview_image") }?.let {
        Image(it.readBytes(), it.name.toString())
    }
    val previewImagePath = if (previewImage != null) {
        getStaticAssetsDirectoryPath().childPath("preview_image_${previewImage.name}")
    } else null


    override fun getStaticHtmlName(): String {
        return "portfolio/${uuid}"
    }

    fun copyPreviewImageFromNotionDataToStatic(){
        previewImagePath?.let { path ->
            path.toFile().deleteRecursively()
            previewImage?.let{ image ->
                path.createParentDirectories()
                path.createFile().toFile().writeBytes(image.byteArray)
            }
        }
    }
}