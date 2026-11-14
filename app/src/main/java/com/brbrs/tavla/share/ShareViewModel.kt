package com.brbrs.tavla.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.data.Stack
import com.brbrs.tavla.ui.components.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val signedIn: Boolean = true,
    val boards: List<Board> = emptyList(),
    val selectedBoard: Board? = null,
    val stacks: List<Stack> = emptyList(),
    val selectedStack: Stack? = null,
    val title: String = "",
    val description: String = "",
    val boardIcons: Map<Long, String> = emptyMap(),
    val boardImages: Map<Long, String> = emptyMap(),
    val isLoadingStacks: Boolean = false,
    val isSaving: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
)

/**
 * #05: sharing text or a link from any other app opens this minimal flow instead of the full
 * Tavla UI -- pick a board and list, confirm the title, done. The shared text becomes the
 * description so a link isn't lost, and its first line (or domain, for a bare URL) becomes a
 * sensible starting title.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val repository: DeckRepository,
    private val settingsRepository: com.brbrs.tavla.settings.SettingsRepository,
    private val coverRepository: com.brbrs.tavla.settings.BoardCoverRepository,
) : ViewModel() {

    private val local = MutableStateFlow(ShareUiState())

    val uiState: StateFlow<ShareUiState> = combine(
        repository.observeBoards(),
        settingsRepository.settings,
        coverRepository.readyCovers,
        local,
    ) { boards, settings, coverPaths, l ->
        l.copy(
            boards = boards.filterNot { it.archived },
            boardIcons = settings.boardIcons,
            boardImages = coverPaths,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState())

    init {
        viewModelScope.launch {
            val signedIn = authRepository.currentSession() != null
            local.update { it.copy(signedIn = signedIn) }
            if (signedIn) runCatching { repository.refreshAll() }
        }
    }

    fun setSharedText(rawText: String) {
        val firstLine = rawText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val title = when {
            firstLine.isBlank() -> "Shared from another app"
            Regex("^https?://").containsMatchIn(firstLine) -> runCatching { java.net.URI(firstLine.trim()).host }.getOrNull() ?: firstLine.take(80)
            else -> firstLine.take(80)
        }
        local.update { it.copy(title = title, description = rawText) }
    }

    fun onTitleChanged(value: String) = local.update { it.copy(title = value) }

    fun selectBoard(board: Board) {
        local.update { it.copy(selectedBoard = board, stacks = emptyList(), selectedStack = null, isLoadingStacks = true) }
        viewModelScope.launch {
            val r = runCatching { repository.refreshBoard(board.id) }
            // observeStacks is a Flow from Room; a one-shot read is enough for this picker.
            val stacks = repository.observeStacks(board.id).first()
            local.update {
                it.copy(
                    isLoadingStacks = false,
                    stacks = stacks,
                    selectedStack = stacks.firstOrNull(),
                    message = r.exceptionOrNull()?.let(::friendlyError),
                )
            }
        }
    }

    fun selectStack(stack: Stack) = local.update { it.copy(selectedStack = stack) }
    fun consumeMessage() = local.update { it.copy(message = null) }

    fun createCard() {
        val state = local.value
        val stack = state.selectedStack ?: return
        val board = state.selectedBoard ?: return
        if (state.title.isBlank()) return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true) }
            val r = runCatching {
                val cardId = repository.createCard(board.id, stack.id, state.title.trim(), 0)
                if (state.description.isNotBlank() && state.description.trim() != state.title.trim()) {
                    val card = repository.observeCard(cardId)
                    val c = card.first()
                    if (c != null) repository.updateCard(c, description = state.description.trim())
                }
            }
            local.update { it.copy(isSaving = false, done = r.isSuccess, message = r.exceptionOrNull()?.let(::friendlyError)) }
        }
    }
}
