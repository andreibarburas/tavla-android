package com.brbrs.tavla.solidtime

import android.util.Log
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.settings.DavConflictException
import com.brbrs.tavla.settings.WebDavClient
import com.brbrs.tavla.solidtime.data.BoardLinkEntity
import com.brbrs.tavla.solidtime.data.CardEntryEntity
import com.brbrs.tavla.solidtime.data.CardProjectEntity
import com.brbrs.tavla.solidtime.data.CardTaskEntity
import com.brbrs.tavla.solidtime.data.SolidtimeDao
import com.brbrs.tavla.sync.WorkScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the three Solidtime link tables (board_link, card_entry, card_task) and the per-card
 * project overrides to a Nextcloud sidecar file at `Tavla/solidtime-links.json`, so they follow
 * the user to a new device -- and survive a reinstall.
 *
 * The local Room database remains the working copy -- this class mirrors changes to Nextcloud
 * and populates Room from the remote file on first sync. Both directions are routed through
 * [WorkScheduler] (durable WorkManager jobs with retry/backoff) rather than a bare coroutine:
 * a push that was debounced in a bare `appScope.launch` would be lost for good if the process
 * died (e.g. right before an uninstall) before its delay elapsed -- the same class of bug fixed
 * for board covers in [com.brbrs.tavla.sync.CoverUploadWorker]. A reinstall then has nothing to
 * pull, which is exactly what was reported: card-project overrides and tracked entries missing
 * after reinstalling.
 *
 * Pending ops are intentionally NOT synced -- they reference local timer state and are replayed
 * on the device that created them.
 */
@Singleton
class SolidtimeLinksSync @Inject constructor(
    private val dao: SolidtimeDao,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    private val workScheduler: WorkScheduler,
) {
    private val syncMutex = Mutex()
    private var lastEtag: String? = null

    /**
     * Pulls the remote links file and merges it into the local Room database. Call once after
     * sign-in or when the boards screen opens -- same places that call
     * [com.brbrs.tavla.settings.SettingsRepository.fetchFromRemote]. Durable: enqueues a
     * WorkManager job that retries with backoff until it succeeds, instead of a single
     * best-effort attempt -- a fresh install depends on this one pull actually landing.
     */
    fun fetchFromRemote() {
        workScheduler.scheduleSolidtimeLinksPull()
    }

    /**
     * Call after any local write to board_link, card_entry, card_task, or a card's project
     * override. Durable and debounced: enqueues a WorkManager job a couple seconds out so rapid
     * writes collapse into one network call, but -- unlike a cancellable coroutine delay -- the
     * job survives process death and retries with backoff if it can't reach the server.
     */
    fun notifyChanged() {
        workScheduler.scheduleSolidtimeLinksPush()
    }

    /** Clears the cached etag so the next fetch starts fresh (call on disconnect). */
    fun reset() { lastEtag = null }

    /** Durable push, called by [com.brbrs.tavla.solidtime.SolidtimeLinksPushWorker]. True on success. */
    suspend fun pushDurable(): Boolean = syncMutex.withLock {
        runCatching { pushNow() }
            .onFailure { Log.w(TAG, "push failed: ${it.message}") }
            .isSuccess
    }

    /** Durable pull, called by [com.brbrs.tavla.solidtime.SolidtimeLinksPullWorker]. True on success. */
    suspend fun pullDurable(): Boolean = syncMutex.withLock {
        runCatching {
            val session = authRepository.currentSession() ?: return@runCatching
            davClient.ensureFolder(session, REMOTE_FOLDER)
            val remote = davClient.getFile(session, REMOTE_FILE)
            if (remote == null) {
                // First run on this account: seed the remote file from whatever's local.
                pushNow()
            } else {
                lastEtag = remote.etag
                val data = SolidtimeLinksData.fromJson(JSONObject(remote.content))
                mergeRemoteIntoLocal(data)
            }
        }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            .isSuccess
    }

    // -- internals -------------------------------------------------------------------------------

    private suspend fun pushNow() {
        val session = authRepository.currentSession() ?: return
        davClient.ensureFolder(session, REMOTE_FOLDER)
        val data = readLocalData()
        val json = data.toJson().toString()
        try {
            lastEtag = davClient.putFile(session, REMOTE_FILE, json, lastEtag)
        } catch (_: DavConflictException) {
            // Another device changed the file. Re-fetch, merge, push again.
            val remote = davClient.getFile(session, REMOTE_FILE) ?: return
            lastEtag = remote.etag
            val remoteData = SolidtimeLinksData.fromJson(JSONObject(remote.content))
            mergeRemoteIntoLocal(remoteData)
            val merged = readLocalData()
            lastEtag = davClient.putFile(session, REMOTE_FILE, merged.toJson().toString(), remote.etag)
        }
    }

    private suspend fun mergeRemoteIntoLocal(remote: SolidtimeLinksData) {
        // Board links: remote wins for boards not locally present, local wins on conflicts.
        val localBoardIds = dao.getAllBoardLinks().associateBy { it.boardId }
        for (link in remote.boardLinks) {
            if (link.boardId !in localBoardIds) dao.upsertBoardLink(link)
        }

        // Card entries: union by entryId. These are append-only in practice.
        for (entry in remote.cardEntries) {
            if (dao.getCardEntry(entry.entryId) == null) {
                dao.insertCardEntry(entry)
            }
        }

        // Card tasks: remote wins for cards not locally present, local wins on conflicts.
        for (task in remote.cardTasks) {
            if (dao.taskForCard(task.cardId) == null) {
                dao.upsertCardTask(task)
            }
        }

        // Card project overrides: remote wins for cards not locally present.
        for (cp in remote.cardProjects) {
            if (dao.getCardProject(cp.cardId) == null) {
                dao.upsertCardProject(cp)
            }
        }
    }

    private suspend fun readLocalData(): SolidtimeLinksData {
        val boardLinks = dao.getAllBoardLinks()
        val cardEntries = dao.getAllCardEntries()
        val cardTasks = dao.getAllCardTasks()
        val cardProjects = dao.getAllCardProjects()
        return SolidtimeLinksData(boardLinks, cardEntries, cardTasks, cardProjects)
    }

    companion object {
        private const val TAG = "SolidtimeLinksSync"
        private const val REMOTE_FOLDER = "Tavla"
        private const val REMOTE_FILE = "Tavla/solidtime-links.json"
    }
}

