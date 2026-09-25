package doug.financetracker.domain.parser

/**
 * Parser for BBVA Colombia app notifications (package `co.com.bbva.mb`,
 * plus any other `*bbva*` package variant).
 *
 * Real authoritative fixtures (credit-card purchases — the only BBVA product
 * currently owned, so transfers/ATM shapes are intentionally unsupported):
 * - Play Store: "Hola, realizaste una compra por $4,000.00 en Google doan toan
 *   con tu tarjeta BBVA *1444. El 2026-09-24 a las 13:06."
 *   → EXPENSE 4000, "Google doan toan" (verbatim), *1444, 2026-09-24 13:06
 * - Physical via Wallet: "Hola, realizaste una compra por $99,320.00 en
 *   Tien ia d1 psto con tu tarjeta BBVA *1444. El 2026-09-24 a las 14:53."
 *   → EXPENSE 99320, "Tien ia d1 psto", *1444, 2026-09-24 14:53
 *
 * Merchant text is preserved exactly as reported — never "corrected".
 */
class BbvaParser : NotificationParser {
    override val institution: String = "BBVA"

    /** Real shape: "... compra por $X en MERCHANT con tu tarjeta BBVA *XXXX. El DATE ..." */
    private val bbvaCardPurchaseRx = Regex(
        """realizaste\s+una\s+compra\s+por\s+\$?\s*([\d.,]+)\s+en\s+(.+?)\s+con\s+tu\s+tarjeta\s+BBVA\s+\*(\d{3,6})""",
        RegexOption.IGNORE_CASE
    )
    private val purchaseRx = Regex(
        """(?:compra(?:ste)?|pago)(?:\s+por)?(?:\s+valor\s+de)?\s+\$?\s*([\d.,]+)\s+en\s+(.+?)(?:\s+con\s+tu\s+tarjeta|\s+el\s+|\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val incomeRx = Regex(
        """(?:transferencia recibida|recibiste|abono)(?:\s+de\s+(.+?))?\s+por\s+\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )
    private val incomeAltRx = Regex(
        """recibiste\s+\$?\s*([\d.,]+)\s+de\s+(.+?)(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val transferRx = Regex(
        """(?:transferiste|transferencia\s+(?:a|enviada\s+a))\s+\$?\s*([\d.,]+)\s+a\s+(.+?)(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )
    private val atmRx = Regex(
        """retir[oa](?:ste|aste)?\s+(?:de\s+)?\$?\s*([\d.,]+)(?:\s+en\s+cajero\s+(.+?))?(?:\s*$|\.)""",
        RegexOption.IGNORE_CASE
    )

    override fun matchesPackage(packageName: String): Boolean =
        "bbva" in packageName.lowercase()

    override fun canHandle(raw: String): Boolean =
        Regex("""bbva""", RegexOption.IGNORE_CASE).containsMatchIn(raw) ||
            bbvaCardPurchaseRx.containsMatchIn(raw) ||
            purchaseRx.containsMatchIn(raw) || incomeRx.containsMatchIn(raw) ||
            incomeAltRx.containsMatchIn(raw) || transferRx.containsMatchIn(raw) ||
            atmRx.containsMatchIn(raw)

    override fun parse(raw: String): ParsedTransaction? {
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

        bbvaCardPurchaseRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            val suffix = m.groupValues[3]
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Merchant not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.EXPENSE,
                institution = institution,
                sourceAccountHint = AccountHint(lastDigits = suffix, label = "BBVA"),
                destinationAccountHint = null,
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "BBVA",
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        purchaseRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Merchant not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.EXPENSE,
                institution = institution,
                sourceAccountHint = ParserUtils.extractAccountHint(raw),
                destinationAccountHint = null,
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "BBVA",
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        transferRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val dest = ParserUtils.cleanCounterparty(m.groupValues[2])
            warnings += "Transfer destination \"${dest ?: "unknown"}\" ownership is unknown; " +
                "confirm whether this is an internal transfer or an expense."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.UNKNOWN,
                possibleKinds = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
                institution = institution,
                sourceAccountHint = ParserUtils.extractAccountHint(raw),
                destinationAccountHint = null,
                counterparty = dest,
                timestampMillis = timestamp,
                reference = "BBVA",
                confidence = Confidence.MEDIUM,
                warnings = warnings
            )
        }

        atmRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val atm = ParserUtils.cleanCounterparty(m.groupValues[2].ifEmpty { null })
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.TRANSFER,
                institution = institution,
                sourceAccountHint = ParserUtils.extractAccountHint(raw),
                destinationAccountHint = AccountHint.CASH,
                counterparty = null,
                timestampMillis = timestamp,
                reference = atm,
                confidence = if (amount != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        incomeRx.find(raw)?.let { m ->
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[1].ifEmpty { null })
            val amount = CopAmountParser.parse(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.INCOMING,
                transactionKind = TransactionKind.INCOME,
                institution = institution,
                sourceAccountHint = null,
                destinationAccountHint = ParserUtils.extractAccountHint(raw),
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "BBVA",
                confidence = if (amount != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        incomeAltRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.INCOMING,
                transactionKind = TransactionKind.INCOME,
                institution = institution,
                sourceAccountHint = null,
                destinationAccountHint = ParserUtils.extractAccountHint(raw),
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = "BBVA",
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        warnings += "Recognized as BBVA but no known shape matched."
        return ParsedTransaction(
            amountPesos = ParserUtils.extractAmount(raw),
            direction = null,
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER),
            institution = institution,
            sourceAccountHint = ParserUtils.extractAccountHint(raw),
            destinationAccountHint = null,
            counterparty = null,
            timestampMillis = timestamp,
            reference = "BBVA",
            confidence = Confidence.LOW,
            warnings = warnings
        )
    }
}
