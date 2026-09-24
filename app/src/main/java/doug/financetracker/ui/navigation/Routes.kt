package doug.financetracker.ui.navigation

object Routes {
    const val TRANSACTIONS = "transactions"
    const val PENDING = "pending"
    const val SETTINGS = "settings"
    const val ADD_TRANSACTION = "add_transaction?editId={editId}&pendingId={pendingId}"

    fun addTransaction(editId: Long? = null, pendingId: Long? = null): String =
        "add_transaction?editId=${editId ?: -1}&pendingId=${pendingId ?: -1}"

    fun pendingEdit(pendingId: Long): String = addTransaction(pendingId = pendingId)
}
