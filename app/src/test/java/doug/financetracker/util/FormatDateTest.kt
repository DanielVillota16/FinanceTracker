package doug.financetracker.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * The DatePicker returns UTC midnight. In negative-offset zones (Bogotá,
 * UTC-5) that instant falls on the PREVIOUS local day, so saving "today"
 * stored yesterday. The form therefore normalizes every date through
 * [startOfLocalDayUtc] and reads it back in UTC.
 */
class FormatDateTest {

    private val bogota = ZoneId.of("America/Bogota")
    private lateinit var previousDefault: TimeZone

    @Before
    fun setZone() {
        previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(bogota))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(previousDefault)
    }

    @Test
    fun `picker utc midnight for today stays today`() {
        val today = LocalDate.now(bogota)
        // What Material's DatePicker hands us for "today".
        val pickerMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // Naive interpretation (the old bug) yields yesterday.
        val naive = Instant.ofEpochMilli(pickerMillis).atZone(bogota).toLocalDate()
        assertEquals(today.minusDays(1), naive)

        val normalized = startOfUtcDay(pickerMillis)
        val combined = combineDateAndTime(normalized, 10, 30)
        assertEquals(today, Instant.ofEpochMilli(combined).atZone(bogota).toLocalDate())
        assertEquals(10, Instant.ofEpochMilli(combined).atZone(bogota).hour)
    }

    @Test
    fun `prefilled instants keep their local day`() {
        val instant = todayAt(2026, 9, 25, 8, 27)
        val normalized = startOfLocalDayUtc(instant)
        val combined = combineDateAndTime(
            normalized,
            hourOf(instant),
            minuteOf(instant)
        )
        val result = Instant.ofEpochMilli(combined).atZone(bogota)
        assertEquals(LocalDate.of(2026, 9, 25), result.toLocalDate())
        assertEquals(8, result.hour)
        assertEquals(27, result.minute)
    }

    @Test
    fun `form date label shows the picked day`() {
        val normalized = startOfLocalDayUtc(todayAt(2026, 9, 25, 23, 45))
        // Late-night instant: UTC date differs, local label must not.
        assertEquals("Sep 25, 2026", formatFormDate(normalized))
    }

    private fun todayAt(y: Int, m: Int, d: Int, h: Int, mi: Int): Long =
        LocalDate.of(y, m, d).atTime(h, mi).atZone(bogota).toInstant().toEpochMilli()
}
