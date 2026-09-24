package doug.financetracker.domain.parser

/**
 * Parses COP amounts from Colombian/institution message formats into exact
 * pesos (Long). Never Double — COP has no fractional pesos in practice.
 *
 * Supported inputs (whitespace and currency symbols are ignored):
 * - "3,050,707.00" / "3.050.707,00" → 3050707  (both separators: rightmost is decimal)
 * - "60,000.00" / "29.000,00"       → 60000 / 29000
 * - "492,292" / "100.000" / "40,000" → thousands → 492292 / 100000 / 40000
 * - "4500.00" / "1000,00"            → trailing 2-digit decimals are dropped
 * - "4500"                           → 4500
 *
 * Rules are explicit, not guessing:
 * - both '.' and ',' present → the rightmost one is the decimal separator;
 *   any fractional digits are dropped (COP minor unit = 1 peso).
 * - single separator kind:
 *   - groups of exactly 3 digits (e.g. "492,292") → thousands separators.
 *   - a single trailing group of 2 digits (e.g. "4500.00") → decimals, dropped.
 *   - anything else → null (malformed, caller must warn, never guess).
 *
 * Returns null when no parseable amount is present.
 */
object CopAmountParser {

    fun parse(raw: String): Long? {
        // Keep only the numeric core: digits plus both possible separators.
        val core = raw.filter { it.isDigit() || it == '.' || it == ',' }
        if (core.isEmpty() || core.none { it.isDigit() }) return null

        val normalized: String = if ('.' in core && ',' in core) {
            // Rightmost separator is the decimal one; drop the fraction (COP pesos).
            val lastDot = core.lastIndexOf('.')
            val lastComma = core.lastIndexOf(',')
            val decimalIndex = maxOf(lastDot, lastComma)
            core.substring(0, decimalIndex).filter { it.isDigit() }
        } else {
            val sep = if ('.' in core) '.' else if (',' in core) ',' else null
            if (sep == null) {
                core
            } else {
                val parts = core.split(sep)
                val intPart = parts.first()
                val fracParts = parts.drop(1)
                if (intPart.isEmpty() || intPart.any { !it.isDigit() }) return null
                if (fracParts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
                if (fracParts.size >= 2) {
                    // Multiple groups: every group after the first must be 3 digits
                    // ("492,292" ok; "12,34,56" malformed).
                    if (!fracParts.all { it.length == 3 }) return null
                    (listOf(intPart) + fracParts).joinToString("")
                } else {
                    val frac = fracParts.single()
                    when (frac.length) {
                        3 -> intPart + frac           // thousands: "100.000"
                        2 -> intPart                 // decimals: "4500.00" → pesos
                        else -> return null          // "9,3" / "9,3000": ambiguous → refuse
                    }
                }
            }
        }
        if (normalized.isEmpty() || normalized.length > 15) return null
        return normalized.toLongOrNull()
    }
}
