package doug.financetracker.ui.navigation

object Routes {
    const val TRANSACTIONS = "transactions"
    const val PENDING = "pending"
    const val SETTINGS = "settings"
    const val ADD_TRANSACTION = "add_transaction?editId={editId}"

    fun addTransaction(editId: Long? = null): String =
        if (editId == null) "add_transaction?editId=-1" else "add_transaction?editId=$editId"
}
