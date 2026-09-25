package doug.financetracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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

/**
 * Contract for the add/edit form's date field: it always holds UTC midnight
 * of the user's LOCAL calendar day.
 *
 * Why: Material's DatePicker returns UTC midnight. Interpreting that instant
 * in a negative-offset zone (e.g. Bogotá, UTC-5) yields the PREVIOUS day, so
 * saving "today" stored yesterday. Normalizing every date (picked, prefilled,
 * default) through here — and reading it back in UTC — keeps the calendar day
 * exact regardless of zone.
 */
fun startOfLocalDayUtc(instantMillis: Long): Long =
    Instant.ofEpochMilli(instantMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Normalizes DatePicker output, which is ALREADY UTC midnight of the intended
 * day: reading its UTC date back is the identity, and it stays correct even
 * if the value ever arrives as a non-midnight instant on that UTC day.
 */
fun startOfUtcDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun formatFormDate(dateMillis: Long): String =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).format(DATE_FMT)

fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
    // dateMillis is UTC midnight of the local day (see startOfLocalDayUtc).
    val date: LocalDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
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
