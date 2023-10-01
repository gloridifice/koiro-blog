package notiondata

import Post
import childPath
import hasChildren
import notion.api.v1.NotionClient
import notion.api.v1.model.blocks.Block
import notion.api.v1.model.databases.Database
import notion.api.v1.model.databases.QueryResults
import notion.api.v1.model.pages.Page
import java.nio.file.Path
import java.text.SimpleDateFormat


fun readNotionData(rootPath: Path): DataDatabase {
    val queryDatabaseJson = rootPath.childPath("query_result.json").toFile().readText()
    val result = NotionClient.defaultJsonSerializer.toQueryResults(queryDatabaseJson)

    val databaseJson = rootPath.childPath("database.json").toFile().readText()
    val database = NotionClient.defaultJsonSerializer.toDatabase(databaseJson)

    var dataPages = result.results.map {
        DataPage(it, rootPath)
    }.toList()
    dataPages = dataPages.sortedWith { n1, n2 ->
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val n1Date = fmt.parse(n1.post.date!!)
        val n2Date = fmt.parse(n2.post.date!!)
        n1Date.compareTo(n2Date)
    }

    return DataDatabase(database, result, dataPages)
}

class DataDatabase(
    val database: Database,
    val queryDatabaseRequest: QueryResults,
    val dataPages: List<DataPage>,
) {
}

class DataPage(
    val page: Page,
    parentPath: Path,
) {
    val dataBlocks: List<DataBlock>?
    val post: Post

    init {
        //read dataBlocks object
        val currentPath = parentPath.childPath(page.id)
        dataBlocks = readChildrenBlocks(currentPath)
        post = Post(page)
    }
}

class DataBlock(val block: Block, parentPath: Path) {
    val children: List<DataBlock>?;

    init {
        val currentPath = parentPath.childPath(block.id!!)
        children = readChildrenBlocks(currentPath)
    }
}

fun readChildrenBlocks(currentPath: Path): List<DataBlock>? {
    val files = currentPath.toFile().listFiles()
    val dataBlocks = if (currentPath.hasChildren() && files != null) {
        val handledFiles = files.filter { file ->
            file != null && file.isFile
        }.sortedBy { it.name.split('_').first().toInt() }
        val list = ArrayList<DataBlock>()
        for (file in handledFiles) {
            val read = file.readText()
            val block = NotionClient.defaultJsonSerializer.toBlock(read)
            list.add(DataBlock(block, currentPath))
        }
        list
    } else null
    return dataBlocks
}