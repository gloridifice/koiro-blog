package htmlgen.model

import OUT_PUT_PATH
import notion.api.v1.model.databases.DatabaseProperty
import notiondata.PageData
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class DevLogPost(pageData: PageData) : Post(pageData) {
    override val htmlName get() = "devLog/${uuid}"

    val work: DatabaseProperty.Select.Option? = pageData.page.properties["Work"]?.select;
    val index: Int = pageData.page.properties["Index"]!!.number!!.toInt();
    val previewImageUrl: String?

    override fun getPlainTitle(): String {
        return super.getPlainTitle() + "｜${work?.name} 开发日志 #${"%04d".format(index)}"
    }

    init {
        previewImageUrl = if (pageData.previewImage != null) {
            "/${htmlName}/${pageData.previewImage.name}"
        } else null
    }

    fun genPreview(){
        previewImageUrl?.let {
            pageData.previewImage?.let{ image ->
                val path = Path("${OUT_PUT_PATH}${this.htmlName}/${image.name}")
                println(path)
                path.createParentDirectories()

                path.createFile().toFile().writeBytes(image.byteArray)
            }
        }
    }
}