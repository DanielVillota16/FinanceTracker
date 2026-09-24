package doug.financetracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UI_LOCALE: Locale = Locale.ENGLISH

private val DATE_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", UI_LOCALE)
private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", UI_LOCALE)
private val TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", UI_LOCALE)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FMT)

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FMT)

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FMT)

fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    val date: LocalDate = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
    return date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

fun hourOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).hour

fun minuteOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).minute

/**
 * Parse user-typed COP input into exact pesos (Long).
 * Handles plain digits plus thousand separators; a trailing exactly-2-digit
 * decimal part (",00" / ".00") is dropped since COP has no fractional pesos.
 */
fun parseAmountToPesos(raw: String): Long? {
    val clean = raw.trim().replace("\\s".toRegex(), "")
    if (clean.isEmpty()) return null
    val withoutCents = clean.replace(Regex("[.,](\\d{2})$"), "")
    val digits = withoutCents.replace(Regex("[^0-9]"), "")
    if (digits.isEmpty()) return null
    return digits.toLongOrNull()?.takeIf { it > 0 }
}
