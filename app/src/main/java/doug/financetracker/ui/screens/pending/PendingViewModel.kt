package doug.financetracker.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.PendingCandidate
import doug.financetracker.domain.repository.PendingReviewRepository
import doug.financetracker.domain.usecase.ConfirmPendingItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
        data class Info(val message: String) : Event
    }

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    // Bulk selection ---------------------------------------------------------

    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    fun setSelecting(active: Boolean) {
        _selecting.value = active
        if (!active) _selected.value = emptySet()
    }

    fun toggleSelect(candidateId: Long) {
        _selected.value = _selected.value.let {
            if (candidateId in it) it - candidateId else it + candidateId
        }
    }

    /** Confirm every selected candidate; ambiguous ones are counted, not guessed. */
    fun confirmSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val current = candidates.value.associateBy { it.id }
            var confirmed = 0
            var needsReview = 0
            var failed = 0
            for (id in ids) {
                val candidate = current[id] ?: continue
                try {
                    confirm.confirm(candidate.primary.id)
                    confirmed++
                } catch (_: ConfirmPendingItem.AmbiguousKind) {
                    needsReview++
                } catch (_: ConfirmPendingItem.NeedsAccountSelection) {
                    needsReview++
                } catch (_: Exception) {
                    failed++
                }
            }
            _selecting.value = false
            _selected.value = emptySet()
            eventChannel.send(
                Event.Info(
                    buildString {
                        append("$confirmed confirmed")
                        if (needsReview > 0) append(", $needsReview need individual review")
                        if (failed > 0) append(", $failed failed")
                    }
                )
            )
        }
    }

    fun dismissSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                try {
                    pending.dismissCandidate(id)
                } catch (_: Exception) {
                }
            }
            _selecting.value = false
            _selected.value = emptySet()
            eventChannel.send(Event.Info("${ids.size} dismissed (evidence kept)"))
        }
    }

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
