package doug.financetracker.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.repository.PendingReviewRepository
import doug.financetracker.domain.usecase.ConfirmPendingItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingViewModel(
    private val pending: PendingReviewRepository,
    private val confirm: ConfirmPendingItem
) : ViewModel() {

    val candidates: StateFlow<List<PendingCandidate>> = pending.observeCandidates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed interface Event {
        /** Review id to open in the edit form (primary member of a candidate). */
        data class OpenEdit(val pendingId: Long) : Event
        data class Error(val message: String) : Event
    }

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /**
     * One-tap confirm of a candidate via its primary member; confirming links
     * every member to the same transaction. Ambiguous items fall through to
     * the edit form.
     */
    fun confirmCandidate(candidate: PendingCandidate) {
        viewModelScope.launch {
            val primaryId = candidate.primary.id
            try {
                confirm.confirm(primaryId)
            } catch (_: ConfirmPendingItem.AmbiguousKind) {
                eventChannel.send(Event.OpenEdit(primaryId))
            } catch (_: ConfirmPendingItem.NeedsAccountSelection) {
                eventChannel.send(Event.OpenEdit(primaryId))
            } catch (e: Exception) {
                eventChannel.send(Event.Error(e.message ?: "Could not confirm"))
            }
        }
    }

    fun dismissCandidate(candidateId: Long) {
        viewModelScope.launch { pending.dismissCandidate(candidateId) }
    }
}
