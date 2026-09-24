package doug.financetracker.parser

import doug.financetracker.domain.parser.CopAmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopAmountParserTest {

    @Test
    fun `both separators - rightmost is decimal`() {
        assertEquals(3_050_707L, CopAmountParser.parse("3,050,707.00"))
        assertEquals(3_050_707L, CopAmountParser.parse("3.050.707,00"))
        assertEquals(29_000L, CopAmountParser.parse("29.000,00"))
        assertEquals(4_500L, CopAmountParser.parse("4,500.00"))
        assertEquals(60_000L, CopAmountParser.parse("60,000.00"))
        assertEquals(1_000L, CopAmountParser.parse("1.000,00"))
    }

    @Test
    fun `single separator - thousands`() {
        assertEquals(492_292L, CopAmountParser.parse("492,292"))
        assertEquals(100_000L, CopAmountParser.parse("100.000"))
        assertEquals(40_000L, CopAmountParser.parse("40,000"))
        assertEquals(9_300L, CopAmountParser.parse("9,300"))
    }

    @Test
    fun `single separator - trailing 2-digit decimals are dropped`() {
        assertEquals(4_500L, CopAmountParser.parse("4500.00"))
        assertEquals(1_000L, CopAmountParser.parse("1000,00"))
    }

    @Test
    fun `plain digits`() {
        assertEquals(4_500L, CopAmountParser.parse("4500"))
        assertEquals(0L, CopAmountParser.parse("0"))
    }

    @Test
    fun `currency symbols and spaces are ignored`() {
        assertEquals(29_000L, CopAmountParser.parse("$29.000,00"))
        assertEquals(29_000L, CopAmountParser.parse("  $ 29.000,00 "))
    }

    @Test
    fun `malformed input returns null instead of guessing`() {
        assertNull(CopAmountParser.parse(""))
        assertNull(CopAmountParser.parse("abc"))
        assertNull(CopAmountParser.parse("12,34,56"))
        assertNull(CopAmountParser.parse("9,3"))
        assertNull(CopAmountParser.parse("9,3000"))
        assertNull(CopAmountParser.parse("...,,,"))
    }
}
