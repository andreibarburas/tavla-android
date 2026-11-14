package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.auth.PollEndpoint
import com.brbrs.tavla.auth.TavlaSession
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.network.DeckApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val loginUrl: String? = null,
    val checkingDeck: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: com.brbrs.tavla.settings.SettingsRepository,
    private val solidtimeLinksSync: com.brbrs.tavla.solidtime.SolidtimeLinksSync,
    private val workScheduler: com.brbrs.tavla.sync.WorkScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var pollJob: Job? = null

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    /** Keeps an explicit http:// (private networks), defaults bare addresses to https://. */
    private fun normalizeServer(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://", ignoreCase = true)  -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun startLogin() {
        val serverUrl = normalizeServer(uiState.value.serverUrl)
        pollJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, loginUrl = null) }

            authRepository.initiateLoginFlow(serverUrl)
                .onSuccess { flow ->
                    // Login Flow v2 poll URL rewrite: the server may report its internal host
                    // (common behind Tailscale or a reverse proxy on a custom subdomain). Keep the
                    // path, but always poll the address the user actually typed. Never remove this.
                    val original = flow.poll.endpoint
                    val path = try {
                        val uri = java.net.URI(original)
                        uri.rawPath + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                    } catch (e: Exception) {
                        original.replaceFirst(Regex("^https?://[^/]+"), "")
                    }
                    val rewrittenPoll = flow.poll.copy(endpoint = serverUrl.trimEnd('/') + path)
                    _uiState.update { it.copy(loginUrl = flow.login, isLoading = false) }
                    pollForCredentials(serverUrl, rewrittenPoll)
                }
                .onFailure { e ->
                    val msg = when {
                        e.message?.contains("CLEARTEXT") == true              -> "Server requires HTTPS"
                        e.message?.contains("Unable to resolve host") == true -> "Cannot find server -- check the URL"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "Connection timed out"
                        e.message?.contains("CERTIFICATE") == true ||
                            e.message?.contains("trust") == true              -> "SSL certificate error"
                        e.message.isNullOrBlank()                             -> "Cannot reach server (${e.javaClass.simpleName})"
                        else                                                  -> "Cannot reach server: ${e.message}"
                    }
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    private fun pollForCredentials(serverUrl: String, poll: PollEndpoint) {
        pollJob = viewModelScope.launch {
            // Network errors while polling are expected, not fatal: the user is in the browser,
            // so Tavla is in the background, and Android often blocks background network access
            // (battery saver, data saver, restricted background data). Lookups then fail with
            // "Unable to resolve host" until Tavla is back in the foreground. Keep polling and only
            // report the last network error if the whole window runs out.
            var lastNetworkError: String? = null
            repeat(90) {
                delay(2_000)
                val result = authRepository.pollLoginFlow(poll)
                result.onSuccess { creds ->
                    val session = TavlaSession(
                        serverUrl   = serverUrl.trimEnd('/'),
                        username    = creds.loginName,
                        appPassword = creds.appPassword,
                    )
                    _uiState.update { it.copy(checkingDeck = true, loginUrl = null) }
                    val probe = deckRepository.probeDeck(session)
                    val failure = probe.exceptionOrNull()
                    if (failure is DeckApiException && (failure.code == 404 || failure.code == 503)) {
                        _uiState.update {
                            it.copy(
                                checkingDeck = false,
                                error = "Signed in, but the Deck app isn't enabled on this Nextcloud. " +
                                    "Ask your admin to enable Deck, then try again.",
                            )
                        }
                        return@launch
                    }
                    // Any other probe failure (e.g. flaky network) is not a reason to block sign-in;
                    // the boards screen will show it and retry.
                    authRepository.saveSession(session)
                    // #03/#13: start background sync and pull in any settings from another device
                    // right away, rather than waiting for the next app launch.
                    settingsRepository.fetchFromRemote()
                    solidtimeLinksSync.fetchFromRemote()
                    workScheduler.scheduleSync(settingsRepository.settings.value.syncIntervalMinutes)
                    _uiState.update { it.copy(checkingDeck = false, loginSuccess = true) }
                    return@launch
                }
                result.onFailure { e ->
                    when {
                        e.message?.startsWith("POLL_NOT_READY") == true -> Unit
                        e is java.io.IOException -> lastNetworkError = e.message ?: e.javaClass.simpleName
                        else -> {
                            _uiState.update { it.copy(isLoading = false, loginUrl = null, error = "Login failed: ${e.message}") }
                            return@launch
                        }
                    }
                }
            }
            val reason = lastNetworkError?.let { " Last error: $it" }.orEmpty()
            _uiState.update { it.copy(error = "Login timed out. Please try again.$reason", loginUrl = null, isLoading = false) }
        }
    }
}
