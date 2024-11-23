package htmlgen.model

import notion.api.v1.model.pages.Page
import java.nio.file.Path

class ActivePage(page: Page, parentPath: Path) : PageData(page, parentPath) {
    override fun getStaticHtmlName(): String {
        return "active/${uuid}"
    }
}