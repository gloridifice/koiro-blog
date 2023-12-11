package notiondata

import com.github.ajalt.mordant.rendering.TextColors
import notion.api.v1.NotionClient
import notion.api.v1.exception.NotionAPIError
import notion.api.v1.request.blocks.RetrieveBlockRequest
import notion.api.v1.request.databases.QueryDatabaseRequest
import notion.api.v1.request.databases.RetrieveDatabaseRequest
import notion.api.v1.request.pages.RetrievePageRequest

const val NOTION_BLOG_DATABASE_ROOT_PATH = "notionData/blogDatabase"
const val NOTION_DEV_LOG_DATABASE_ROOT_PATH = "notionData/devLogDatabase"
const val MAX_RETRY_COUNT = 3
fun main() {
    notionClient { client ->
        val blogDatabaseId = "0ed868dbb56445929e8a993ff70b1750"
        val devLogDatabaseId = "07a7413ef3424478abdceee428cebdfb"

        val blogDatabaseCollector = DatabaseCollector(client, blogDatabaseId)
        blogDatabaseCollector.collectTo(NOTION_BLOG_DATABASE_ROOT_PATH)

        val devLogDatabaseCollector = DatabaseCollector(client, devLogDatabaseId)
        devLogDatabaseCollector.collectTo(NOTION_DEV_LOG_DATABASE_ROOT_PATH)
    }
}

fun NotionClient.queryDatabaseJson(request: QueryDatabaseRequest): String {
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

fun notionClient(doAction: (client: NotionClient) -> Unit) {
    val notionToken = System.getenv("NOTION_TOKEN");
    NotionClient(token = notionToken).use { client ->
        doAction(client)
    }
}