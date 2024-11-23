package htmlgen.model

import OUT_PUT_PATH
import htmlgen.asLoc
import htmlgen.htmlServerPath
import htmlgen.toNormalString
import notion.api.v1.model.common.Emoji
import notion.api.v1.model.common.Icon
import notion.api.v1.model.pages.PageProperty
import notiondata.PageData
import java.text.SimpleDateFormat
import java.util.Calendar

open class Post(val pageData: PageData) {
    val pageTitle: List<PageProperty.RichText> = pageData.page.properties["Title"]!!.title!!
    val icon: Icon = pageData.page.icon
    val published: Boolean = pageData.page.properties["Published"]!!.checkbox!!

    val slug: List<PageProperty.RichText>? = pageData.page.properties["Slug"]?.richText
    val date: String? = pageData.page.properties["Date"]?.date?.start
    val authors: List<String>?

    init {
        val authorsOption = pageData.page.properties["Authors"]?.people

        authors = if (authorsOption != null){
            val authorNames = ArrayList<String>()
            authorsOption.forEach { authorNames.add(it.name!!) }
            authorNames
        } else null
    }

    val htmlServerPath get() = htmlServerPath(htmlName.asLoc())
    val assetsDirectoryPath get() = "${OUT_PUT_PATH}${htmlName}/"
    open val htmlName get() = "post/${uuid}"
    val uuid get() = pageData.page.id;

    open fun getPlainTitle(): String = pageTitle.toNormalString()
    fun getEmoji(): String{
        val default ="🦆"
        if (icon is Emoji){
            return icon.emoji ?: default
        }
        return default
    }
    fun getLastEditedTimeDay(): String{
        pageData.page.lastEditedTime.let {
            return it.split('T')[0]
        }
        return "No date"
    }

    fun getPreviewDisplayDate(): String{
        val fmtThisYear = SimpleDateFormat("MM-dd")
        val fmtOtherYear = SimpleDateFormat("yyyy-MM-dd")

        val timeCal = Calendar.getInstance()
        val currentCal = Calendar.getInstance()

        timeCal.time = pageData.lastEditedTimeDate

        return if (timeCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)){
            fmtThisYear.format(date)
        } else fmtOtherYear.format(date)
    }

    fun getCreatedTimeDay(): String{
        pageData.page.createdTime.let {
            return it.split('T')[0]
        }
        return "No date"
    }
}