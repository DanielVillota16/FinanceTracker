package doug.financetracker.domain.repository

import doug.financetracker.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun getOrCreate(name: String): Long
    suspend fun delete(tag: Tag)
}
