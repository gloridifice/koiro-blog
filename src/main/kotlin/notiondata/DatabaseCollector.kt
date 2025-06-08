package notiondata

import TitleExtractor
import childPath
import com.github.ajalt.mordant.rendering.TextColors
import com.google.gson.Gson
import com.google.gson.JsonObject
import htmlgen.downloadImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.*

class DatabaseCollector(
    val client: NotionClient, val databaseId: String
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
            if (existPage.lastEditedTime == page.lastEditedTime) return false
        }
        return true
    }

    private fun isBlockNeedToUpdate(block: Block, parentPath: Path): Boolean {
        val pageFile = parentPath.childPath(block.id!! + ".json").toFile()
        if (pageFile.exists() && pageFile.isFile) {
            val existPage = client.jsonSerializer.toBlock(pageFile.readText())
            if (existPage.lastEditedTime == block.lastEditedTime) return false
        }
        return true
    }

    @OptIn(ExperimentalPathApi::class)
    fun collectToOld(path: String) {
        val rootPath = Path(path)
        val databaseJson = client.retrieveDatabaseJson(RetrieveDatabaseRequest(databaseId))
        val database = client.jsonSerializer.toDatabase(databaseJson);

        if (isDatabaseNeedToUpdate(database, rootPath)) {

            writeJson(rootPath.childPath("database.json"), databaseJson)

            val queryDatabaseJson = client.queryDatabaseJson(QueryDatabaseRequest(databaseId))
            val queryDatabaseResult = client.jsonSerializer.toQueryResults(queryDatabaseJson)

            queryDatabaseResult.results.forEach { page ->
//                if (isPageNeedToUpdate(page, rootPath)) collectPage(page.id, rootPath)
            }
            writeJson(rootPath.childPath("query_result.json"), queryDatabaseJson)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    suspend fun collectTo(path: String): Unit = coroutineScope {
        val rootPath = Path(path)

        // 1. 并行获取数据库信息和查询结果
        val (databaseJson, queryDatabaseJson) = withContext(Dispatchers.IO) {
            async { client.retrieveDatabaseJson(RetrieveDatabaseRequest(databaseId)) } to
                    async { client.queryDatabaseJson(QueryDatabaseRequest(databaseId)) }
        }.let { (db, query) -> db.await() to query.await() }

        val database = client.jsonSerializer.toDatabase(databaseJson)

        if (isDatabaseNeedToUpdate(database, rootPath)) {
            // 2. 并行执行写操作和页面收集
            val writeJobs = mutableListOf<Job>()

            // 2.1 异步写入数据库文件
            writeJobs += launch(Dispatchers.IO) {
                writeJson(rootPath.childPath("database.json"), databaseJson)
            }

            // 2.2 异步处理所有页面
            val queryResult = client.jsonSerializer.toQueryResults(queryDatabaseJson)
            queryResult.results.forEach { page ->
                if (isPageNeedToUpdate(page, rootPath)) {
                    writeJobs += launch(Dispatchers.IO) {
                        collectPage(page.id, rootPath) // 假设collectPage已经是suspend函数
                    }
                }
            }

            // 2.3 异步写入查询结果
            writeJobs += launch(Dispatchers.IO) {
                writeJson(rootPath.childPath("query_result.json"), queryDatabaseJson)
            }

            // 3. 等待所有操作完成
            writeJobs.joinAll()
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun collectPage(pageId: String, parentPath: Path) = coroutineScope {
        // 1. 并行获取页面数据和封面
        val (json, page) = withContext(Dispatchers.IO) {
            async { client.retrievePageJson(RetrievePageRequest(pageId)) } to
                    async {
                        val json = client.retrievePageJson(RetrievePageRequest(pageId))
                        NotionClient.defaultJsonSerializer.toPage(json)
                    }
        }.let { (json, page) -> json.await() to page.await() }

        // 2. 并行执行三个独立操作
        val jobs = listOf(
            // 2.1 异步写入页面JSON
            launch(Dispatchers.IO) {
                writeJson(parentPath.childPath("$pageId.json"), json)
            },

            // 2.2 异步清理旧区块目录
            launch(Dispatchers.IO) {
                parentPath.childPath(pageId).deleteRecursively()
            },

            // 2.3 异步下载封面图
            launch(Dispatchers.IO) {
                page.properties["Preview"]?.files?.firstOrNull()?.file?.url?.let { url ->
                    downloadImage(url.toString(), parentPath.childPath(pageId), "preview_image")
                }
            }
        )

        // 3. 获取并处理子区块（结构化并发）
        try {
            val blocks = withContext(Dispatchers.IO) {
                client.retrieveAllBlockChildrenWithRetry(pageId)
            }

            val toCollect = blocks.mapIndexed { i, block ->
                CollectBlockRequest(
                    block.id!!,
                    parentId = pageId,
                    parentPath = parentPath.childPath(pageId),
                    index = i
                )
            }

            if (toCollect.isNotEmpty()) {
                collectBlocks(toCollect) // 直接调用suspend函数，无需runBlocking
            }
        } catch (e: Exception) {
            println(
                TextColors.red(
                    """
            Retrieving children failed, some blocks will be missing. 
            PageId: $pageId
            Error: ${e.stackTraceToString()}
        """.trimIndent()
                )
            )
            // 可选：标记不完整状态
            parentPath.childPath("${pageId}.incomplete").createFile()
        }

        // 等待所有并行操作完成
        jobs.joinAll()
    }

    @ExperimentalPathApi
    private fun collectPageOld(pageId: String, parentPath: Path) {
        val json = client.retrievePageJson(RetrievePageRequest(pageId))
        writeJson(parentPath.childPath("$pageId.json"), json)

        // 删除原来所有的 Block
        parentPath.childPath(pageId).deleteRecursively()

        // 下载封面
        val serializer = NotionClient.defaultJsonSerializer;
        val page = serializer.toPage(json)
        page.properties["Preview"]?.let {
            it.files?.let { files ->
                files.getOrNull(0)?.let { file ->
                    downloadImage(file.file!!.url.toString(), parentPath.childPath(pageId), "preview_image")
                }
            }
        }

        try {
            val blocks = client.retrieveAllBlockChildrenWithRetry(pageId)
            val toCollect = ArrayList<CollectBlockRequest>()

            for (i in blocks.indices) {
                val it = blocks[i]
                toCollect.add(CollectBlockRequest(it.id!!, pageId, parentPath.childPath(pageId), i))
            }
            runBlocking {
                collectBlocks(toCollect)
            }
        } catch (e: Exception) {
            println(TextColors.red("Retrieving children failed, somethings will miss. Id: <$pageId>; Error: $e"))
        }
    }

    data class CollectBlockRequest(val blockId: String, val parentId: String, val parentPath: Path, val index: Int)

    private suspend fun collectBlocks(requests: List<CollectBlockRequest>): Unit = coroutineScope {
        // 并发安全集合
        val toWrite = Channel<Pair<CollectBlockRequest, String>>(Channel.UNLIMITED)
        val toRetrieveChildren = Channel<CollectBlockRequest>(Channel.UNLIMITED)

        // 并发处理所有请求（真正异步）
        val collectJobs = requests.map { request ->
            launch(Dispatchers.IO) { // 每个请求独立调度
                try {
                    val json = client.retrieveBlockJson(RetrieveBlockRequest(request.blockId))
                    toWrite.send(request to json)

                    val block = client.jsonSerializer.toBlock(json)
                    if (isBlockNeedToUpdate(block, request.parentPath)) {
                        processSpecialBlock(block, request)

                        if (block.hasChildren == true) {
                            retrieveChildrenBlocks(request).forEach {
                                toRetrieveChildren.send(it)
                            }
                        }
                    }
                } catch (e: Exception) {
                    handleException(e, request)
                }
            }
        }

        collectJobs.joinAll()

        // 启动写入协程
        val writer = launch(Dispatchers.IO) {
            for ((block, json) in toWrite) {
                writeBlock(block, json)
            }
        }

        // 启动子块处理协程
        val childrenProcessor = launch(Dispatchers.IO) {
            val children = mutableListOf<CollectBlockRequest>()
            for (request in toRetrieveChildren) {
                children.add(request)
            }
            if (children.isNotEmpty()) {
                collectBlocks(children) // 递归处理子块
            }
        }

        toWrite.close()
        toRetrieveChildren.close()
        writer.join()
        childrenProcessor.join()
    }

    private suspend fun collectBlocksOld(requests: List<CollectBlockRequest>): Unit = coroutineScope {
        val toWrite = ConcurrentLinkedQueue<Pair<CollectBlockRequest, String>>()
        val toRetrieveChildren = ConcurrentLinkedQueue<CollectBlockRequest>()

        val jobs = requests.map { request ->
            async {
                try {
                    val json = client.retrieveBlockJson(RetrieveBlockRequest(request.blockId))
                    toWrite.add(request to json)
                    val block = client.jsonSerializer.toBlock(json)
                    if (isBlockNeedToUpdate(block, request.parentPath)) {
                        processSpecialBlock(block, request)

                        if (block.hasChildren == true) {
                            retrieveChildrenBlocks(request).let(toRetrieveChildren::addAll)
                        }
                    }
                } catch (e: Exception) {
                    handleException(e, request)
                    throw e
                }
            }
        }
        jobs.awaitAll()
        toWrite.forEach { (block, json) -> writeBlock(block, json) }

        if (toRetrieveChildren.isNotEmpty()) {
            collectBlocks(toRetrieveChildren.toList())
        }
    }

    private fun processSpecialBlock(block: Block, request: CollectBlockRequest) {
        when (block) {
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
                val filePath = request.parentPath.childPath("bookmark_${request.blockId}.json")
                filePath.createParentDirectories()
                val file = if (filePath.exists()) filePath else filePath.createFile()
                file.writeText(bookmarkJson)
            }
        }
    }

    private fun retrieveChildrenBlocks(request: CollectBlockRequest): List<CollectBlockRequest> {
        return client.retrieveBlockChildren(request.blockId).results.mapIndexed { i, child ->
            CollectBlockRequest(
                child.id!!,
                request.blockId,
                request.parentPath.childPath(request.blockId),
                i
            )
        }
    }

    private fun handleException(e: Exception, request: CollectBlockRequest) {
        request.parentPath.parent.childPath("${request.parentId}.json").deleteIfExists()
        println("Failed to collect block ${request.blockId}: ${e.message}")
    }

    private fun writeBlock(block: CollectBlockRequest, json: String) {
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

