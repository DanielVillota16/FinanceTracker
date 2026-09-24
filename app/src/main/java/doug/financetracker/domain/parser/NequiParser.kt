package doug.financetracker.domain.parser

/**
 * Parser for Nequi SMS messages (sender "Nequi", "NEQUI:" prefix).
 *
 * Supported shapes:
 * - Payment:  "NEQUI: Pagaste 1.000,00 en GOOGLE *Google One ..."
 * - Payment:  "NEQUI: Pagaste 9.300,00 en GOOGLE YouTube ..."
 * - Received: "NEQUI: Recibiste 50.000,00 de <SENDER> ..."
 *
 * Nequi messages carry no account suffix; the account is implicitly the
 * user's Nequi account, resolved at review time.
 */
class NequiParser : TransactionParser {
    override val institution: String = "Nequi"

    private val paymentRx =
        Regex("""pagaste\s+([\d.,]+)\s+en\s+(.+?)(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)
    private val receivedRx =
        Regex("""recibiste\s+([\d.,]+)\s+de\s+(.+?)(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)

    override fun canHandle(raw: String): Boolean =
        Regex("""nequi""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    override fun parse(raw: String): ParsedTransaction? {
        if (!canHandle(raw)) return null
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

        paymentRx.find(raw)?.let { m ->
            val amount = CopAmountParser.parse(m.groupValues[1])
            val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
            if (amount == null) warnings += "Amount could not be parsed."
            if (counterparty == null) warnings += "Merchant not identified."
            return ParsedTransaction(
                amountPesos = amount,
                direction = Direction.OUTGOING,
                transactionKind = TransactionKind.EXPENSE,
                institution = institution,
                sourceAccountHint = AccountHint(lastDigits = "", label = "Nequi"),
                destinationAccountHint = null,
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = null,
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
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
                destinationAccountHint = AccountHint(lastDigits = "", label = "Nequi"),
                counterparty = counterparty,
                timestampMillis = timestamp,
                reference = null,
                confidence = if (amount != null && counterparty != null) Confidence.HIGH else Confidence.MEDIUM,
                warnings = warnings
            )
        }

        warnings += "Recognized as Nequi but no known message shape matched."
        return ParsedTransaction(
            amountPesos = ParserUtils.extractAmount(raw),
            direction = null,
            transactionKind = TransactionKind.UNKNOWN,
            possibleKinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME),
            institution = institution,
            sourceAccountHint = AccountHint(lastDigits = "", label = "Nequi"),
            destinationAccountHint = null,
            counterparty = null,
            timestampMillis = timestamp,
            reference = null,
            confidence = Confidence.LOW,
            warnings = warnings
        )
    }
}
