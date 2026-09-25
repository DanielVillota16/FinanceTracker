package doug.financetracker.domain.parser

/**
 * Parser for Google Wallet payment notifications
 * (package `com.google.android.apps.walletnfcrel`).
 *
 * Real authoritative fixture (listener composes title + "\n" + text):
 * - Title: "TIEN IA D1 PSTO IAM I"
 *   Text:  "COP 99,320.00 with Mastercard Platinum ••1444"
 *   → EXPENSE 99320, counterparty = title verbatim, card suffix 1444,
 *     eventTime = notification timestamp (body carries no date).
 *
 * The masking glyph varies (•·∙*…), so suffix extraction accepts common
 * Unicode mask characters — but only in the card/payment portion (`with
 * <product> <masks><suffix>`), never bare four-digit numbers.
 *
 * Legacy Spanish shapes ("Pagaste … en …", "Recibiste …") are also kept.
 */
class GoogleWalletParser : NotificationParser {
    override val institution: String = "Google Wallet"

    /** Mask glyphs observed before the last-4 card suffix. */
    private val maskChars = """[•·∙⋅●○◦◾▪\*xX#\-–—]"""

    /** Real shape: "<TITLE>\nCOP 99,320.00 with Mastercard Platinum ••1444" */
    private val walletLineRx = Regex(
        """(?m)^([^\n]+)\n(?:COP|COL\$?)\s*\$?\s*([\d.,]+)\s+with\s+(.+?)\s*$maskChars{1,6}\s*(\d{4})\s*$"""
    )
    private val walletCardRx = Regex(
        """with\s+(.+?)\s*$maskChars{1,6}\s*(\d{4})(?:\s|$|\.)"""
    )
    private val expenseRx = Regex(
        """(?:pagaste|compra(?:ste)?(?:\s+por)?|cargo(?:\s+de)?)\s+\$?\s*([\d.,]+)\s+en\s+(.+?)(?:\s+con\s+(?:tu\s+)?tarjeta.*)?(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val incomeRx = Regex(
        """(?:recibiste|te enviaron|abono(?:\s+de)?)\s+\$?\s*([\d.,]+)(?:\s+de\s+(.+?))?(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val cardHintRx = Regex(
        """$maskChars{2,}\s*(\d{4})|terminad[ao]\s+en\s+(\d{4})|\*(\d{3,6})"""
    )

    override fun matchesPackage(packageName: String): Boolean =
        packageName == "com.google.android.apps.walletnfcrel" ||
            packageName == "com.google.android.apps.wallet" ||
            ("wallet" in packageName.lowercase() && "google" in packageName.lowercase())

    override fun canHandle(raw: String): Boolean =
        walletLineRx.containsMatchIn(raw) || expenseRx.containsMatchIn(raw) ||
            incomeRx.containsMatchIn(raw) ||
            Regex("""google\s+wallet""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    override fun parse(raw: String): ParsedTransaction? {
        // Only consulted for Wallet packages; be lenient here.
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

        walletLineRx.find(raw)?.let { m ->
            val title = ParserUtils.cleanCounterparty(m.groupValues[1])
            val amount = CopAmountParser.parse(m.groupValues[2])
            val product = ParserUtils.cleanCounterparty(m.groupValues[3])
            val suffix = m.groupValues[4]
            if (amount == null) warnings += "Amount could not be parsed."
            if (title == null) warnings += "Merchant not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.EXPENSE,
                institution = institution,
                sourceAccountHint = AccountHint(lastDigits = suffix, label = "Wallet"),
                destinationAccountHint = null,
                counterparty = title,
                timestampMillis = timestamp,
                reference = product,
                confidence = if (amount != null && title != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        expenseRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Merchant not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.EXPENSE,
                institution = institution,
                sourceAccountHint = cardHint(raw),
                destinationAccountHint = null,
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "Google Wallet",
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        incomeRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2].ifEmpty { null })
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Sender not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.INCOMING,
                transactionKind = TransactionKind.INCOME,
                institution = institution,
                sourceAccountHint = null,
                destinationAccountHint = cardHint(raw),
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "Google Wallet",
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        warnings += "Recognized as Google Wallet but no known shape matched."
        return ParsedTransaction(
            amountPesos = ParserUtils.extractAmount(raw),
            direction = null,
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME),
            institution = institution,
            sourceAccountHint = cardHint(raw),
            destinationAccountHint = null,
            counterparty = null,
            timestampMillis = timestamp,
            reference = "Google Wallet",
            confidence = Confidence.LOW,
            warnings = warnings
        )
    }

    private fun cardHint(raw: String): AccountHint? {
        // Prefer the card/payment portion ("with <product> <masks><suffix>");
        // never treat a bare four-digit number elsewhere as a card suffix.
        walletCardRx.find(raw)?.let { m ->
            return AccountHint(lastDigits = m.groupValues[2], label = "Wallet")
        }
        val m = cardHintRx.find(raw) ?: return null
        val digits = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        return AccountHint(lastDigits = digits, label = "Wallet")
    }
}
