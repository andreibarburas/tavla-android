package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.BoardCardCount
import com.brbrs.tavla.data.CardWithContext
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.settings.SettingsRepository
import com.brbrs.tavla.settings.TavlaSettings
import com.brbrs.tavla.solidtime.SolidtimeRepository
import com.brbrs.tavla.solidtime.timer.RunningTimer
import com.brbrs.tavla.ui.PendingUndo
import com.brbrs.tavla.ui.UndoController
import com.brbrs.tavla.ui.components.friendlyError
import com.brbrs.tavla.ui.theme.DisplayPreferences
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import com.brbrs.tavla.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoardsUiState(
    val boards: List<Board> = emptyList(),
    val archivedCount: Int = 0,
    val counts: Map<Long, BoardCardCount> = emptyMap(),
    val dueCards: List<CardWithContext> = emptyList(),
    /** #01: title/description matches across every open card, newest first. Only populated while searching. */
    val searchResults: List<CardWithContext> = emptyList(),
    val boardIcons: Map<Long, String> = emptyMap(),
    /** boardId -> absolute path of the cached cover, present only once the file is on the device. */
    val boardImages: Map<Long, String> = emptyMap(),
    /** boardIds that have a cover configured (in settings), even if not yet downloaded here. */
    val boardsWithCover: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val themeMode: String = "system",
    val cardViewMode: String = "list",
    val cardDensity: String = "comfortable",
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val message: String? = null,
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

private data class LocalState(
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val message: String? = null,
)

/** Bundles the four lower-churn flows so the main combine below stays within Kotlin's typed (non-vararg) overload. */
private data class Extra(
    val display: DisplayPreferences,
    val theme: String,
    val settings: TavlaSettings,
    val allCards: List<CardWithContext>,
    val coverPaths: Map<Long, String>,
)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val themeRepository: ThemeRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val settingsRepository: SettingsRepository,
    private val coverRepository: com.brbrs.tavla.settings.BoardCoverRepository,
    private val solidtime: SolidtimeRepository,
    private val solidtimeLinksSync: com.brbrs.tavla.solidtime.SolidtimeLinksSync,
    private val contactLinksSync: com.brbrs.tavla.contactlinks.ContactLinksSync,
) : ViewModel() {

    /** The running Solidtime timer, for the banner on the boards screen. */
    val runningTimer: StateFlow<RunningTimer?> = solidtime.runningTimer

    private val undoCtl = UndoController(viewModelScope)
    val pendingUndo: StateFlow<PendingUndo?> = undoCtl.pending

    private val dueWindowEnd = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
    private val local = MutableStateFlow(LocalState())

    private val extraFlow = combine(
        displayPrefs.preferences,
        themeRepository.themeMode,
        settingsRepository.settings,
        repository.observeAllOpenCards(),
        coverRepository.readyCovers,
    ) { display, theme, settings, allCards, coverPaths -> Extra(display, theme, settings, allCards, coverPaths) }

    val uiState: StateFlow<BoardsUiState> = combine(
        repository.observeBoards(),
        repository.observeCardCounts(dueWindowEnd),
        repository.observeDueCards(dueWindowEnd),
        extraFlow,
        local,
    ) { boards, counts, due, extra, l ->
        val query = l.searchQuery.trim()
        val searched = if (query.isBlank()) boards else boards.filter { it.title.contains(query, ignoreCase = true) }
        val visible = if (extra.display.showArchivedBoards) searched else searched.filterNot { it.archived }
        // #01: search looks at every open card's title AND description, not just the due-soon
        // strip, so Tavla doubles as a quick lookup tool. Capped so a very common word doesn't
        // render hundreds of rows.
        val searchResults = if (query.isBlank()) emptyList() else extra.allCards
            .filter { it.card.title.contains(query, ignoreCase = true) || it.card.description.contains(query, ignoreCase = true) }
            .take(40)
        BoardsUiState(
            boards = visible,
            archivedCount = boards.count { it.archived },
            counts = counts,
            dueCards = due,
            searchResults = searchResults,
            boardIcons = extra.settings.boardIcons,
            boardImages = extra.coverPaths,
            boardsWithCover = extra.settings.boardImages.keys,
            searchQuery = l.searchQuery,
            showArchived = extra.display.showArchivedBoards,
            themeMode = extra.theme,
            cardViewMode = extra.display.cardViewMode,
            cardDensity = extra.display.cardDensity,
            isRefreshing = l.isRefreshing,
            hasLoadedOnce = l.hasLoadedOnce || boards.isNotEmpty(),
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardsUiState())

    override fun onCleared() { undoCtl.flush() }

    init {
        refresh()
        viewModelScope.launch { runCatching { solidtime.refreshActiveTimer() } }
        settingsRepository.fetchFromRemote()
        solidtimeLinksSync.fetchFromRemote()
        contactLinksSync.fetchFromRemote()
    }

    fun refresh() {
        if (local.value.isRefreshing) return
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true) }
            val result = runCatching { repository.refreshAll() }
            local.update {
                it.copy(
                    isRefreshing = false,
                    hasLoadedOnce = true,
                    message = result.exceptionOrNull()?.let(::friendlyError) ?: it.message,
                )
            }
        }
    }

    fun onSearchChanged(q: String) = local.update { it.copy(searchQuery = q) }
    fun consumeMessage() = local.update { it.copy(message = null) }
    fun cycleTheme() { viewModelScope.launch { themeRepository.cycleThemeMode() } }
    fun setShowArchived(show: Boolean) { viewModelScope.launch { displayPrefs.setShowArchivedBoards(show) } }
    fun setBoardIcon(boardId: Long, iconName: String?) = settingsRepository.setBoardIcon(boardId, iconName)

    /** Set the picked image as the board's cover: cached locally, synced to Nextcloud (#02). */
    fun setBoardCover(boardId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = coverRepository.setCover(boardId, uri)
            if (!ok) local.update { it.copy(message = "Couldn't use that image.") }
        }
    }

    fun removeBoardCover(boardId: Long) = coverRepository.removeCover(boardId)

    private fun runAction(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            val r = runCatching { block() }
            local.update { it.copy(message = r.exceptionOrNull()?.let(::friendlyError) ?: success) }
        }
    }

    fun createBoard(title: String, color: String) = runAction("Board created") {
        repository.createBoard(title.trim(), color)
    }

    fun setArchived(board: Board, archived: Boolean) {
        if (archived) {
            undoCtl.schedule("Board archived") { repository.updateBoard(board, archived = true) }
        } else {
            runAction("Board restored") { repository.updateBoard(board, archived = false) }
        }
    }

    fun updateBoard(board: Board, title: String, color: String) = runAction {
        repository.updateBoard(board, title = title.trim(), color = color)
    }

    fun deleteBoard(board: Board) {
        undoCtl.schedule("Board deleted") { repository.deleteBoard(board.id) }
    }

    fun undoAction() = undoCtl.cancel()
}
