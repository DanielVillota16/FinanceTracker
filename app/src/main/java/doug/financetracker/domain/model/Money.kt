package doug.financetracker.domain.model

import java.text.NumberFormat
import java.util.Locale

/** COP money helpers. Amounts are Long pesos — never Double/Float. */
object Money {
    private val COP_LOCALE: Locale = Locale.Builder().setLanguage("es").setRegion("CO").build()

    fun formatCop(amountPesos: Long): String {
        val nf = NumberFormat.getNumberInstance(COP_LOCALE)
        nf.maximumFractionDigits = 0
        return "$${nf.format(amountPesos)}"
    }

    /** Totals helper that structurally excludes transfers (spec §2). */
    data class Totals(
        val totalIncome: Long = 0L,
        val totalExpenses: Long = 0L,
        val totalTransfers: Long = 0L
    ) {
        val net: Long get() = totalIncome - totalExpenses
    }

    fun totalsOf(transactions: List<Transaction>): Totals {
        var income = 0L
        var expenses = 0L
        var transfers = 0L
        for (t in transactions) {
            when (t.type) {
                TransactionType.INCOME -> income += t.amount
                TransactionType.EXPENSE -> expenses += t.amount
                TransactionType.TRANSFER -> transfers += t.amount
            }
        }
        return Totals(income, expenses, transfers)
    }
}
