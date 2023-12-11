import notion.api.v1.model.databases.DatabaseProperty
import notion.api.v1.model.pages.Page

class BlogPost(page: Page): Post(page) {
    val type: DatabaseProperty.Select.Option
    val tags: List<DatabaseProperty.MultiSelect.Option>

    init {
        tags = page.properties["Tags"]!!.multiSelect!!
        type = page.properties["Class"]!!.select!!
    }
}