package htmlgen.model

import notion.api.v1.model.databases.DatabaseProperty
import notiondata.PageData

class BlogPost(page: PageData): Post(page) {
    val type: DatabaseProperty.Select.Option
    val tags: List<DatabaseProperty.MultiSelect.Option>

    init {
        tags = page.page.properties["Tags"]!!.multiSelect!!
        type = page.page.properties["Class"]!!.select!!
    }
}