package com.brbrs.tavla.solidtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brbrs.tavla.di.ApplicationScope
import com.brbrs.tavla.solidtime.data.BoardLinkEntity
import com.brbrs.tavla.solidtime.data.CardEntryEntity
import com.brbrs.tavla.solidtime.data.CardProjectEntity
import com.brbrs.tavla.solidtime.data.CardTaskEntity
import com.brbrs.tavla.solidtime.data.PendingOpEntity
import com.brbrs.tavla.solidtime.data.SolidtimeDao
import com.brbrs.tavla.solidtime.timer.RunningTimer
import com.brbrs.tavla.solidtime.timer.TimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Non-secret connection snapshot for the UI. */
data class SolidtimeConnection(
    val connected: Boolean = false,
    val serverUrl: String? = null,
    val organizationName: String? = null,
    val tokenEncrypted: Boolean = true,
)

/** Raised by [SolidtimeRepository.startTimer] when a timer is already running. */
class TimerBusyException : Exception("A timer is already running")

/**
 * The single orchestrator for the Solidtime integration: owns the credentials (via
 * [SecureCredentialStore]), the API client, the persistent link database, and the running-timer
 * state. Everything the ViewModels need goes through here.
 *
 * Design notes:
 *  - The running timer is mirrored to a small prefs file so it survives process death; on a cold
 *    start the repository restores it and the foreground service can keep showing it.
 *  - Start/stop that fail because the device is offline are queued (pending_op) and replayed by
 *    [SolidtimeSyncWorker] when connectivity returns, so the user never loses a timer.
 */
