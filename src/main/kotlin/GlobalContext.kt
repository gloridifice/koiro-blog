import htmlgen.model.*
import htmlgen.model.home.ActiveElement
import htmlgen.model.home.BlogElement
import htmlgen.model.home.DevLogElement
import htmlgen.model.home.HomeElement

class GlobalContext(
    val blogDatabaseData: DatabaseData<BlogPostPage>,
    val devLogDatabaseData: DatabaseData<DevLogPostPage>,
    val activeDatabaseData: DatabaseData<ActivePage>,
    val portfolioDatabaseData: DatabaseData<ProjectPage>
) {
    val latestPostPage: PostPage;
    val homeElements: ArrayList<HomeElement>;

    init {
        val postLatest = blogDatabaseData.latestData
        val devLogLatest = devLogDatabaseData.latestData
        latestPostPage = if (postLatest > devLogLatest)
            blogDatabaseData.publishedPages.first()
        else devLogDatabaseData.publishedPages.first();

        homeElements = ArrayList()
        homeElements.addAll(blogDatabaseData.publishedPages.map { BlogElement(it) })
        homeElements.addAll(devLogDatabaseData.publishedPages.map { DevLogElement(it) })
        homeElements.addAll(activeDatabaseData.publishedPages.map { ActiveElement(it) })
        homeElements.sortByDescending { it.getDate() }

        portfolioDatabaseData.publishedPages.forEach {
            it.copyPreviewImageFromNotionDataToStatic()
        }
    }
}