package markdown

import ServerPath
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class BlogRecord(
    val header: BlogHeader,
    val serverPath: ServerPath
)

class ActiveRecord(
    val header: ActiveHeader,
    val markdownContent: String, // in markdown
)

class DevlogRecord(val header: DevlogHeader, val serverPath: ServerPath)

@Serializable
class ActiveHeader(
    val date: LocalDate,
    val published: Boolean
)

@Serializable
class Portfolio(
    val title: String,
    @SerialName("project")
    val workName: String,
    val description: String,
    val url: String,
    @SerialName("preview_image")
    val previewImage: String,
    val published: Boolean,
    val status: String,
    val type: String,
)

@Serializable
class DevlogHeader(
    val title: String,
    @SerialName("work")
    val workName: String,
    val index: Int,
    val published: Boolean,
    val date: LocalDate,
    @SerialName("preview")
    val previewImagePath: String?,
)

@Serializable
class BlogHeader(
    val title: String,
    val slug: String,
    val published: Boolean,
    @SerialName("class")
    val blogClass: String,
    val date: LocalDate,
    val tags: List<String> = listOf(),
    val emoji: String?

) {
    @OptIn(ExperimentalTime::class)
    fun displayDate(): String {
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val mmDd = "${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
        return if (date.year == currentYear) mmDd else "${date.year}-$mmDd"
    }
}
