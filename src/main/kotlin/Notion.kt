import notion.api.v1.NotionClient
import notion.api.v1.model.pages.PageProperty

fun notionClient(doAction: (client: NotionClient) -> Unit) {
    val notionToken = System.getenv("NOTION_TOKEN");
    NotionClient(token = notionToken).use { client ->
        doAction(client)
    }
}

private fun String.asRichText(): List<PageProperty.RichText> =
    listOf(PageProperty.RichText(text = PageProperty.RichText.Text(content = this)))

fun List<PageProperty.RichText>.toNormalString(): String{
    var str = ""
    this.forEach { str += it.plainText }
    return str;
}
