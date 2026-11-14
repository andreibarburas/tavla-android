package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.biometric.BiometricHelper
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.reminders.NotificationHelper
import com.brbrs.tavla.reminders.ReminderScheduler
import com.brbrs.tavla.settings.SettingsRepository
import com.brbrs.tavla.sync.WorkScheduler
import com.brbrs.tavla.ui.components.friendlyError
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import com.brbrs.tavla.ui.theme.ThemeRepository
import com.brbrs.tavla.util.LocalBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val isLocalMode: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val appLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = true,
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = true,
    val showArchivedBoards: Boolean = false,
    val showLabelsOnCards: Boolean = true,
    val cardViewMode: String = "list",
    val cardDensity: String = "comfortable",
    val syncIntervalMinutes: Int = 60,
    val remindersEnabled: Boolean = true,
    val reminderOffsetsMinutes: List<Int> = listOf(60, 1440),
    val notificationsPermitted: Boolean = true,
    val needsExactAlarmPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val loggedOut: Boolean = false,
    val message: String? = null,
)

private data class SettingsOps(
    val isSyncing: Boolean = false,
    val loggedOut: Boolean = false,
    val message: String? = null,
    val connectNextcloud: Boolean = false,
    val localBackupResult: File? = null,
    val isLocalExporting: Boolean = false,
    val isLocalImporting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val themeRepository: ThemeRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val reminderScheduler: ReminderScheduler,
    private val notificationHelper: NotificationHelper,
    private val localBackupRepository: LocalBackupRepository,
    biometricHelper: BiometricHelper,
) : ViewModel() {

    private val ops = MutableStateFlow(SettingsOps())
    private val biometricAvailable = biometricHelper.isAvailable()

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.session,
        authRepository.appLockEnabled,
        displayPrefs.preferences,
        combine(themeRepository.themeMode, settingsRepository.settings, authRepository.storageMode) { theme, settings, mode ->
            Triple(theme, settings, mode)
        },
        ops,
    ) { session, appLock, display, (theme, settings, mode), op ->
        SettingsUiState(
            isLocalMode = mode == "local",
            serverUrl = session?.serverUrl.orEmpty(),
            username = session?.username.orEmpty(),
            appLockEnabled = appLock,
            biometricAvailable = biometricAvailable,
            themeMode = theme,
            textSize = display.textSize,
            useCustomFont = display.useCustomFont,
            showArchivedBoards = display.showArchivedBoards,
            showLabelsOnCards = display.showLabelsOnCards,
            cardViewMode = display.cardViewMode,
            cardDensity = display.cardDensity,
            syncIntervalMinutes = settings.syncIntervalMinutes,
            remindersEnabled = settings.remindersEnabled,
            reminderOffsetsMinutes = settings.reminderOffsetsMinutes,
            notificationsPermitted = notificationHelper.canNotify(),
            needsExactAlarmPermission = reminderScheduler.needsExactAlarmPermission(),
            isSyncing = op.isSyncing,
            loggedOut = op.loggedOut,
            message = op.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun consumeMessage() = ops.update { it.copy(message = null) }

    fun setAppLock(enabled: Boolean) { viewModelScope.launch { authRepository.setAppLockEnabled(enabled) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { themeRepository.setThemeMode(mode) } }
    fun setTextSize(size: String) { viewModelScope.launch { displayPrefs.setTextSize(size) } }
    fun setUseCustomFont(enabled: Boolean) { viewModelScope.launch { displayPrefs.setUseCustomFont(enabled) } }
    fun setShowArchivedBoards(enabled: Boolean) { viewModelScope.launch { displayPrefs.setShowArchivedBoards(enabled) } }
    fun setShowLabelsOnCards(enabled: Boolean) { viewModelScope.launch { displayPrefs.setShowLabelsOnCards(enabled) } }
    fun setCardViewMode(mode: String) { viewModelScope.launch { displayPrefs.setCardViewMode(mode) } }
    fun setCardDensity(density: String) { viewModelScope.launch { displayPrefs.setCardDensity(density) } }

    /** #03: WorkManager enforces a 15-minute floor; this reschedules the periodic job immediately. */
    fun setSyncIntervalMinutes(minutes: Int) {
        settingsRepository.setSyncIntervalMinutes(minutes)
        workScheduler.scheduleSync(minutes)
    }

    fun setRemindersEnabled(enabled: Boolean) = settingsRepository.setRemindersEnabled(enabled)

    fun toggleReminderOffset(offsetMinutes: Int) {
        val current = uiState.value.reminderOffsetsMinutes
        val updated = if (offsetMinutes in current) current - offsetMinutes else (current + offsetMinutes).sorted()
        settingsRepository.setReminderOffsets(updated)
    }

    fun syncNow() {
        if (ops.value.isSyncing) return
        viewModelScope.launch {
            ops.update { it.copy(isSyncing = true) }
            val r = runCatching { deckRepository.refreshAll() }
            ops.update {
                it.copy(isSyncing = false, message = r.exceptionOrNull()?.let(::friendlyError) ?: "All boards are up to date")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            deckRepository.clearCache()
            ops.update { it.copy(message = "Offline copy cleared. Boards reload from Nextcloud.") }
        }
    }

    /** Signs out and wipes the offline copy -- nothing from this account stays on the device. */
    fun logout() {
        viewModelScope.launch {
            deckRepository.clearCache()
            authRepository.clearSession()
            workScheduler.cancelSync()
            ops.update { it.copy(loggedOut = true) }
        }
    }

    // ─── Local-mode backup ────────────────────────────────────────────────

    fun exportLocalBackup() {
        if (ops.value.isLocalExporting) return
        viewModelScope.launch {
            ops.update { it.copy(isLocalExporting = true) }
            localBackupRepository.export()
                .onSuccess { file -> ops.update { it.copy(isLocalExporting = false, localBackupResult = file) } }
                .onFailure { e -> ops.update { it.copy(isLocalExporting = false, message = "Export failed: ${e.message}") } }
        }
    }

    fun localBackupShareIntentFor(file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun consumeBackupResult() = ops.update { it.copy(localBackupResult = null) }

    fun importLocalBackup(uri: Uri) {
        if (ops.value.isLocalImporting) return
        viewModelScope.launch {
            ops.update { it.copy(isLocalImporting = true) }
            runCatching {
                val tmp = File(context.cacheDir, "tavla_import.zip")
                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalArgumentException("Cannot open file")
                localBackupRepository.import(tmp).getOrThrow()
            }
                .onSuccess { result -> ops.update { it.copy(isLocalImporting = false, message = "Imported ${result.imported} items (${result.skipped} skipped)") } }
                .onFailure { e -> ops.update { it.copy(isLocalImporting = false, message = "Import failed: ${e.message}") } }
        }
    }

    // ─── Connect to Nextcloud (from local mode) ──────────────────────────

    fun connectToNextcloud() {
        viewModelScope.launch {
            authRepository.setStorageMode("nextcloud")
            ops.update { it.copy(connectNextcloud = true) }
        }
    }

    val connectNextcloud: StateFlow<Boolean> = ops
        .map { it.connectNextcloud }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val localBackupResult: StateFlow<File?> = ops
        .map { it.localBackupResult }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun consumeConnectNextcloud() = ops.update { it.copy(connectNextcloud = false) }
}
