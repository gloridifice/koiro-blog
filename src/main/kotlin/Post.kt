import htmlgen.asLoc
import htmlgen.htmlServerPath
import htmlgen.toNormalString
import notion.api.v1.model.common.Emoji
import notion.api.v1.model.common.Icon
import notion.api.v1.model.pages.Page
import notion.api.v1.model.pages.PageProperty
import java.text.SimpleDateFormat
import java.util.Calendar

open class Post(val page: Page) {
    val pageTitle: List<PageProperty.RichText> = page.properties["Title"]!!.title!!
    val icon: Icon = page.icon
    val published: Boolean = page.properties["Published"]!!.checkbox!!

    val slug: List<PageProperty.RichText>? = page.properties["Slug"]?.richText
    val date: String? = page.properties["Date"]?.lastEditedTime
    val authors: List<String>?

    init {
        val authorsOption = page.properties["Authors"]?.people

        authors = if (authorsOption != null){
            val authorNames = ArrayList<String>()
            authorsOption.forEach { authorNames.add(it.name!!) }
            authorNames
        } else null
    }

    val htmlServerPath get() = htmlServerPath(htmlName.asLoc())
    val assetsDirectoryPath get() = "${OUT_PUT_PATH}${htmlName}/"
    open val htmlName get() = "post/${uuid}"
    val uuid get() = page.id;

    open fun getPlainTitle(): String = pageTitle.toNormalString()
    fun getEmoji(): String{
        val default ="🦆"
        if (icon is Emoji){
            return icon.emoji ?: default
        }
        return default
    }
    fun getLastEditedTimeDay(): String{
        page.lastEditedTime.let {
            return it.split('T')[0]
        }
        return "No date"
    }

    fun getPreviewDisplayDate(): String{
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val date = fmt.parse(page.lastEditedTime)

        val fmtThisYear = SimpleDateFormat("MM-dd")
        val fmtOtherYear = SimpleDateFormat("yyyy-MM-dd")

        val timeCal = Calendar.getInstance()
        val currentCal = Calendar.getInstance()

        timeCal.time = date

        return if (timeCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)){
            fmtThisYear.format(date)
        } else fmtOtherYear.format(date)
    }

    fun getCreatedTimeDay(): String{
        page.createdTime.let {
            return it.split('T')[0]
        }
        return "No date"
    }
}