package htmlgen.model

import notion.api.v1.model.databases.Database
import notion.api.v1.model.databases.QueryResults
import java.util.*

class DatabaseData<T: PageData>(
    val database: Database,
    val queryDatabaseRequest: QueryResults,
    val publishedPages: List<T>,
    val allPages: List<T> // contains unpublished pages
) {
    val latestData: Date = publishedPages.first().lastEditedTimeDate;
}