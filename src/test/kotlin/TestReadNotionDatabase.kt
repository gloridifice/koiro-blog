import htmlgen.toNormalString
import notiondata.readNotionDatabase
import kotlin.io.path.Path


fun main(){
    val database = readNotionDatabase(Path("notionData"))
    database.publishedPages.forEach { page ->
        println("page: " + page.page.properties["Page"]!!.title!!.toNormalString())
        page.dataBlocks?.forEach { block ->
            println("--> block { type: ${block.block.type} }")
            block.children?.forEach {
                println("  --> child_0 { type: ${it.block.type} }")
            }
        }
    }
}