// -- Data container + JSON serialization ---------------------------------------------------------

data class SolidtimeLinksData(
    val boardLinks: List<BoardLinkEntity>,
    val cardEntries: List<CardEntryEntity>,
    val cardTasks: List<CardTaskEntity>,
    val cardProjects: List<CardProjectEntity> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("boardLinks", JSONArray().apply {
            boardLinks.forEach { put(it.toJson()) }
        })
        put("cardEntries", JSONArray().apply {
            cardEntries.forEach { put(it.toJson()) }
        })
        put("cardTasks", JSONArray().apply {
            cardTasks.forEach { put(it.toJson()) }
        })
        put("cardProjects", JSONArray().apply {
            cardProjects.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): SolidtimeLinksData {
            val boardLinks = json.optJSONArray("boardLinks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { boardLinkFromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
            val cardEntries = json.optJSONArray("cardEntries")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { cardEntryFromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
            val cardTasks = json.optJSONArray("cardTasks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { cardTaskFromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
            val cardProjects = json.optJSONArray("cardProjects")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { cardProjectFromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
            return SolidtimeLinksData(boardLinks, cardEntries, cardTasks, cardProjects)
        }
    }
}

// -- Entity <-> JSON helpers ---------------------------------------------------------------------

private fun BoardLinkEntity.toJson(): JSONObject = JSONObject().apply {
    put("boardId", boardId)
    put("projectId", projectId)
    put("projectName", projectName)
    put("projectColor", projectColor)
    put("clientName", clientName ?: JSONObject.NULL)
    put("projectBillable", projectBillable)
    put("createTasks", createTasks)
    put("useListTag", useListTag)
    put("doneStackId", doneStackId ?: JSONObject.NULL)
}

private fun boardLinkFromJson(o: JSONObject) = BoardLinkEntity(
    boardId = o.getLong("boardId"),
    projectId = o.getString("projectId"),
    projectName = o.getString("projectName"),
    projectColor = o.optString("projectColor", "#000000"),
    clientName = if (o.isNull("clientName")) null else o.optString("clientName"),
    projectBillable = o.optBoolean("projectBillable"),
    createTasks = o.optBoolean("createTasks"),
    useListTag = o.optBoolean("useListTag"),
    doneStackId = if (o.isNull("doneStackId")) null else o.optLong("doneStackId"),
)

private fun CardEntryEntity.toJson(): JSONObject = JSONObject().apply {
    put("entryId", entryId)
    put("cardId", cardId)
    put("boardId", boardId)
}

private fun cardEntryFromJson(o: JSONObject) = CardEntryEntity(
    entryId = o.getString("entryId"),
    cardId = o.getLong("cardId"),
    boardId = o.getLong("boardId"),
)

private fun CardTaskEntity.toJson(): JSONObject = JSONObject().apply {
    put("cardId", cardId)
    put("taskId", taskId)
}

private fun cardTaskFromJson(o: JSONObject) = CardTaskEntity(
    cardId = o.getLong("cardId"),
    taskId = o.getString("taskId"),
)

private fun CardProjectEntity.toJson(): JSONObject = JSONObject().apply {
    put("cardId", cardId)
    put("projectId", projectId)
    put("projectName", projectName)
    put("projectColor", projectColor)
    put("clientName", clientName ?: JSONObject.NULL)
    put("projectBillable", projectBillable)
}

private fun cardProjectFromJson(o: JSONObject) = CardProjectEntity(
    cardId = o.getLong("cardId"),
    projectId = o.getString("projectId"),
    projectName = o.getString("projectName"),
    projectColor = o.optString("projectColor", "#000000"),
    clientName = if (o.isNull("clientName")) null else o.optString("clientName"),
    projectBillable = o.optBoolean("projectBillable"),
)
