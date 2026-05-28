@file:OptIn(ExperimentalTime::class)

package htmlgen

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun LocalDateTime.formatToString(clock: Clock = Clock.System): String {
    val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    return if (year == today.year) {
        // 当年：只显示月日 (例如: 05-28)
        val thisYearFormat = LocalDateTime.Format {
            monthNumber(); char('-'); day()
        }
        this.format(thisYearFormat)
    } else {
        // 跨年：显示完整年月日 (例如: 2025-05-28)
        val otherYearFormat = LocalDateTime.Format {
            year(); char('-'); monthNumber(); char('-'); day()
        }
        this.format(otherYearFormat)
    }
}