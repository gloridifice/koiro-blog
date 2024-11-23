package htmlgen.model

import notion.api.v1.model.databases.DatabaseProperty
import notion.api.v1.model.pages.Page
import java.nio.file.Path

class BlogPostPage(page: Page, parentPath: Path) : Post(page, parentPath) {
    val type: DatabaseProperty.Select.Option
    val tags: List<DatabaseProperty.MultiSelect.Option>

    init {
        tags = page.properties["Tags"]!!.multiSelect!!
        type = page.properties["Class"]!!.select!!
    }

    override fun getStaticHtmlName(): String {
        return "post/${uuid}"
    }
}