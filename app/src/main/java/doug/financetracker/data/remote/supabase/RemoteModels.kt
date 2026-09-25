package doug.financetracker.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PostgREST row shapes (snake_case mirrors the migration). IDs are
 * client-generated UUIDs so inserts never depend on a returning clause.
 * No source-evidence types exist here by design — official data only.
 */
@Serializable
data class RemoteAccount(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("institution") val institution: String = "",
    @SerialName("account_type") val accountType: String = "",
    @SerialName("identifier_suffix") val identifierSuffix: String = "",
    @SerialName("is_owned_by_user") val isOwnedByUser: Boolean = true,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)

@Serializable
data class RemoteTag(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)

@Serializable
data class RemoteTransaction(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("amount") val amount: Long,
    @SerialName("date_time_ms") val dateTimeMs: Long,
    @SerialName("description") val description: String = "",
    @SerialName("counterparty") val counterparty: String = "",
    @SerialName("source_account_id") val sourceAccountId: String? = null,
    @SerialName("destination_account_id") val destinationAccountId: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)

@Serializable
data class RemoteTransactionTag(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("tag_id") val tagId: String
)
