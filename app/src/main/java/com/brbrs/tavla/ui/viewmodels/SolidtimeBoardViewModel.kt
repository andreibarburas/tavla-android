package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.data.Stack
import com.brbrs.tavla.solidtime.STProject
import com.brbrs.tavla.solidtime.SolidtimeRepository
import com.brbrs.tavla.solidtime.data.BoardLinkEntity
import com.brbrs.tavla.solidtime.friendlyStError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SolidtimeBoardUiState(
    val connected: Boolean = false,
    val boardTitle: String = "",
    val link: BoardLinkEntity? = null,
    val stacks: List<Stack> = emptyList(),
    val projects: List<STProject> = emptyList(),
    val clientNames: Map<String, String> = emptyMap(),
    val loadingProjects: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
) {
    val isLinked: Boolean get() = link != null
}

private data class STBoardLocal(
    val projects: List<STProject> = emptyList(),
    val clientNames: Map<String, String> = emptyMap(),
    val loadingProjects: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SolidtimeBoardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val solidtime: SolidtimeRepository,
) : ViewModel() {

    private val boardId: Long = checkNotNull(savedStateHandle.get<Long>("boardId"))
    private val local = MutableStateFlow(STBoardLocal())

    val uiState: StateFlow<SolidtimeBoardUiState> = combine(
        deckRepository.observeBoard(boardId).map { it?.title.orEmpty() },
        deckRepository.observeStacks(boardId),
        solidtime.observeBoardLink(boardId),
        solidtime.connection.map { it.connected },
        local,
    ) { title, stacks, link, connected, l ->
        SolidtimeBoardUiState(
            connected = connected,
            boardTitle = title,
            link = link,
            stacks = stacks,
            projects = l.projects,
            clientNames = l.clientNames,
            loadingProjects = l.loadingProjects,
            working = l.working,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SolidtimeBoardUiState())

    init { loadProjects() }

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun loadProjects() {
        if (!solidtime.isConnected) return
        viewModelScope.launch {
            local.update { it.copy(loadingProjects = true) }
            val projects = solidtime.listProjects()
            val clients = solidtime.listClients().getOrDefault(emptyMap())
            local.update {
                it.copy(
                    loadingProjects = false,
                    projects = projects.getOrDefault(emptyList()).sortedBy { p -> p.name.lowercase() },
                    clientNames = clients,
                    message = projects.exceptionOrNull()?.let(::friendlyStError),
                )
            }
        }
    }

    private fun work(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(working = true) }
            val r = runCatching { block() }
            local.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError) ?: success) }
        }
    }

    fun linkTo(project: STProject) = work("Linked to ${project.name}") {
        solidtime.linkBoard(boardId, project, project.clientId?.let { local.value.clientNames[it] })
    }

    fun createAndLink(name: String, color: String, billable: Boolean, clientId: String?) = work {
        val project = solidtime.createProject(name, color, billable, clientId).getOrThrow()
        solidtime.linkBoard(boardId, project, clientId?.let { local.value.clientNames[it] })
        loadProjects()
    }

    fun unlink() = work("Unlinked") { solidtime.unlinkBoard(boardId) }

    fun setCreateTasks(enabled: Boolean) = work {
        val l = uiState.value.link ?: return@work
        solidtime.setBoardOptions(boardId, enabled, l.useListTag, l.doneStackId)
    }

    fun setUseListTag(enabled: Boolean) = work {
        val l = uiState.value.link ?: return@work
        solidtime.setBoardOptions(boardId, l.createTasks, enabled, l.doneStackId)
    }

    fun setDoneStack(stackId: Long?) = work {
        val l = uiState.value.link ?: return@work
        solidtime.setBoardOptions(boardId, l.createTasks, l.useListTag, stackId)
    }
}
