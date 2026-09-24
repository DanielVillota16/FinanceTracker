package doug.financetracker.domain.model

/**
 * Financial account owned (or not) by the user.
 * Only the minimum identifier suffix is stored (e.g. last 4 digits).
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val institution: String,
    val accountType: String = "",
    val identifierSuffix: String = "",
    val isOwnedByUser: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayLabel: String
        get() = if (identifierSuffix.isBlank()) name else "$name ****$identifierSuffix"

    companion object {
        /** Default user-owned accounts from the spec (Phase 1 seed). */
        fun defaults(now: Long = System.currentTimeMillis()) = listOf(
            Account(name = "Bancolombia", institution = "Bancolombia", isOwnedByUser = true, createdAt = now),
            Account(name = "Nequi", institution = "Nequi", isOwnedByUser = true, createdAt = now),
            Account(name = "DAVIbank", institution = "DAVIbank", isOwnedByUser = true, createdAt = now),
            Account(name = "BBVA", institution = "BBVA", isOwnedByUser = true, createdAt = now),
            Account(name = "Cash", institution = "Cash", accountType = "CASH", isOwnedByUser = true, createdAt = now)
        )
    }
}