@Singleton
class SolidtimeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SolidtimeApi,
    private val secure: SecureCredentialStore,
    private val dao: SolidtimeDao,
    private val linksSync: SolidtimeLinksSync,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val opMutex = Mutex()
    private val statePrefs = context.getSharedPreferences("tavla_solidtime_state", Context.MODE_PRIVATE)

    private val _connection = MutableStateFlow(readConnection())
    val connection: StateFlow<SolidtimeConnection> = _connection.asStateFlow()

    private val _runningTimer = MutableStateFlow<RunningTimer?>(restoreRunningTimer())
    val runningTimer: StateFlow<RunningTimer?> = _runningTimer.asStateFlow()

    val pendingOpCount: Flow<Int> = dao.observePendingCount()

    val usingFallbackStorage: Boolean get() = secure.usingFallback

    init {
        // If a timer was running when the process died, re-assert the foreground service and
        // reconcile with the server (it may have been stopped on the web meanwhile).
        if (_runningTimer.value != null) {
            ensureServiceRunning()
            appScope.launch { runCatching { refreshActiveTimer() } }
        }
    }

    // ------------------------------------------------------------------ credentials / connection

    private fun readConnection() = SolidtimeConnection(
        connected = secure.isConnected,
        serverUrl = secure.serverUrl,
        organizationName = secure.organizationName,
        tokenEncrypted = !secure.usingFallback,
    )

    private fun refreshConnection() { _connection.value = readConnection() }

    val isConnected: Boolean get() = secure.isConnected

    /** Normalizes a typed server address: keeps an explicit http://, defaults bare hosts to https://. */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        return when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            else -> "https://$t"
        }
    }

    /**
     * Validates the server URL + token by fetching the memberships, and stashes them (not yet
     * "connected" -- the organization still has to be chosen). Returns the memberships to pick from.
     */
    suspend fun connect(serverUrlRaw: String, token: String): Result<List<STMembership>> = runCatching {
        val url = normalizeUrl(serverUrlRaw)
        val memberships = api.getMemberships(url, token)
        if (memberships.isEmpty()) error("This token has no organizations.")
        secure.serverUrl = url
        secure.token = token
        refreshConnection()
        memberships
    }

    /** Commits the chosen organization; after this the integration is live. */
    fun selectOrganization(membership: STMembership) {
        secure.organizationId = membership.organizationId
        secure.organizationName = membership.organizationName
        secure.memberId = membership.memberId
        refreshConnection()
    }

    /**
     * Replaces the stored token (and server URL, in case it also changed) for the SAME
     * organization already connected -- the normal path when a Solidtime personal access token
     * expires and is rotated. Unlike [disconnect] + [connect], this touches none of the local
     * tables: board links, per-card project overrides, tracked time-entry mappings and the pending
     * queue all stay exactly as they were, since none of that data depends on the token itself,
     * only on the organization staying the same.
     */
    suspend fun updateToken(serverUrlRaw: String, token: String): Result<Unit> = runCatching {
        val url = normalizeUrl(serverUrlRaw)
        val memberships = api.getMemberships(url, token)
        val currentOrgId = secure.organizationId
        val match = memberships.firstOrNull { it.organizationId == currentOrgId }
            ?: error(
                "This token doesn't have access to ${secure.organizationName ?: "your organization"}. " +
                    "Use Disconnect below if you're switching Solidtime accounts."
            )
        secure.serverUrl = url
        secure.token = token
        secure.memberId = match.memberId
        refreshConnection()
    }

    suspend fun testConnection(): Result<Unit> = runCatching {
        val c = requireCreds()
        api.getMemberships(c.url, c.token) // cheapest authenticated call
        Unit
    }

    fun disconnect() {
        appScope.launch {
            clearRunningTimer()
            secure.clear()
            dao.clearBoardLinks(); dao.clearCardEntries(); dao.clearCardTasks(); dao.clearCardProjects(); dao.clearPendingOps()
            linksSync.reset()
            refreshConnection()
        }
    }

    private data class Creds(val url: String, val token: String, val orgId: String, val memberId: String)

    private fun requireCreds(): Creds {
        val url = secure.serverUrl; val token = secure.token
        if (url.isNullOrBlank() || token.isNullOrBlank()) error("Not connected to Solidtime.")
        return Creds(url, token, secure.organizationId.orEmpty(), secure.memberId.orEmpty())
    }

    // ------------------------------------------------------------------ projects / board links

    suspend fun listProjects(): Result<List<STProject>> = runCatching {
        val c = requireCreds(); api.getProjects(c.url, c.token, c.orgId).filterNot { it.isArchived }
    }

    suspend fun listClients(): Result<Map<String, String>> = runCatching {
        val c = requireCreds(); api.getClients(c.url, c.token, c.orgId).associate { it.id to it.name }
    }

    suspend fun createProject(name: String, color: String, billable: Boolean, clientId: String?): Result<STProject> =
        runCatching { val c = requireCreds(); api.createProject(c.url, c.token, c.orgId, name, color, billable, clientId) }

    fun observeBoardLink(boardId: Long): Flow<BoardLinkEntity?> = dao.observeBoardLink(boardId)
    fun observeAllBoardLinks(): Flow<List<BoardLinkEntity>> = dao.observeAllBoardLinks()
    suspend fun getBoardLink(boardId: Long): BoardLinkEntity? = dao.getBoardLink(boardId)

    /** Links [boardId] to [project]. [clientName] is resolved by the caller for display. */
    suspend fun linkBoard(boardId: Long, project: STProject, clientName: String?) {
        val existing = dao.getBoardLink(boardId)
        dao.upsertBoardLink(
            BoardLinkEntity(
                boardId = boardId,
                projectId = project.id,
                projectName = project.name,
                projectColor = project.color,
                clientName = clientName,
                projectBillable = project.isBillable,
                createTasks = existing?.createTasks ?: false,
                useListTag = existing?.useListTag ?: false,
                doneStackId = existing?.doneStackId,
            )
        )
        linksSync.notifyChanged()
    }

    suspend fun setBoardOptions(boardId: Long, createTasks: Boolean, useListTag: Boolean, doneStackId: Long?) {
        val l = dao.getBoardLink(boardId) ?: return
        dao.upsertBoardLink(l.copy(createTasks = createTasks, useListTag = useListTag, doneStackId = doneStackId))
        linksSync.notifyChanged()
    }

    suspend fun unlinkBoard(boardId: Long) {
        dao.deleteBoardLink(boardId)
        linksSync.notifyChanged()
    }

    // ------------------------------------------------------------------ per-card project override

    fun observeCardProject(cardId: Long): Flow<CardProjectEntity?> = dao.observeCardProject(cardId)
    suspend fun getCardProject(cardId: Long): CardProjectEntity? = dao.getCardProject(cardId)

    suspend fun setCardProject(cardId: Long, project: STProject, clientName: String?) {
        dao.upsertCardProject(
            CardProjectEntity(
                cardId = cardId,
                projectId = project.id,
                projectName = project.name,
                projectColor = project.color,
                clientName = clientName,
                projectBillable = project.isBillable,
            )
        )
        linksSync.notifyChanged()
    }

    suspend fun clearCardProject(cardId: Long) {
        dao.deleteCardProject(cardId)
        linksSync.notifyChanged()
    }

    /**
     * Resolves the effective project for a card: the per-card override if set, otherwise
     * the board's linked project. Returns null if neither exists.
     */
    private suspend fun resolveProject(boardId: Long, cardId: Long): ResolvedProject? {
        val cardOverride = dao.getCardProject(cardId)
        if (cardOverride != null) return ResolvedProject(
            cardOverride.projectId, cardOverride.projectName, cardOverride.projectBillable, isOverride = true,
        )
        val link = dao.getBoardLink(boardId) ?: return null
        return ResolvedProject(link.projectId, link.projectName, link.projectBillable, isOverride = false)
    }

    private data class ResolvedProject(
        val projectId: String,
        val projectName: String,
        val projectBillable: Boolean,
        val isOverride: Boolean,
    )

    // ------------------------------------------------------------------ timer: start / stop

    /**
     * Starts a timer for [cardId] on its linked board. [stackTitle] is the card's current list
     * (used as a tag when the board opts in). Guards the one-running-timer rule.
     */
    suspend fun startTimer(
        boardId: Long,
        cardId: Long,
        cardTitle: String,
        stackTitle: String,
        descriptionOverride: String? = null,
        billableOverride: Boolean? = null,
    ): Result<Unit> = opMutex.withLock {
        runCatching {
            if (_runningTimer.value != null) throw TimerBusyException()
            val c = requireCreds()
            val link = dao.getBoardLink(boardId) ?: error("This board isn't linked to a Solidtime project.")
            val resolved = resolveProject(boardId, cardId)
                ?: error("This board isn't linked to a Solidtime project.")
            val description = (descriptionOverride ?: cardTitle).trim()
            val billable = billableOverride ?: resolved.projectBillable
            val start = System.currentTimeMillis()

            val taskId = if (link.createTasks && !resolved.isOverride) resolveTaskId(c, link, cardId, cardTitle) else null
            val tagIds = if (link.useListTag && stackTitle.isNotBlank()) listOf(resolveTagId(c, stackTitle)) else emptyList()

            try {
                val entry = api.createTimeEntry(
                    c.url, c.token, c.orgId, c.memberId, start, null, billable,
                    resolved.projectId, taskId, description, tagIds,
                )
                dao.insertCardEntry(CardEntryEntity(entry.id, cardId, boardId))
                linksSync.notifyChanged()
                setRunningTimer(entry.toRunning(cardId, boardId, resolved.projectName, cardTitle))
            } catch (e: SolidtimeApiException) {
                if (e.code == 0) {
                    // Offline: start a local-only timer and queue the create for later.
                    val localId = "local-" + UUID.randomUUID()
                    queueOp(
                        createOp(localId, c, start, null, billable, resolved.projectId, taskId, description, tagIds, cardId, boardId)
                    )
                    setRunningTimer(
                        RunningTimer(localId, start, description, billable, resolved.projectId, taskId, tagIds, cardId, boardId, resolved.projectName, cardTitle)
                    )
                } else throw e
            }
            Unit
        }
    }

    suspend fun stopActiveTimer(): Result<Unit> = opMutex.withLock {
        runCatching {
            val rt = _runningTimer.value ?: return@runCatching
            val end = System.currentTimeMillis()
            val c = runCatching { requireCreds() }.getOrNull()

            if (rt.isLocalOnly) {
                // Never reached the server: turn its queued "create" into a completed entry.
                finalizeLocalCreate(rt.entryId, end)
                clearRunningTimer()
                return@runCatching
            }
            if (c == null) { clearRunningTimer(); return@runCatching }
            try {
                api.updateTimeEntry(
                    c.url, c.token, c.orgId, rt.entryId, c.memberId, rt.startMs, end, rt.billable,
                    rt.projectId, rt.taskId, rt.description, rt.tagIds,
                )
                clearRunningTimer()
            } catch (e: SolidtimeApiException) {
                when (e.code) {
                    0 -> { // offline: queue the stop and clear locally
                        queueOp(stopOp(rt, end, c))
                        clearRunningTimer()
                    }
                    404 -> clearRunningTimer() // already gone on the server
                    else -> throw e
                }
            }
            Unit
        }
    }

    /** Re-reads the server's active timer and reconciles local state (it may have changed on the web). */
    suspend fun refreshActiveTimer(): Result<Unit> = runCatching {
        if (!secure.isConnected) return@runCatching
        val c = requireCreds()
        val active = api.getActiveTimer(c.url, c.token)
        val current = _runningTimer.value
        if (active == null) {
            // Keep a local-only timer that hasn't synced yet; otherwise the server is authoritative.
            if (current != null && !current.isLocalOnly) clearRunningTimer()
        } else {
            val row = dao.getCardEntry(active.id)
            val projectName = dao.getAllBoardLinks().firstOrNull { it.projectId == active.projectId }?.projectName
            val cardTitle = if (current?.cardId == row?.cardId) current?.cardTitle else null
            setRunningTimer(active.toRunning(row?.cardId, row?.boardId, projectName, cardTitle))
        }
    }

    // ------------------------------------------------------------------ card time list

    data class CardTime(val totalSeconds: Int, val entries: List<STTimeEntry>)

    /** Total tracked time and the entry list for a card (only entries Tavla created for it). */
    suspend fun cardTime(cardId: Long, boardId: Long): Result<CardTime> = runCatching {
        val c = requireCreds()
        val resolved = resolveProject(boardId, cardId) ?: return@runCatching CardTime(0, emptyList())
        val ourIds = dao.entryIdsForCard(cardId).toSet()
        if (ourIds.isEmpty()) return@runCatching CardTime(0, emptyList())
        val all = api.getTimeEntriesForProject(c.url, c.token, c.orgId, c.memberId, resolved.projectId)
        val mine = all.filter { it.id in ourIds }.sortedByDescending { it.start }
        val runningExtra = _runningTimer.value
            ?.takeIf { it.cardId == cardId && it.isLocalOnly }
            ?.let { listOf(it.toLocalEntry()) }
            ?: emptyList()
        val entries = (runningExtra + mine)
        val total = entries.sumOf { e -> e.end?.let { ((it - e.start) / 1000).toInt() } ?: e.durationSeconds }
        CardTime(total, entries)
    }

    suspend fun addManualEntry(
        boardId: Long, cardId: Long, cardTitle: String, start: Long, end: Long,
        description: String, billable: Boolean,
    ): Result<Unit> = runCatching {
        val c = requireCreds()
        val link = dao.getBoardLink(boardId) ?: error("Board not linked.")
        val resolved = resolveProject(boardId, cardId) ?: error("Board not linked.")
        val taskId = if (link.createTasks && !resolved.isOverride) resolveTaskId(c, link, cardId, cardTitle) else null
        val entry = api.createTimeEntry(
            c.url, c.token, c.orgId, c.memberId, start, end, billable, resolved.projectId, taskId, description.ifBlank { cardTitle }, emptyList(),
        )
        dao.insertCardEntry(CardEntryEntity(entry.id, cardId, boardId))
        linksSync.notifyChanged()
        Unit
    }

    suspend fun updateEntry(entry: STTimeEntry, start: Long, end: Long?, description: String, billable: Boolean): Result<Unit> =
        runCatching {
            val c = requireCreds()
            api.updateTimeEntry(
                c.url, c.token, c.orgId, entry.id, c.memberId, start, end, billable,
                entry.projectId, entry.taskId, description, entry.tagIds,
            )
            Unit
        }

    suspend fun deleteEntry(entry: STTimeEntry): Result<Unit> = runCatching {
        val c = requireCreds()
        if (!entry.id.startsWith("local-")) api.deleteTimeEntry(c.url, c.token, c.orgId, entry.id)
        dao.deleteCardEntry(entry.id)
        linksSync.notifyChanged()
        Unit
    }

    // ------------------------------------------------------------------ tag / task resolution

    private suspend fun resolveTagId(c: Creds, name: String): String {
        val existing = api.getTags(c.url, c.token, c.orgId).firstOrNull { it.name.equals(name, ignoreCase = true) }
        return existing?.id ?: api.createTag(c.url, c.token, c.orgId, name).id
    }

    private suspend fun resolveTaskId(c: Creds, link: BoardLinkEntity, cardId: Long, cardTitle: String): String {
        dao.taskForCard(cardId)?.let { return it }
        val existing = api.getTasks(c.url, c.token, c.orgId)
            .firstOrNull { it.projectId == link.projectId && it.name.equals(cardTitle, ignoreCase = true) }
        val task = existing ?: api.createTask(c.url, c.token, c.orgId, cardTitle, link.projectId)
        dao.upsertCardTask(CardTaskEntity(cardId, task.id))
        linksSync.notifyChanged()
        return task.id
    }

    // ------------------------------------------------------------------ offline queue

    private fun createOp(
        localId: String, c: Creds, start: Long, end: Long?, billable: Boolean,
        projectId: String?, taskId: String?, description: String, tagIds: List<String>, cardId: Long, boardId: Long,
    ): JSONObject = JSONObject().apply {
        put("kind", "create"); put("localId", localId); put("orgId", c.orgId); put("memberId", c.memberId)
        put("start", start); put("end", end ?: JSONObject.NULL); put("billable", billable)
        put("projectId", projectId ?: JSONObject.NULL); put("taskId", taskId ?: JSONObject.NULL)
        put("description", description); put("tags", JSONArray(tagIds)); put("cardId", cardId); put("boardId", boardId)
    }

    private fun stopOp(rt: RunningTimer, end: Long, c: Creds): JSONObject = JSONObject().apply {
        put("kind", "stop"); put("entryId", rt.entryId); put("orgId", c.orgId); put("memberId", c.memberId)
        put("start", rt.startMs); put("end", end); put("billable", rt.billable)
        put("projectId", rt.projectId ?: JSONObject.NULL); put("taskId", rt.taskId ?: JSONObject.NULL)
        put("description", rt.description); put("tags", JSONArray(rt.tagIds))
    }

    private suspend fun queueOp(op: JSONObject) {
        dao.insertPendingOp(PendingOpEntity(opJson = op.toString(), createdAt = System.currentTimeMillis()))
        scheduleSync()
    }

    /** Turns a queued (still-running) "create" op for [localId] into a completed one, end set. */
    private suspend fun finalizeLocalCreate(localId: String, end: Long) {
        val ops = dao.pendingOps()
        val match = ops.firstOrNull {
            runCatching { JSONObject(it.opJson).optString("localId") == localId }.getOrDefault(false)
        }
        if (match != null) {
            val obj = JSONObject(match.opJson).put("end", end)
            dao.deletePendingOp(match.id)
            dao.insertPendingOp(PendingOpEntity(opJson = obj.toString(), createdAt = match.createdAt))
        }
        scheduleSync()
    }

    /** Replays queued ops. Called by [SolidtimeSyncWorker]. Returns true if the queue is now empty. */
    suspend fun syncPending(): Boolean {
        if (!secure.isConnected) return false
        val c = requireCreds()
        for (op in dao.pendingOps()) {
            val obj = runCatching { JSONObject(op.opJson) }.getOrNull()
            if (obj == null) { dao.deletePendingOp(op.id); continue }
            try {
                when (obj.optString("kind")) {
                    "create" -> {
                        val end = if (obj.isNull("end")) null else obj.optLong("end")
                        val entry = api.createTimeEntry(
                            c.url, c.token, c.orgId, c.memberId,
                            obj.optLong("start"), end, obj.optBoolean("billable"),
                            obj.optString("projectId").ifBlank { null }, obj.optString("taskId").ifBlank { null },
                            obj.optString("description"), obj.jsonStringList("tags"),
                        )
                        dao.insertCardEntry(CardEntryEntity(entry.id, obj.optLong("cardId"), obj.optLong("boardId")))
                        linksSync.notifyChanged()
                        // If this create was the running (local-only) timer, swap in the real id.
                        val localId = obj.optString("localId")
                        val rt = _runningTimer.value
                        if (end == null && rt != null && rt.entryId == localId) {
                            setRunningTimer(rt.copy(entryId = entry.id))
                        }
                    }
                    "stop" -> api.updateTimeEntry(
                        c.url, c.token, c.orgId, obj.optString("entryId"), c.memberId,
                        obj.optLong("start"), obj.optLong("end"), obj.optBoolean("billable"),
                        obj.optString("projectId").ifBlank { null }, obj.optString("taskId").ifBlank { null },
                        obj.optString("description"), obj.jsonStringList("tags"),
                    )
                }
                dao.deletePendingOp(op.id)
            } catch (e: SolidtimeApiException) {
                if (e.code == 0) return false // still offline: keep the rest for next time
                // Non-network error (e.g. project deleted): drop the poison op so the queue drains.
                Log.w(TAG, "Dropping un-replayable op ${op.id}: ${e.message}")
                dao.deletePendingOp(op.id)
            }
        }
        return dao.pendingOps().isEmpty()
    }

    private fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SolidtimeSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("solidtime_sync", ExistingWorkPolicy.KEEP, request)
    }

    // ------------------------------------------------------------------ running-timer state + service

    private fun setRunningTimer(rt: RunningTimer) {
        _runningTimer.value = rt
        persistRunningTimer(rt)
        ensureServiceRunning()
    }

    private fun clearRunningTimer() {
        _runningTimer.value = null
        persistRunningTimer(null)
        // The service observes runningTimer and stops itself when it goes null.
    }

    private fun ensureServiceRunning() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java)) }
            .onFailure { Log.w(TAG, "Could not start timer service: ${it.message}") }
    }

    private fun persistRunningTimer(rt: RunningTimer?) {
        val editor = statePrefs.edit()
        if (rt == null) editor.remove(KEY_RUNNING) else editor.putString(KEY_RUNNING, rt.toJson().toString())
        editor.apply()
    }

    private fun restoreRunningTimer(): RunningTimer? =
        statePrefs.getString(KEY_RUNNING, null)?.let { runCatching { RunningTimer.fromJson(JSONObject(it)) }.getOrNull() }

    companion object {
        private const val TAG = "SolidtimeRepo"
        private const val KEY_RUNNING = "running_timer"
    }
}

