import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


open class DateRecord(val date: LocalDateTime)

class BlogRecord(
    val header: BlogHeader,
    val serverPath: ServerPath,
) : DateRecord(KoiroCatCafe.parseTomlDateTime(header.date))

class ActiveRecord(
    val header: ActiveHeader,
    val markdownContent: String, // in markdown
    val sourcePath: Path,
) : DateRecord(KoiroCatCafe.parseTomlDateTime(header.date))

class DevlogRecord(val header: DevlogHeader, val serverPath: ServerPath, val previewImagePath: ServerPath?) :
    DateRecord(KoiroCatCafe.parseTomlDateTime(header.date))

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
}
