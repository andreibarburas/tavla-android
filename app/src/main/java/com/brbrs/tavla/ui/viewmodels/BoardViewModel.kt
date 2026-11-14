package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.data.Stack
import com.brbrs.tavla.reminders.ReminderScheduler
import com.brbrs.tavla.settings.SettingsRepository
import com.brbrs.tavla.solidtime.SolidtimeRepository
import com.brbrs.tavla.ui.PendingUndo
import com.brbrs.tavla.ui.UndoController
import com.brbrs.tavla.ui.components.friendlyError
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardFilter(val label: String) {
    All("All cards"),
    Mine("Assigned to me"),
    WithDue("With due date"),
    Overdue("Overdue"),
    Done("Done"),
    Archived("Archived"),
}

data class BoardUiState(
    val board: Board? = null,
    val boardIcon: String? = null,
    val boardImage: String? = null,
    val stacks: List<Stack> = emptyList(),
    val cardsByStack: Map<Long, List<Card>> = emptyMap(),
    val totalByStack: Map<Long, Int> = emptyMap(),
    val filter: CardFilter = CardFilter.All,
    val labelFilter: Long? = null,
    val showLabels: Boolean = true,
    val cardViewMode: String = "list",
    val cardDensity: String = "comfortable",
    val runningCardId: Long? = null,
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

private data class BoardLocal(
    val filter: CardFilter = CardFilter.All,
    val labelFilter: Long? = null,
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

private data class BoardExtras(
    val username: String,
    val display: com.brbrs.tavla.ui.theme.DisplayPreferences,
    val boardIcon: String?,
    val boardImage: String?,
    val runningCardId: Long?,
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeckRepository,
    authRepository: AuthRepository,
    displayPrefs: DisplayPreferencesRepository,
    settingsRepository: SettingsRepository,
    coverRepository: com.brbrs.tavla.settings.BoardCoverRepository,
    private val solidtime: SolidtimeRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val boardId: Long = checkNotNull(savedStateHandle.get<Long>("boardId"))
    private val local = MutableStateFlow(BoardLocal())
    private val undoCtl = UndoController(viewModelScope)
    val pendingUndo: StateFlow<PendingUndo?> = undoCtl.pending

    val uiState: StateFlow<BoardUiState> = combine(
        repository.observeBoard(boardId),
        repository.observeStacks(boardId),
        repository.observeCards(boardId),
        combine(
            authRepository.session.map { it?.username.orEmpty() },
            displayPrefs.preferences,
            settingsRepository.settings.map { it.boardIcons[boardId] },
            coverRepository.readyCovers.map { it[boardId] },
            solidtime.runningTimer.map { rt -> rt?.takeIf { it.boardId == boardId }?.cardId },
        ) { u, d, icon, coverPath, runningCardId -> BoardExtras(u, d, icon, coverPath, runningCardId) },
        local,
    ) { board, stacks, cards, extras, l ->
        val username = extras.username
        val display = extras.display
        val boardIcon = extras.boardIcon
        val boardImage = extras.boardImage
        val now = System.currentTimeMillis()
        // repository.observeCards now includes archived cards -- #10, so they can be browsed and
        // restored via the Archived filter instead of becoming invisible once archived. Every other
        // filter keeps excluding them, same as before that query change.
        val filtered = cards.filter { c ->
            val passesFilter = when (l.filter) {
                CardFilter.All      -> !c.archived
                CardFilter.Mine     -> !c.archived && c.assignees.any { it.uid == username }
                CardFilter.WithDue  -> !c.archived && c.dueDate != null
                CardFilter.Overdue  -> !c.archived && c.dueDate != null && c.dueDate < now && c.doneAt == null
                CardFilter.Done     -> !c.archived && c.doneAt != null
                CardFilter.Archived -> c.archived
            }
            val passesLabel = l.labelFilter == null || c.labels.any { it.id == l.labelFilter }
            passesFilter && passesLabel
        }
        val activeCards = cards.filterNot { it.archived }
        BoardUiState(
            board = board,
            boardIcon = boardIcon,
            boardImage = boardImage,
            stacks = stacks,
            cardsByStack = filtered.groupBy { it.stackId },
            totalByStack = activeCards.groupingBy { it.stackId }.eachCount(),
            filter = l.filter,
            labelFilter = l.labelFilter,
            showLabels = display.showLabelsOnCards,
            cardViewMode = display.cardViewMode,
            cardDensity = display.cardDensity,
            runningCardId = extras.runningCardId,
            isRefreshing = l.isRefreshing,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init { refresh() }

    override fun onCleared() { undoCtl.flush() }

    fun refresh() {
        if (local.value.isRefreshing) return
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true) }
            val r = runCatching { repository.refreshBoard(boardId) }
            local.update { it.copy(isRefreshing = false, message = r.exceptionOrNull()?.let(::friendlyError) ?: it.message) }
        }
    }

    fun setFilter(filter: CardFilter) = local.update { it.copy(filter = filter) }
    fun setLabelFilter(labelId: Long?) = local.update { it.copy(labelFilter = labelId) }
    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun runAction(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            val r = runCatching { block() }
            local.update { it.copy(message = r.exceptionOrNull()?.let(::friendlyError) ?: success) }
        }
    }

    fun createCard(stackId: Long, title: String) = runAction {
        val order = (uiState.value.totalByStack[stackId] ?: 0)
        repository.createCard(boardId, stackId, title.trim(), order)
    }

    fun createStack(title: String) = runAction {
        val order = (uiState.value.stacks.maxOfOrNull { it.order } ?: -1) + 1
        repository.createStack(boardId, title.trim(), order)
    }

    fun renameStack(stack: Stack, title: String) = runAction { repository.renameStack(stack, title.trim()) }

    fun deleteStack(stack: Stack) {
        undoCtl.schedule("List deleted") { repository.deleteStack(stack) }
    }

    fun moveCard(card: Card, targetStackId: Long) {
        if (card.stackId == targetStackId) return
        undoCtl.schedule("Card moved") {
            val order = uiState.value.totalByStack[targetStackId] ?: 0
            repository.moveCard(card, targetStackId, order)
        }
    }

    fun undoAction() = undoCtl.cancel()

    /** #06: drag-and-drop. Covers both reordering within a list and dropping into a different one. */
    fun reorderCard(card: Card, targetStackId: Long, targetIndex: Int) = runAction {
        repository.moveCard(card, targetStackId, targetIndex)
    }

    /** #06: drag-reordering a list's own tab. */
    fun reorderStack(stack: Stack, newIndex: Int) = runAction {
        repository.reorderStack(stack, newIndex)
    }

    /** #09: quick checkmark toggle from the board view, without opening the card. Cancels any
     *  pending due-date reminder the same way CardViewModel.setDone does -- this path was missing
     *  that cancellation, which left stale reminders firing for cards completed from here. */
    fun setCardDone(card: Card, done: Boolean) = runAction {
        repository.setCardDone(card, done)
        if (done) reminderScheduler.cancel(card.id, card.boardId)
    }
}
