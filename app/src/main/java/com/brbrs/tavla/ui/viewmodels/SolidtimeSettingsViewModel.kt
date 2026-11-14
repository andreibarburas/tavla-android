package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.solidtime.STMembership
import com.brbrs.tavla.solidtime.SolidtimeRepository
import com.brbrs.tavla.solidtime.friendlyStError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SolidtimeSettingsUiState(
    val connected: Boolean = false,
    val serverUrl: String = "",
    val organizationName: String? = null,
    val tokenEncrypted: Boolean = true,
    val pendingCount: Int = 0,
    val urlInput: String = "",
    val tokenInput: String = "",
    val connecting: Boolean = false,
    val testing: Boolean = false,
    /** When non-empty and not yet connected, the user is choosing which organization to use. */
    val memberships: List<STMembership> = emptyList(),
    /** True while the "rotate an expired token" form is open (shown instead of Disconnect). */
    val showUpdateToken: Boolean = false,
    val message: String? = null,
)

private data class LocalConnect(
    val urlInput: String = "",
    val tokenInput: String = "",
    val connecting: Boolean = false,
    val testing: Boolean = false,
    val memberships: List<STMembership> = emptyList(),
    val showUpdateToken: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SolidtimeSettingsViewModel @Inject constructor(
    private val repository: SolidtimeRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalConnect())

    val uiState: StateFlow<SolidtimeSettingsUiState> = combine(
        repository.connection,
        repository.pendingOpCount,
        local,
    ) { conn, pending, l ->
        SolidtimeSettingsUiState(
            connected = conn.connected,
            serverUrl = conn.serverUrl.orEmpty(),
            organizationName = conn.organizationName,
            tokenEncrypted = conn.tokenEncrypted,
            pendingCount = pending,
            urlInput = l.urlInput,
            tokenInput = l.tokenInput,
            connecting = l.connecting,
            testing = l.testing,
            memberships = l.memberships,
            showUpdateToken = l.showUpdateToken,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SolidtimeSettingsUiState())

    fun onUrlChange(v: String) = local.update { it.copy(urlInput = v, message = null) }
    fun onTokenChange(v: String) = local.update { it.copy(tokenInput = v, message = null) }
    fun consumeMessage() = local.update { it.copy(message = null) }

    fun connect() {
        val url = local.value.urlInput.trim()
        val token = local.value.tokenInput.trim()
        if (url.isBlank() || token.isBlank()) { local.update { it.copy(message = "Enter the server URL and a token.") }; return }
        viewModelScope.launch {
            local.update { it.copy(connecting = true, message = null) }
            repository.connect(url, token)
                .onSuccess { memberships ->
                    if (memberships.size == 1) {
                        repository.selectOrganization(memberships.first())
                        local.update { it.copy(connecting = false, memberships = emptyList(), tokenInput = "") }
                    } else {
                        local.update { it.copy(connecting = false, memberships = memberships) }
                    }
                }
                .onFailure { e -> local.update { it.copy(connecting = false, message = friendlyStError(e)) } }
        }
    }

    fun selectOrganization(m: STMembership) {
        repository.selectOrganization(m)
        local.update { it.copy(memberships = emptyList(), tokenInput = "", urlInput = "") }
    }

    fun testConnection() {
        viewModelScope.launch {
            local.update { it.copy(testing = true, message = null) }
            val r = repository.testConnection()
            local.update {
                it.copy(testing = false, message = if (r.isSuccess) "Connection OK." else friendlyStError(r.exceptionOrNull()!!))
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
        local.update { LocalConnect() }
    }

    // ─── Rotate an expired token, keeping every board link and tracked time entry ────────────

    fun startUpdateToken() {
        local.update {
            it.copy(showUpdateToken = true, urlInput = repository.connection.value.serverUrl.orEmpty(), tokenInput = "", message = null)
        }
    }

    fun cancelUpdateToken() {
        local.update { it.copy(showUpdateToken = false, urlInput = "", tokenInput = "", message = null) }
    }

    fun updateToken() {
        val url = local.value.urlInput.trim()
        val token = local.value.tokenInput.trim()
        if (url.isBlank() || token.isBlank()) { local.update { it.copy(message = "Enter the server URL and the new token.") }; return }
        viewModelScope.launch {
            local.update { it.copy(connecting = true, message = null) }
            repository.updateToken(url, token)
                .onSuccess {
                    local.update {
                        it.copy(connecting = false, showUpdateToken = false, urlInput = "", tokenInput = "", message = "Token updated. Everything else stayed as it was.")
                    }
                }
                .onFailure { e -> local.update { it.copy(connecting = false, message = friendlyStError(e)) } }
        }
    }
}
