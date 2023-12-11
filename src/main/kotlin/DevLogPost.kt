import notion.api.v1.model.databases.DatabaseProperty
import notiondata.DataPage
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class DevLogPost(val dataPage: DataPage) : Post(dataPage.page) {
    override val htmlName get() = "devLog/${uuid}"

    val work: DatabaseProperty.Select.Option? = dataPage.page.properties["Work"]?.select;
    val index: Int = dataPage.page.properties["Index"]!!.number!!.toInt();
    val previewImageUrl: String?

    override fun getPlainTitle(): String {
        return super.getPlainTitle() + "｜${work?.name} 开发日志 #${"%04d".format(index)}"
    }

    init {
        previewImageUrl = if (dataPage.previewImage != null) {
            "/${htmlName}/${dataPage.previewImage.name}"
        } else null
    }

    fun genPreview(){
        previewImageUrl?.let {
            dataPage.previewImage?.let{ image ->
                val path = Path("${OUT_PUT_PATH}${this.htmlName}/${image.name}")
                println(path)
                path.createParentDirectories()

                path.createFile().toFile().writeBytes(image.byteArray)
            }
        }
    }
}