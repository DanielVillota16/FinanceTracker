package doug.financetracker.domain.parser

/**
 * Parser for Bancolombia SMS messages (sender "Bancolombia").
 *
 * Supported shapes (amounts/counterparties/dates vary):
 * - Purchase:       "Compraste $29.000,00 en BOLD SA*20 DE JU con tu T.Deb *0757 ..."
 * - Transfer out:   "Transferiste $4,500.00 desde tu cuenta *8494 ..."
 * - Transfer pay:   "Pagaste $975,500.00 a Banco Davivienda S A Zona Pa desde tu producto 8494 ..."
 * - BRE-B:          "Transferiste $60,000.00 a la llave <alias> ..."
 * - QR payment:     "Pagaste $40,000.00 por codigo QR [en <merchant>] ..."
 * - ATM withdrawal: "Retiraste $100.000,00 en <ATM_ID> [con tu T.Deb *0757] ..."
 * - Scheduled bill: "... pago Factura Programada <NAME> por $85.000,00 ..."
 * - Incoming:       "Recibiste una transferencia de <SENDER> por $3,050,707.00 ..."
 *
 * Outgoing transfers whose destination ownership is unknown are returned as
 * UNKNOWN with possibleKinds [TRANSFER, EXPENSE] — never auto-assumed.
 * ATM withdrawals are TRANSFERs with destination Cash (spec §14).
 */
class BancolombiaParser : TransactionParser {
    override val institution: String = "Bancolombia"

