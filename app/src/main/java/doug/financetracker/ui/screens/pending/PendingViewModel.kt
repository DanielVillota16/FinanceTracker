package doug.financetracker.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import doug.financetracker.domain.model.PendingItem
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

    val items: StateFlow<List<PendingItem>> = pending.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed interface Event {
        data class OpenEdit(val pendingId: Long) : Event
        data class Error(val message: String) : Event
    }

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** One-tap confirm; ambiguous items fall through to the edit form. */
    fun confirmItem(id: Long) {
        viewModelScope.launch {
            try {
                confirm.confirm(id)
            } catch (_: ConfirmPendingItem.AmbiguousKind) {
                eventChannel.send(Event.OpenEdit(id))
            } catch (_: ConfirmPendingItem.NeedsAccountSelection) {
                eventChannel.send(Event.OpenEdit(id))
            } catch (e: Exception) {
                eventChannel.send(Event.Error(e.message ?: "Could not confirm"))
            }
        }
    }

    fun dismissItem(id: Long) {
        viewModelScope.launch { pending.dismiss(id) }
    }
}
