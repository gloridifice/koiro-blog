package notiondata

import notion.api.v1.NotionClient
import notion.api.v1.exception.NotionAPIError
import notion.api.v1.model.databases.Database
import notion.api.v1.request.blocks.RetrieveBlockRequest
import notion.api.v1.request.databases.QueryDatabaseRequest
import notion.api.v1.request.databases.RetrieveDatabaseRequest
import notion.api.v1.request.pages.RetrievePageRequest

const val notionDataRootPath = "notionData/"
fun main() {
    notionClient { client ->
        val databaseId = "0ed868dbb56445929e8a993ff70b1750"

        val collector = DatabaseCollector(client, databaseId)
        collector.collectTo(notionDataRootPath)
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

fun NotionClient.retrieveDatabaseJson(request: RetrieveDatabaseRequest): String {
    val httpResponse =
        httpClient.get(
            logger = logger,
            url = "$baseUrl/databases/${urlEncode(request.databaseId)}",
            headers = buildRequestHeaders(emptyMap()))
    if (httpResponse.status == 200) {
        return httpResponse.body
    } else {
        throw NotionAPIError(
            error = jsonSerializer.toError(httpResponse.body),
            httpResponse = httpResponse,
        )
    }
}

//toBlock
fun NotionClient.retrieveBlockJson(request: RetrieveBlockRequest): String {
    val httpResponse =
        httpClient.get(
            logger = logger,
            url = "$baseUrl/blocks/${urlEncode(request.blockId)}",
            headers = buildRequestHeaders(emptyMap()))
    if (httpResponse.status == 200) {
        return httpResponse.body
    } else {
        throw NotionAPIError(
            error = jsonSerializer.toError(httpResponse.body),
            httpResponse = httpResponse,
        )
    }
}

fun NotionClient.retrievePageJson(request: RetrievePageRequest): String {
    val httpResponse =
        httpClient.get(
            logger = logger,
            query = request.toQuery(),
            url = "$baseUrl/pages/${urlEncode(request.pageId)}",
            headers = buildRequestHeaders(emptyMap()))
    if (httpResponse.status == 200) {
        return httpResponse.body
    } else {
        throw NotionAPIError(
            error = jsonSerializer.toError(httpResponse.body),
            httpResponse = httpResponse,
        )
    }
}
fun notionClient(doAction: (client: NotionClient) -> Unit) {
    val notionToken = System.getenv("NOTION_TOKEN");
    NotionClient(token = notionToken).use { client ->
        doAction(client)
    }
}