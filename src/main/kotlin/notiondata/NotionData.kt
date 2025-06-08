package notiondata

import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import notion.api.v1.NotionClient
import notion.api.v1.exception.NotionAPIError
import notion.api.v1.model.blocks.Block
import notion.api.v1.model.blocks.Blocks
import notion.api.v1.request.blocks.RetrieveBlockRequest
import notion.api.v1.request.databases.QueryDatabaseRequest
import notion.api.v1.request.databases.RetrieveDatabaseRequest
import notion.api.v1.request.pages.RetrievePageRequest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val NOTION_BLOG_DATABASE_ROOT_PATH = "notionData/blogDatabase"
const val NOTION_DEV_LOG_DATABASE_ROOT_PATH = "notionData/devLogDatabase"
const val NOTION_ACTIVE_DATABASE_ROOT_PATH = "notionData/activeDatabase"
const val NOTION_PORTFOLIO_DATABASE_ROOT_PATH = "notionData/portfolio"
const val MAX_RETRY_COUNT = 10

val databaseThreadPool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

fun main() {
    notionClient { client ->
        val blogDatabaseId = "0ed868dbb56445929e8a993ff70b1750"
        val devLogDatabaseId = "07a7413ef3424478abdceee428cebdfb"
        val activeDatabaseId = "1427e342f5b580b78b7bf0e6876e8eac"
        val portfolioDatabaseId = "1527e342f5b580f9a49ae31fd1e38e21"

        runBlocking {
            val collector = listOf(
                launch(databaseThreadPool) {
                    DatabaseCollector(client, blogDatabaseId).collectTo(NOTION_BLOG_DATABASE_ROOT_PATH)
                    println(TextColors.blue("${Thread.currentThread().name} Finished Blog database collection!"))
                },
                        launch(databaseThreadPool) {
                    DatabaseCollector(client, devLogDatabaseId).collectTo(NOTION_DEV_LOG_DATABASE_ROOT_PATH)
                    println(TextColors.blue("${Thread.currentThread().name} Finished Devlog database collection!"))
                },
                        launch(databaseThreadPool) {
                    DatabaseCollector(client, activeDatabaseId).collectTo(NOTION_ACTIVE_DATABASE_ROOT_PATH)
                    println(TextColors.blue("${Thread.currentThread().name} Finished active database collection!"))
                },
                        launch(databaseThreadPool) {
                    DatabaseCollector(client, portfolioDatabaseId).collectTo(NOTION_PORTFOLIO_DATABASE_ROOT_PATH)
                    println(TextColors.blue("${Thread.currentThread().name} Finished portfolio database collection"))
                }
            )
            collector.joinAll()
            (databaseThreadPool.executor as ExecutorService).shutdown()
        }
    }
}

fun NotionClient.queryDatabaseJson(request: QueryDatabaseRequest, count: Int = 0): String {
    try {
        val httpResponse =
            httpClient.postTextBody(
                logger = logger,
                url = "$baseUrl/databases/${urlEncode(request.databaseId)}/query",
                body = jsonSerializer.toJsonString(request),
                headers = buildRequestHeaders(contentTypeJson())
            )
        if (httpResponse.status == 200) {
            return httpResponse.body
        } else {
            throw NotionAPIError(
                error = jsonSerializer.toError(httpResponse.body),
                httpResponse = httpResponse,
            )
        }
    } catch (error: Exception) {
        if (count <= MAX_RETRY_COUNT) {
            println(TextColors.yellow("Retrieve failed, start retry, current retry count: ${count}."))
            return queryDatabaseJson(request, count + 1)
        } else throw error
    }
}

fun NotionClient.retrieveDatabaseJson(request: RetrieveDatabaseRequest, count: Int = 0): String {
    return try {
        val httpResponse =
            httpClient.get(
                logger = logger,
                url = "$baseUrl/databases/${urlEncode(request.databaseId)}",
                headers = buildRequestHeaders(emptyMap())
            )
        if (httpResponse.status == 200) {
            return httpResponse.body
        } else {
            throw NotionAPIError(
                error = jsonSerializer.toError(httpResponse.body),
                httpResponse = httpResponse,
            )
        }
    } catch (error: Exception) {
        if (count <= MAX_RETRY_COUNT) {
            println(TextColors.yellow("Retrieve failed, start retry, current retry count: ${count}."))
            retrieveDatabaseJson(request, count + 1)
        } else throw error
    }
}


//toBlock
fun NotionClient.retrieveBlockJson(request: RetrieveBlockRequest, count: Int = 0): String {
    return try {
        val httpResponse =
            httpClient.get(
                logger = logger,
                url = "$baseUrl/blocks/${urlEncode(request.blockId)}",
                headers = buildRequestHeaders(emptyMap())
            )
        if (httpResponse.status == 200) {
            return httpResponse.body
        } else {
            throw NotionAPIError(
                error = jsonSerializer.toError(httpResponse.body),
                httpResponse = httpResponse,
            )
        }
    } catch (error: Exception) {
        if (count <= MAX_RETRY_COUNT) {
            println(TextColors.yellow("Retrieve failed, start retry, current retry count: ${count}."))
            retrieveBlockJson(request, count + 1)
        } else throw error
    }
}

fun NotionClient.retrievePageJson(request: RetrievePageRequest, count: Int = 0): String {
    return try {
        val httpResponse =
            httpClient.get(
                logger = logger,
                query = request.toQuery(),
                url = "$baseUrl/pages/${urlEncode(request.pageId)}",
                headers = buildRequestHeaders(emptyMap())
            )
        if (httpResponse.status == 200) {
            return httpResponse.body
        } else {
            throw NotionAPIError(
                error = jsonSerializer.toError(httpResponse.body),
                httpResponse = httpResponse,
            )
        }
    } catch (error: Exception) {
        if (count <= MAX_RETRY_COUNT) {
            println(TextColors.yellow("Retrieve failed, start retry, current retry count: ${count}."))
            retrievePageJson(request, count + 1)
        } else throw error
    }
}

fun NotionClient.retrieveAllBlockChildrenWithRetry(id: String): List<Block> {
    val ret = mutableListOf<Block>()
    var cursor: String? = null

    do {
        val blocks = retrieveBlockChildrenInternally(id, 0, cursor)
        ret.addAll(blocks.results)
        cursor = blocks.nextCursor
    } while (cursor != null)

    return ret
}

private fun NotionClient.retrieveBlockChildrenInternally(id: String, count: Int, cursor: String?): Blocks {
    try {
        return retrieveBlockChildren(id, cursor, 100)
    } catch (e: Exception) {
        if (count <= MAX_RETRY_COUNT) {
            println(TextColors.yellow("Retrieve children failed, start retry, current retry count: ${count}."))
            return retrieveBlockChildrenInternally(id, count + 1, cursor)
        } else throw e
    }
}

fun notionClient(doAction: (client: NotionClient) -> Unit) {
    val notionToken = System.getenv("NOTION_TOKEN");
    NotionClient(token = notionToken).use { client ->
        doAction(client)
    }
}