package htmlgen.model

import childPath
import notion.api.v1.model.databases.DatabaseProperty
import notion.api.v1.model.pages.Page
import notiondata.Image
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class DevLogPostPage(page: Page, parentPath: Path) : PostPage(page, parentPath) {
    val work: DatabaseProperty.Select.Option? = page.properties["Work"]?.select;
    val index: Int = page.properties["Index"]!!.number!!.toInt();
    val previewImage: Image?
    val previewImagePath: Path?

    override fun getPlainTitle(): String {
        return super.getPlainTitle() + "｜${work?.name} 开发日志 #${"%04d".format(index)}"
    }

    override fun getStaticHtmlName(): String {
        return "devLog/${uuid}"
    }

    init {
        previewImage = notionDataPath.toFile().listFiles()?.find { it.name.startsWith("preview_image") }?.let {
            Image(it.readBytes(), it.name.toString())
        }
        previewImagePath = if (previewImage != null) {
            getStaticAssetsDirectoryPath().childPath("preview_image_${previewImage.name}")
        } else null
    }

    fun copyPreviewImageFromNotionDataToStatic(){
        previewImagePath?.let { path ->
            previewImage?.let{ image ->
                println(path)
                path.createParentDirectories()
                path.createFile().toFile().writeBytes(image.byteArray)
            }
        }
    }
}