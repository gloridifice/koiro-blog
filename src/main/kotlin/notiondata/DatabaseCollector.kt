package notiondata

import TitleExtractor
import childPath
import com.github.ajalt.mordant.rendering.TextColors
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import htmlgen.downloadImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import notion.api.v1.NotionClient
import notion.api.v1.model.blocks.Block
import notion.api.v1.model.blocks.BookmarkBlock
import notion.api.v1.model.blocks.ImageBlock
import notion.api.v1.model.databases.Database
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

    private fun isDatabaseNeedToUpdate(database: Database, parentPath: Path): Boolean {
//        val databaseFile = parentPath.childPath("database.json").toFile()
//        if (databaseFile.exists() && databaseFile.isFile) {
//            val existDatabase = client.jsonSerializer.toDatabase(databaseFile.readText())
//            if (existDatabase.lastEditedTime == database.lastEditedTime)
//                return false
//        }
        return true
    }

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
        val databaseJson = client.retrieveDatabaseJson(RetrieveDatabaseRequest(databaseId))

        if (isDatabaseNeedToUpdate(client.jsonSerializer.toDatabase(databaseJson), rootPath)) {

            writeJson(rootPath.childPath("database.json"), databaseJson)

            val queryDatabaseJson = client.queryDatabaseJson(QueryDatabaseRequest(databaseId))
            val queryDatabaseResult = client.jsonSerializer.toQueryResults(queryDatabaseJson)

            queryDatabaseResult.results.forEach { page ->
                if (isPageNeedToUpdate(page, rootPath))
                    collectPage(page.id, rootPath)
            }
            writeJson(rootPath.childPath("query_result.json"), queryDatabaseJson)
        }
    }


    private fun collectPage(pageId: String, parentPath: Path) {
        val json = client.retrievePageJson(RetrievePageRequest(pageId))
        writeJson(parentPath.childPath("$pageId.json"), json)

        val blocks = client.retrieveBlockChildren(pageId)
        val toCollect = ArrayList<CollectBlockRequest>()
        for (i in blocks.results.indices){
            val it = blocks.results[i]
            toCollect.add(CollectBlockRequest(it.id!!, pageId, parentPath.childPath(pageId), i))
        }
        runBlocking {
            collectBlocks(toCollect)
        }
    }

    data class CollectBlockRequest(val blockId: String, val parentId: String, val parentPath: Path, val index: Int)

    private suspend fun collectBlocks(requests: List<CollectBlockRequest>) {

        coroutineScope {
            val toWrite = ArrayList<Pair<CollectBlockRequest, String>>()
            val toRetrieveChildren = ArrayList<CollectBlockRequest>()

            val collect = launch {
                requests.forEach { request ->
                    launch {
                        val json: String = try {
                            client.retrieveBlockJson(RetrieveBlockRequest(request.blockId))
                        } catch (_: Exception) {
                            val parentFile = request.parentPath.parent.childPath("${request.parentId}.json")
                            parentFile.deleteIfExists()
                            throw Exception("Collect Block Failed.")
                        }
                        toWrite.add(Pair(request, json))

                        val block = client.jsonSerializer.toBlock(json)
                        if (isBlockNeedToUpdate(block, request.parentPath)) {
                            when(block){
                                is ImageBlock -> {
                                    block.image?.file?.url?.let {
                                        downloadImage(it, request.parentPath, "img_${request.blockId}")
                                    }
                                }
                                is BookmarkBlock -> {
                                    val title = TitleExtractor.getPageTitle(block.bookmark!!.url)
                                    val root = JsonObject()
                                    root.addProperty("title", title)
                                    val bookmarkJson = Gson().toJson(root)
                                    request.parentPath.childPath("bookmark_${request.blockId}.json").toFile().writeText(bookmarkJson)
                                }
                            }
                            if (block.hasChildren == true) {
                                val childrenRetrieve = client.retrieveBlockChildren(request.blockId)
                                for (i in childrenRetrieve.results.indices) {
                                    val child = childrenRetrieve.results[i]
                                    toRetrieveChildren.add(
                                        CollectBlockRequest(
                                            child.id!!,
                                            request.blockId,
                                            request.parentPath.childPath(request.blockId),
                                            i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            collect.join()

            toWrite.forEach {
                val block = it.first
                val json = it.second
                writeBlock(block, json)
            }
            if (toRetrieveChildren.isNotEmpty()) {
                collectBlocks(toRetrieveChildren)
            }
        }

    }
    private fun writeBlock(block: CollectBlockRequest, json: String){
        val blockInstance = NotionClient.defaultJsonSerializer.toBlock(json)
        val path = block.parentPath.childPath("${String.format("%03d", block.index)}_${block.blockId}.json")
        println(TextColors.brightMagenta("Successfully Collect Block: type-${blockInstance.type.name} \n path: ${path}"))

        writeJson(path, json)
    }

    private fun collectBlockRecursively(blockId: String, parentId: String, parentPath: Path, index: Int) {
        val json: String = try {
            client.retrieveBlockJson(RetrieveBlockRequest(blockId))
        } catch (_: Exception) {
            val parentFile = parentPath.parent.childPath("${parentId}.json")
            parentFile.deleteIfExists()
            throw Exception("Collect Block Failed.")
        }
        writeJson(parentPath.childPath("${String.format("%03d", index)}_$blockId.json"), json)

        val block = client.jsonSerializer.toBlock(json)
        if (isBlockNeedToUpdate(block, parentPath)) {
            if (block is ImageBlock) {
                block.image?.file?.url?.let {
                    downloadImage(it, parentPath, "img_$blockId")
                }
            }
            if (block.hasChildren == true) {
                val blocks = client.retrieveBlockChildren(blockId)
                for (i in blocks.results.indices) {
                    collectBlockRecursively(blocks.results[i].id!!, blockId, parentPath.childPath(blockId), i)
                }
            }
        }
    }
}

