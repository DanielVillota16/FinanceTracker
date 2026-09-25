package doug.financetracker.domain.correlation

import doug.financetracker.domain.model.Account
import doug.financetracker.domain.parser.AccountHint

/**
 * Matches parser account hints (suffixes, labels, Cash) to configured
 * accounts. Shared by one-tap confirm and transfer matching so both resolve
 * identically. Pure and unit-tested.
 */
object AccountResolver {

    fun resolve(hint: AccountHint?, allAccounts: List<Account>): Account? {
        if (hint == null) return null
        if (hint.isCash) {
            return allAccounts.firstOrNull { it.accountType == "CASH" }
                ?: allAccounts.firstOrNull { it.name.equals("Cash", ignoreCase = true) }
        }
        if (hint.lastDigits.isNotEmpty()) {
            allAccounts.firstOrNull { it.identifierSuffix == hint.lastDigits }?.let { return it }
        }
        // Label-only hints (e.g. "Nequi") match by institution or account name.
        hint.label?.takeIf { it.isNotBlank() }?.let { label ->
            allAccounts.firstOrNull {
                it.institution.equals(label, ignoreCase = true) ||
                    it.name.equals(label, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }
}
