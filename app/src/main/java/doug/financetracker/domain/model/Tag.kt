package doug.financetracker.domain.model

/** Reusable classification label (e.g. "comida", "viaje"). */
data class Tag(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Remote backup/sync metadata (Phase 7). Null until first upload. */
    val remoteId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Tag name must not be blank" }
    }

    val normalizedName: String get() = name.trim().lowercase()
}