// -- small mapping helpers ------------------------------------------------------------------------

private fun STTimeEntry.toRunning(cardId: Long?, boardId: Long?, projectName: String?, cardTitle: String?) =
    RunningTimer(id, start, description, billable, projectId, taskId, tagIds, cardId, boardId, projectName, cardTitle)

private fun RunningTimer.toLocalEntry() =
    STTimeEntry("local", startMs, null, 0, description, projectId, taskId, billable, tagIds, "work")

private fun JSONObject.jsonStringList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
}

private fun RunningTimer.toJson(): JSONObject = JSONObject().apply {
    put("entryId", entryId); put("startMs", startMs); put("description", description); put("billable", billable)
    put("projectId", projectId ?: JSONObject.NULL); put("taskId", taskId ?: JSONObject.NULL)
    put("tagIds", JSONArray(tagIds)); put("cardId", cardId ?: JSONObject.NULL); put("boardId", boardId ?: JSONObject.NULL)
    put("projectName", projectName ?: JSONObject.NULL); put("cardTitle", cardTitle ?: JSONObject.NULL)
}

private fun RunningTimer.Companion.fromJson(o: JSONObject): RunningTimer {
    fun ns(k: String) = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() }
    fun nl(k: String) = if (o.isNull(k)) null else o.optLong(k)
    val tags = o.optJSONArray("tagIds") ?: JSONArray()
    return RunningTimer(
        entryId = o.optString("entryId"), startMs = o.optLong("startMs"), description = o.optString("description"),
        billable = o.optBoolean("billable"), projectId = ns("projectId"), taskId = ns("taskId"),
        tagIds = (0 until tags.length()).mapNotNull { tags.optString(it).takeIf { s -> s.isNotBlank() } },
        cardId = nl("cardId"), boardId = nl("boardId"), projectName = ns("projectName"), cardTitle = ns("cardTitle"),
    )
}
