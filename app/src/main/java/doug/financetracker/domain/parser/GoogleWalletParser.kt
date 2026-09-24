package doug.financetracker.domain.parser

/**
 * Parser for Google Wallet payment notifications
 * (package `com.google.android.apps.walletnfcrel`).
 *
 * NOTE: exact wording is based on common Spanish Wallet notifications, not on
 * captured samples yet. Supported shapes:
 * - "Pagaste $45.900 en DROGUERIA ALEMANA con tu tarjeta •• 4821" → EXPENSE
 * - "Compra por $20.000 en TIENDA X" → EXPENSE
 * - "Recibiste $200.000 de JUAN PEREZ" → INCOME
 *
 * Refine with real payloads when available; the Settings test hook ingests
 * pasted notification text through this same parser.
 */
class GoogleWalletParser : NotificationParser {
    override val institution: String = "Google Wallet"

    private val expenseRx = Regex(
        """(?:pagaste|compra(?:ste)?(?:\s+por)?|cargo(?:\s+de)?)\s+\$?\s*([\d.,]+)\s+en\s+(.+?)(?:\s+con\s+(?:tu\s+)?tarjeta.*)?(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val incomeRx = Regex(
        """(?:recibiste|te enviaron|abono(?:\s+de)?)\s+\$?\s*([\d.,]+)(?:\s+de\s+(.+?))?(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val cardHintRx = Regex(
        """•{2,}\s*(\d{4})|terminad[ao]\s+en\s+(\d{4})|\*(\d{3,6})"""
    )

    override fun matchesPackage(packageName: String): Boolean =
        packageName == "com.google.android.apps.walletnfcrel" ||
            packageName == "com.google.android.apps.wallet" ||
            ("wallet" in packageName.lowercase() && "google" in packageName.lowercase())

    override fun canHandle(raw: String): Boolean =
        expenseRx.containsMatchIn(raw) || incomeRx.containsMatchIn(raw) ||
            Regex("""google\s+wallet""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    override fun parse(raw: String): ParsedTransaction? {
        // Only consulted for Wallet packages; be lenient here.
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

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
        val m = cardHintRx.find(raw) ?: return null
        val digits = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        return AccountHint(lastDigits = digits, label = "Wallet")
    }
}
