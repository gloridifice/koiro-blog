package htmlgen.model

import htmlgen.asLoc
import htmlgen.htmlServerPath
import htmlgen.toNormalString
import notion.api.v1.model.common.Emoji
import notion.api.v1.model.common.Icon
import notion.api.v1.model.pages.Page
import notion.api.v1.model.pages.PageProperty
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Calendar

abstract class Post(page: Page, parentPath: Path): PageData(page, parentPath) {
    val pageTitle: List<PageProperty.RichText> = page.properties["Title"]!!.title!!
    val icon: Icon = page.icon
    val published: Boolean = page.properties["Published"]!!.checkbox!!

    val slug: List<PageProperty.RichText>? = page.properties["Slug"]?.richText
    val date: String? = page.properties["Date"]?.date?.start
    val authors: List<String>?

    init {
        val authorsOption = page.properties["Authors"]?.people

        authors = if (authorsOption != null){
            val authorNames = ArrayList<String>()
            authorsOption.forEach { authorNames.add(it.name!!) }
            authorNames
        } else null
    }

    val htmlServerPath get() = htmlServerPath(getStaticHtmlName().asLoc())

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
        val fmtThisYear = SimpleDateFormat("MM-dd")
        val fmtOtherYear = SimpleDateFormat("yyyy-MM-dd")

        val timeCal = Calendar.getInstance()
        val currentCal = Calendar.getInstance()

        timeCal.time = lastEditedTimeDate

        return if (timeCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)){
            fmtThisYear.format(lastEditedTimeDate)
        } else fmtOtherYear.format(lastEditedTimeDate)
    }

    fun getCreatedTimeDay(): String{
        page.createdTime.let {
            return it.split('T')[0]
        }
        return "No date"
    }
}