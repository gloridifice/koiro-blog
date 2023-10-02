package notiondata

import childPath
import htmlgen.downloadImage
import notion.api.v1.NotionClient
import notion.api.v1.model.blocks.Block
import notion.api.v1.model.blocks.ImageBlock
import notion.api.v1.model.pages.Page
import notion.api.v1.request.blocks.RetrieveBlockRequest
import notion.api.v1.request.databases.QueryDatabaseRequest
import notion.api.v1.request.databases.RetrieveDatabaseRequest
import notion.api.v1.request.pages.RetrievePageRequest
import writeJson
import java.nio.file.Path
import kotlin.io.path.*

class DatabaseCollector(
    val client: NotionClient,
    val databaseId: String
) {
    private fun isPageNeedToUpdate(page: Page, parentPath: Path): Boolean {
        val pageFile = parentPath.childPath(page.id + ".json").toFile()
        if (pageFile.exists() && pageFile.isFile) {
            val existPage = client.jsonSerializer.toPage(pageFile.readText())
            if (existPage.lastEditedTime == page.lastEditedTime)
                return false
        }
        return true
    }

    private fun isBlockNeedToUpdate(block: Block, parentPath: Path): Boolean {
        val pageFile = parentPath.childPath(block.id!! + ".json").toFile()
        if (pageFile.exists() && pageFile.isFile) {
            val existPage = client.jsonSerializer.toBlock(pageFile.readText())
            if (existPage.lastEditedTime == block.lastEditedTime)
                return false
        }
        return true
    }

    fun collectTo(path: String) {
        val rootPath = Path(path)
        val queryDatabaseJson = client.queryDatabaseJson(QueryDatabaseRequest(databaseId))
        val queryDatabaseResult = client.jsonSerializer.toQueryResults(queryDatabaseJson)

        queryDatabaseResult.results.forEach { page ->
            if (isPageNeedToUpdate(page, rootPath))
                collectPage(page.id, rootPath)
        }
        writeJson(rootPath.childPath("query_result.json"), queryDatabaseJson)

        val databaseJson = client.retrieveDatabaseJson(RetrieveDatabaseRequest(databaseId))
        writeJson(rootPath.childPath("database.json"), databaseJson)
    }


    private fun collectPage(pageId: String, parentPath: Path) {
        val json = client.retrievePageJson(RetrievePageRequest(pageId))
        writeJson(parentPath.childPath("$pageId.json"), json)

        val blocks = client.retrieveBlockChildren(pageId)
        for (i in blocks.results.indices) {
            collectBlockRecursively(blocks.results[i].id!!, parentPath.childPath(pageId), i)
        }
    }

    private fun collectBlockRecursively(blockId: String, parentPath: Path, index: Int) {
        val json = client.retrieveBlockJson(RetrieveBlockRequest(blockId))
        writeJson(parentPath.childPath("${String.format("%03d", index)}_$blockId.json"), json)

        val block = client.jsonSerializer.toBlock(json)
        if (isBlockNeedToUpdate(block, parentPath)){
            if (block is ImageBlock) {
                block.image?.file?.url?.let {
                    downloadImage(it, parentPath, "img_$blockId")
                }
            }
            if (block.hasChildren == true) {
                val blocks = client.retrieveBlockChildren(blockId)
                for (i in blocks.results.indices) {
                    collectBlockRecursively(blocks.results[i].id!!, parentPath.childPath(blockId), i)
                }
            }
        }
    }
}

