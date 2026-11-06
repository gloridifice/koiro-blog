import buildsystem.ResourceOutput
import buildsystem.SiteResources
import kotlin.test.*

class SiteResourcesTest {
    @Test
    fun `RSS fragments use the shared atomic cache and unselected atoms are discarded`() = withSiteFixture { data, output, _ ->
        val initial = mapOf("en-US" to "<item><title>First</title></item>", "zh-CN" to "<item><title>中文</title></item>")
        SiteResources(output, data).apply {
            assertEquals(initial, atomic("rss:post", "input-one", fragmentKeys = initial.keys) { ResourceOutput(fragments = initial) })
            finish()
        }
        SiteResources(output, data).apply {
            assertEquals(initial, atomic("rss:post", "input-one", fragmentKeys = initial.keys) { error("Unchanged RSS items must not be regenerated") })
            finish()
        }
        val revised = mapOf("en-US" to "<item><title>Revised</title></item>")
        SiteResources(output, data).apply {
            assertEquals(revised, atomic("rss:post", "input-two", fragmentKeys = revised.keys) { ResourceOutput(fragments = revised) })
            finish()
        }
        SiteResources(output, data).finish()
        var generated = false
        SiteResources(output, data).apply {
            assertEquals(revised, atomic("rss:post", "input-two", fragmentKeys = revised.keys) {
                generated = true
                ResourceOutput(fragments = revised)
            })
            finish()
        }
        assertTrue(generated, "A removed article cannot remain in the active cache")
    }
}
