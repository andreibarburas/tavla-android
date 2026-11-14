package com.brbrs.tavla.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tavla_nc_settings")
private const val TAG = "TavlaSettings"
private const val REMOTE_FOLDER = "Tavla"
private const val REMOTE_FILE = "Tavla/tavla-settings.json"

/**
 * The single source of truth for everything in [TavlaSettings]. Backed by a local DataStore cache
 * (so reads are instant and work offline) that's kept in sync with one small JSON file in the
 * user's own Nextcloud (see [WebDavClient]), so settings, board icons and card links follow the
 * person to a new device. Writes are debounced so flipping a few switches in a row is one network
 * call, not five.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val JSON_KEY = stringPreferencesKey("settings_json")
    private val ETAG_KEY = stringPreferencesKey("settings_etag")
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null
    private var folderEnsured = false

    private val localFlow: Flow<TavlaSettings> = context.ncSettingsDataStore.data.map { prefs ->
        prefs[JSON_KEY]?.let { runCatching { TavlaSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: TavlaSettings()
    }

    val settings: StateFlow<TavlaSettings> =
        localFlow.stateIn(appScope, SharingStarted.Eagerly, TavlaSettings())

    /** Call once after sign-in (and it's safe to call again later) to pull in settings from another device. */
    fun fetchFromRemote() {
        appScope.launch {
            if (authRepository.isLocalMode()) return@launch
            val session = authRepository.currentSession() ?: return@launch
            syncMutex.withLock {
                runCatching {
                    if (!folderEnsured) { davClient.ensureFolder(session, REMOTE_FOLDER); folderEnsured = true }
                    val remote = davClient.getFile(session, REMOTE_FILE)
                    if (remote == null) {
                        // First run on this account: seed the remote file with whatever's local (likely defaults).
                        pushNow(session)
                    } else if (remote.etag != context.ncSettingsDataStore.data.first()[ETAG_KEY]) {
                        // Merge: keep local scalar settings (so a just-changed toggle isn't
                        // overwritten by a remote file that doesn't contain the new field yet),
                        // but union map fields from remote.
                        val remoteSettings = runCatching { TavlaSettings.fromJson(JSONObject(remote.content)) }.getOrNull() ?: TavlaSettings()
                        val local = localFlow.first()
                        val merged = remoteSettings.copy(
                            themeMode = local.themeMode,
                            textSize = local.textSize,
                            useCustomFont = local.useCustomFont,
                            showArchivedBoards = local.showArchivedBoards,
                            showLabelsOnCards = local.showLabelsOnCards,
                            syncIntervalMinutes = local.syncIntervalMinutes,
                            remindersEnabled = local.remindersEnabled,
                            reminderOffsetsMinutes = local.reminderOffsetsMinutes,
                            cardViewMode = local.cardViewMode,
                            cardDensity = local.cardDensity,
                            boardIcons = remoteSettings.boardIcons + local.boardIcons,
                            boardImages = remoteSettings.boardImages + local.boardImages,
                            doneStacks = remoteSettings.doneStacks + local.doneStacks,
                            cardLinks = remoteSettings.cardLinks + local.cardLinks,
                        )
                        context.ncSettingsDataStore.edit { it[JSON_KEY] = merged.toJson().toString(); it[ETAG_KEY] = remote.etag.orEmpty() }
                    }
                }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            }
        }
    }

    /** Applies [mutator] to the current settings, saves locally immediately, then syncs to Nextcloud shortly after. */
    fun update(mutator: (TavlaSettings) -> TavlaSettings) {
        appScope.launch {
            val current = localFlow.first()
            val updated = mutator(current)
            context.ncSettingsDataStore.edit { it[JSON_KEY] = updated.toJson().toString() }
            // Skip WebDAV push in local mode
            if (authRepository.isLocalMode()) return@launch
            debounceJob?.cancel()
            debounceJob = appScope.launch {
                delay(1_500)
                val session = authRepository.currentSession() ?: return@launch
                syncMutex.withLock { runCatching { pushNow(session) }.onFailure { Log.w(TAG, "sync failed: ${it.message}") } }
            }
        }
    }

    private suspend fun pushNow(session: com.brbrs.tavla.auth.TavlaSession) {
        if (!folderEnsured) { davClient.ensureFolder(session, REMOTE_FOLDER); folderEnsured = true }
        val prefs = context.ncSettingsDataStore.data.first()
        val content = prefs[JSON_KEY] ?: TavlaSettings().toJson().toString()
        val etag = prefs[ETAG_KEY]
        try {
            val newEtag = davClient.putFile(session, REMOTE_FILE, content, etag.takeIf { !it.isNullOrBlank() })
            context.ncSettingsDataStore.edit { it[ETAG_KEY] = newEtag.orEmpty() }
        } catch (e: DavConflictException) {
            // Another device saved first. Merge map fields (union, ours wins on key clashes) and
            // keep our scalar settings -- conflicts here are rare and low-stakes for one person.
            val remote = davClient.getFile(session, REMOTE_FILE) ?: return
            val remoteSettings = runCatching { TavlaSettings.fromJson(JSONObject(remote.content)) }.getOrNull() ?: return
            val ours = runCatching { TavlaSettings.fromJson(JSONObject(content)) }.getOrNull() ?: return
            val merged = remoteSettings.copy(
                themeMode = ours.themeMode,
                textSize = ours.textSize,
                useCustomFont = ours.useCustomFont,
                showArchivedBoards = ours.showArchivedBoards,
                showLabelsOnCards = ours.showLabelsOnCards,
                syncIntervalMinutes = ours.syncIntervalMinutes,
                remindersEnabled = ours.remindersEnabled,
                reminderOffsetsMinutes = ours.reminderOffsetsMinutes,
                cardViewMode = ours.cardViewMode,
                cardDensity = ours.cardDensity,
                boardIcons = remoteSettings.boardIcons + ours.boardIcons,
                boardImages = remoteSettings.boardImages + ours.boardImages,
                doneStacks = remoteSettings.doneStacks + ours.doneStacks,
                cardLinks = remoteSettings.cardLinks + ours.cardLinks,
            )
            val mergedJson = merged.toJson().toString()
            context.ncSettingsDataStore.edit { it[JSON_KEY] = mergedJson }
            val newEtag = davClient.putFile(session, REMOTE_FILE, mergedJson, remote.etag)
            context.ncSettingsDataStore.edit { it[ETAG_KEY] = newEtag.orEmpty() }
        }
    }

    // -- Convenience setters used across the app --

    fun setThemeMode(mode: String) = update { it.copy(themeMode = mode) }
    fun setTextSize(size: String) = update { it.copy(textSize = size) }
    fun setUseCustomFont(enabled: Boolean) = update { it.copy(useCustomFont = enabled) }
    fun setShowArchivedBoards(enabled: Boolean) = update { it.copy(showArchivedBoards = enabled) }
    fun setShowLabelsOnCards(enabled: Boolean) = update { it.copy(showLabelsOnCards = enabled) }
    fun setSyncIntervalMinutes(minutes: Int) = update { it.copy(syncIntervalMinutes = minutes) }
    fun setRemindersEnabled(enabled: Boolean) = update { it.copy(remindersEnabled = enabled) }
    fun setReminderOffsets(offsets: List<Int>) = update { it.copy(reminderOffsetsMinutes = offsets) }

    fun setBoardIcon(boardId: Long, iconName: String?) = update {
        it.copy(boardIcons = if (iconName == null) it.boardIcons - boardId else it.boardIcons + (boardId to iconName))
    }

    fun setBoardImage(boardId: Long, fileName: String?) = update {
        it.copy(boardImages = if (fileName == null) it.boardImages - boardId else it.boardImages + (boardId to fileName))
    }

    fun setDoneStack(boardId: Long, stackId: Long?) = update {
        it.copy(doneStacks = if (stackId == null) it.doneStacks - boardId else it.doneStacks + (boardId to stackId))
    }

    fun setCardViewMode(mode: String) = update { it.copy(cardViewMode = mode) }
    fun setCardDensity(density: String) = update { it.copy(cardDensity = density) }

    fun addCardLink(cardId: Long, link: CrossAppLink) = update {
        it.copy(cardLinks = it.cardLinks + (cardId to (it.cardLinks[cardId].orEmpty() + link)))
    }

    fun removeCardLink(cardId: Long, linkId: String) = update {
        val remaining = it.cardLinks[cardId].orEmpty().filterNot { l -> l.id == linkId }
        it.copy(cardLinks = if (remaining.isEmpty()) it.cardLinks - cardId else it.cardLinks + (cardId to remaining))
    }
}
