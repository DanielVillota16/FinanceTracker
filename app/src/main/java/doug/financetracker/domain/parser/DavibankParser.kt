package doug.financetracker.domain.parser

/**
 * Parser for Davivienda SMS messages (sender markers "DAVIbank", "Davivienda", "Daviplata").
 *
 * Supported shapes:
 * - Card purchase: "DAVIbank: Realizaste transaccion en AIRBNB * HMJYTBHWZW por 492,292 ..."
 * - Transfer out:  "... transferiste <AMOUNT> a <DEST> ..." (destination ownership
 *   unknown → UNKNOWN with [TRANSFER, EXPENSE] hints)
 * - Received:      "... recibiste <AMOUNT> de <SENDER> ..." → INCOME
 */
class DavibankParser : TransactionParser {
    override val institution: String = "DAVIbank"

    private val purchaseRx =
        Regex("""realizaste\s+transacci[oó]n\s+en\s+(.+?)\s+por\s+\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val transferOutRx =
        Regex("""transferiste\s+\$?\s*([\d.,]+)\s+a\s+(.+?)(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)
    private val receivedRx =
        Regex("""recibiste\s+\$?\s*([\d.,]+)\s+de\s+(.+?)(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)

    override fun canHandle(raw: String): Boolean =
        Regex("""davi(bank|vienda|plata)""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    override fun parse(raw: String): ParsedTransaction? {
        if (!canHandle(raw)) return null
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

        purchaseRx.find(raw)?.let { m ->
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[1])
            val amount = CopAmountParser.parse(m.groupValues[2])
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
                reference = null,
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        transferOutRx.find(raw)?.let { m ->
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
                reference = null,
                confidence = Confidence.MEDIUM,
                warnings = warnings
            )
        }

        receivedRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Sender not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.INCOMING,
                transactionKind = TransactionKind.INCOME,
                institution = institution,
                sourceAccountHint = null,
                destinationAccountHint = ParserUtils.extractAccountHint(raw),
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = null,
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        warnings += "Recognized as DAVIbank but no known message shape matched."
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
            reference = null,
            confidence = Confidence.LOW,
            warnings = warnings
        )
    }
}
