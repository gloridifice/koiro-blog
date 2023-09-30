import notion.api.v1.model.common.Emoji
import notion.api.v1.model.common.Icon
import notion.api.v1.model.databases.DatabaseProperty
import notion.api.v1.model.pages.Page
import notion.api.v1.model.pages.PageProperty

class BlogPage(val page: Page) {
    val pageTitle: List<PageProperty.RichText>
    val icon: Icon
    val slug: List<PageProperty.RichText>
    val date: String?
    val authors: List<String>
    val type: DatabaseProperty.Select.Option
    val tags: List<DatabaseProperty.MultiSelect.Option>
    val published: Boolean

    init {
        icon = page.icon
        tags = page.properties["Tags"]!!.multiSelect!!
        pageTitle = page.properties["Page"]!!.title!!
        type = page.properties["Class"]!!.select!!
        slug = page.properties["Slug"]!!.richText!!
        published = page.properties["Published"]!!.checkbox!!
        date = page.properties["Date"]?.lastEditedTime
        val authorsOption = page.properties["Authors"]!!.people!!

        val authorNames = ArrayList<String>()
        authorsOption.forEach { authorNames.add(it.name!!) }

        authors = authorNames
    }

    val htmlServerPath get() = htmlServerPath(htmlName.asLoc())
    val htmlGenPath get() = "${outputDirectory}${htmlName}.html"
    val assetsDirectoryPath get() = "${outputDirectory}${htmlName}/"
    val htmlName get() = "post/${uuid}"
    val uuid get() = page.id;

    fun getPlainTitle(): String = pageTitle.toNormalString()
    fun getEmoji(): String{
        val default ="🦆"
        if (icon is Emoji){
            return icon.emoji ?: default
        }
        return default
    }
    fun getDateDay(): String{
        date?.let {
            return it.split('T')[0]
        }
        return "No date"
    }
}