    private val purchaseRx =
        Regex("""compraste\s+\$?\s*([\d.,]+)\s+en\s+(.+?)\s+con tu\s+(.+?)(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)
    private val transferOutRx =
        Regex("""transferiste\s+\$?\s*([\d.,]+)\s+desde tu (?:cuenta|producto)\s+\*?(\d{3,6})(.*)""", RegexOption.IGNORE_CASE)
    /** Real shape: "Pagaste $975,500.00 a Banco Davivienda S A Zona Pa desde tu producto 8494 ..." */
    private val transferPayRx =
        Regex("""pagaste\s+\$?\s*([\d.,]+)\s+a\s+(.+?)\s+desde tu (?:cuenta|producto)\s+\*?(\d{3,6})""", RegexOption.IGNORE_CASE)
    private val brebRx =
        Regex("""transferiste\s+\$?\s*([\d.,]+)\s+a\s+la\s+llave\s+(\S+)(.*)""", RegexOption.IGNORE_CASE)
    private val destinationAccountRx =
        Regex("""a\s+la\s+cuenta\s+\*(\d{3,6})""", RegexOption.IGNORE_CASE)
    private val llaveRx =
        Regex("""a\s+la\s+llave\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val qrRx =
        Regex("""pagaste\s+\$?\s*([\d.,]+)\s+por\s+c[oó]digo\s+qr(?:\s+en\s+(.+?))?(?:\s+el\s+|\s*$|\.)""", RegexOption.IGNORE_CASE)
    private val atmRx =
        Regex("""retiraste\s+\$?\s*([\d.,]+)\s+en\s+(\S+)(.*)""", RegexOption.IGNORE_CASE)
    private val billRx =
        Regex("""factura\s+programada\s+(.+?)\s+por\s+\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val incomingRx =
        Regex("""recibiste(?:\s+una\s+transferencia)?\s+de\s+(.+?)\s+por\s+\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)

    override fun canHandle(raw: String): Boolean {
        if (Regex("""bancolombia""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return true
        val lower = raw.lowercase()
        return purchaseRx.containsMatchIn(lower) ||
            transferOutRx.containsMatchIn(lower) ||
            transferPayRx.containsMatchIn(lower) ||
            brebRx.containsMatchIn(lower) ||
            atmRx.containsMatchIn(lower) ||
            billRx.containsMatchIn(lower) ||
            incomingRx.containsMatchIn(lower) ||
            qrRx.containsMatchIn(lower)
    }

    override fun parse(raw: String): ParsedTransaction? {
        if (!canHandle(raw)) return null
        val warnings = mutableListOf<String>()
        val timestamp = ParserUtils.extractTimestamp(raw, warnings)

        parsePurchase(raw, timestamp, warnings)?.let { return it }
        parseTransferOut(raw, timestamp, warnings)?.let { return it }
        parseBreb(raw, timestamp, warnings)?.let { return it }
        parseTransferPay(raw, timestamp, warnings)?.let { return it }
        parseQr(raw, timestamp, warnings)?.let { return it }
        parseAtm(raw, timestamp, warnings)?.let { return it }
        parseBill(raw, timestamp, warnings)?.let { return it }
        parseIncoming(raw, timestamp, warnings)?.let { return it }

        warnings += "Recognized as Bancolombia but no known message shape matched."
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

    private fun base(
        amount: Long?,
        direction: Direction?,
        kind: TransactionKind,
        possible: List<TransactionKind> = listOf(kind),
        source: AccountHint?,
        dest: AccountHint?,
        counterparty: String?,
        timestamp: Long?,
        reference: String?,
        warnings: MutableList<String>
    ): ParsedTransaction {
        if (amount == null) warnings += "Amount could not be parsed."
        val confidence = when {
            amount == null || kind == TransactionKind.UNKNOWN -> Confidence.MEDIUM
            counterparty == null && reference == null &&
                kind != TransactionKind.TRANSFER -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }
        // UNKNOWN kinds can never be HIGH.
        val finalConfidence = if (kind == TransactionKind.UNKNOWN && confidence == Confidence.HIGH) {
            Confidence.MEDIUM
        } else confidence
        return ParsedTransaction(
            amountPesos = amount,
            direction = direction,
            transactionKind = kind,
            possibleKinds = possible,
            institution = institution,
            sourceAccountHint = source,
            destinationAccountHint = dest,
            counterparty = counterparty,
            timestampMillis = timestamp,
            reference = reference,
            confidence = finalConfidence,
            warnings = warnings.toList()
        )
    }

    private fun parsePurchase(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = purchaseRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
        val source = ParserUtils.extractAccountHint(m.groupValues[3])
            ?: ParserUtils.extractAccountHint(raw)
        if (counterparty == null) warnings += "Purchase counterparty not identified."
        if (source == null) warnings += "Source account not identified."
        return base(amount, Direction.OUTGOING, TransactionKind.EXPENSE, source = source,
            dest = null, counterparty = counterparty, timestamp = timestamp,
            reference = null, warnings = warnings)
    }

    private fun parseTransferOut(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {        val m = transferOutRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val source = AccountHint(lastDigits = m.groupValues[2])
        val tail = m.groupValues[3]
        val destMatch = destinationAccountRx.find(tail)
        val llaveMatch = llaveRx.find(tail)
        return if (destMatch != null) {
            warnings += "Destination ****${destMatch.groupValues[1]} ownership is unknown; " +
                "confirm whether this is an internal transfer or an expense."
            base(amount, Direction.OUTGOING, TransactionKind.UNKNOWN,
                possible = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
                source = source, dest = AccountHint(lastDigits = destMatch.groupValues[1]),
                counterparty = null, timestamp = timestamp, reference = null, warnings = warnings)
        } else if (llaveMatch != null) {
            val llave = llaveMatch.groupValues[1].trimEnd('.', ',', ';')
            warnings += "BRE-B transfer to llave \"$llave\"; destination ownership is unknown."
            base(amount, Direction.OUTGOING, TransactionKind.UNKNOWN,
                possible = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
                source = source, dest = null, counterparty = null, timestamp = timestamp,
                reference = llave, warnings = warnings)
        } else {
            warnings += "Transfer destination could not be identified."
            base(amount, Direction.OUTGOING, TransactionKind.UNKNOWN,
                possible = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
                source = source, dest = null, counterparty = null, timestamp = timestamp,
                reference = null, warnings = warnings)
        }
    }

    private fun parseBreb(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = brebRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val llave = m.groupValues[2].trimEnd('.', ',', ';')
        val source = ParserUtils.extractAccountHint(m.groupValues[3])
            ?: ParserUtils.extractAccountHint(raw)
        warnings += "BRE-B transfer to llave \"$llave\"; destination ownership is unknown."
        return base(amount, Direction.OUTGOING, TransactionKind.UNKNOWN,
            possible = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
            source = source, dest = null, counterparty = null, timestamp = timestamp,
            reference = llave.ifEmpty { null }, warnings = warnings)
    }

    private fun parseTransferPay(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = transferPayRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2])
        val source = AccountHint(lastDigits = m.groupValues[3])
        if (counterparty == null) warnings += "Transfer destination could not be identified."
        warnings += "Destination \"${counterparty ?: "unknown"}\" ownership is unknown; " +
            "confirm whether this is an internal transfer or an expense."
        return base(amount, Direction.OUTGOING, TransactionKind.UNKNOWN,
            possible = listOf(TransactionKind.TRANSFER, TransactionKind.EXPENSE),
            source = source, dest = null, counterparty = counterparty,
            timestamp = timestamp, reference = null, warnings = warnings)
    }

    private fun parseQr(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = qrRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val counterparty = ParserUtils.cleanCounterparty(m.groupValues[2].ifEmpty { null })
        if (counterparty == null) warnings += "QR merchant not identified."
        return base(amount, Direction.OUTGOING, TransactionKind.EXPENSE,
            source = ParserUtils.extractAccountHint(raw), dest = null,
            counterparty = counterparty, timestamp = timestamp,
            reference = "QR", warnings = warnings)
    }

    private fun parseAtm(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = atmRx.find(raw) ?: return null
        val amount = CopAmountParser.parse(m.groupValues[1])
        val atmId = m.groupValues[2].trimEnd('.', ',', ';')
        val source = ParserUtils.extractAccountHint(m.groupValues[3])
            ?: ParserUtils.extractAccountHint(raw)
        if (source == null) warnings += "Source account not identified."
        // Cash is a user-owned account: withdrawal = transfer to Cash (spec §14).
        return base(amount, Direction.OUTGOING, TransactionKind.TRANSFER,
            source = source, dest = AccountHint.CASH, counterparty = null,
            timestamp = timestamp, reference = atmId.ifEmpty { null }, warnings = warnings)
    }

    private fun parseBill(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = billRx.find(raw) ?: return null
        val counterparty = ParserUtils.cleanCounterparty(m.groupValues[1])
        val amount = CopAmountParser.parse(m.groupValues[2])
        if (counterparty == null) warnings += "Billed entity not identified."
        return base(amount, Direction.OUTGOING, TransactionKind.EXPENSE,
            source = ParserUtils.extractAccountHint(raw), dest = null,
            counterparty = counterparty, timestamp = timestamp,
            reference = "Factura Programada", warnings = warnings)
    }

    private fun parseIncoming(raw: String, timestamp: Long?, warnings: MutableList<String>): ParsedTransaction? {
        val m = incomingRx.find(raw) ?: return null
        val counterparty = ParserUtils.cleanCounterparty(m.groupValues[1])
        val amount = CopAmountParser.parse(m.groupValues[2])
        if (counterparty == null) warnings += "Sender not identified."
        return base(amount, Direction.INCOMING, TransactionKind.INCOME,
            source = null, dest = ParserUtils.extractAccountHint(raw),
            counterparty = counterparty, timestamp = timestamp,
            reference = null, warnings = warnings)
    }
}
