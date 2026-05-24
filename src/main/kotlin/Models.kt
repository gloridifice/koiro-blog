import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


class BlogRecord(
    val header: BlogHeader,
    val serverPath: ServerPath
)

class ActiveRecord(
    val header: ActiveHeader,
    val markdownContent: String, // in markdown
    val sourcePath: Path
)

class DevlogRecord(val header: DevlogHeader, val serverPath: ServerPath, val previewImagePath: ServerPath?)

@Serializable
class ActiveHeader(
    val date: String,
    val published: Boolean
)

class PortfolioRecord(val header: PortfolioHeader, val previewImagePath: ServerPath)

@Serializable
class PortfolioHeader(
    val title: String,
    @SerialName("project")
    val workName: String,
    val description: String,
    val url: String,
    @SerialName("preview")
    val previewImagePath: String,
    val published: Boolean,
    val status: String,
    val type: String,
    val lite: Boolean
)

@Serializable
class DevlogHeader(
    val title: String,
    @SerialName("work")
    val workName: String,
    val index: Int,
    val published: Boolean,
    val date: String,

    @SerialName("preview")
    val previewImagePath: String? = null,
)

@Serializable
class BlogHeader(
    val title: String,
    val slug: String = "",
    val published: Boolean,
    @SerialName("class")
    val blogClass: String,
    val date: String,

    val tags: List<String> = listOf(),
    val emoji: String? = null
) {
    @OptIn(ExperimentalTime::class)
    fun displayDate(): String {
        val localDate = try {
            LocalDateTime.parse(date).date
        } catch (_: Exception) {
            LocalDate.parse(date)
        }
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val mmDd = "${localDate.month.toString().padStart(2, '0')}-${localDate.day.toString().padStart(2, '0')}"
        return if (localDate.year == currentYear) mmDd else "${localDate.year}-$mmDd"
    }
}
