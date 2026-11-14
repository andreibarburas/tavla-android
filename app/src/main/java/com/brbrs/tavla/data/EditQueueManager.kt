package com.brbrs.tavla.data

import android.util.Log
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.network.DeckApi
import com.brbrs.tavla.network.DeckJson
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EditQueue"

/**
 * Intercepts every write operation in [DeckRepository]. When the device is online the call goes
 * straight to the Deck API (behaviour identical to before). When the API call fails with a
 * network error the mutation is saved as a [PendingEditEntity] so the [EditReplayWorker] can
 * replay it later, and an optimistic update is written to Room so the UI stays responsive.
 */
@Singleton
class EditQueueManager @Inject constructor(
    private val api: DeckApi,
    private val dao: DeckDao,
    private val pendingDao: PendingEditDao,
    private val auth: AuthRepository,
) {
    private suspend fun session() =
        auth.currentSession() ?: throw IllegalStateException("Not signed in")

    /** Returns true if this looks like a network/connectivity error that should be queued. */
    private fun isNetworkError(e: Throwable): Boolean =
        e is java.net.UnknownHostException ||
        e is java.net.ConnectException ||
        e is java.net.SocketTimeoutException ||
        e is java.io.IOException && e !is com.brbrs.tavla.network.DeckApiException

    // -- Public API called by DeckRepository -------------------------------------------------

    /**
     * Tries [online] first; on a network error, calls [optimistic] (to update Room so the UI
     * reflects the change immediately) and enqueues the edit for later replay.
     */
    suspend fun <T> tryOrQueue(
        actionType: String,
        payload: JSONObject,
        online: suspend () -> T,
        optimistic: (suspend () -> Unit)? = null,
    ): T? {
        // In local mode, skip the API call entirely and just do the optimistic update.
        if (auth.isLocalMode()) {
            optimistic?.invoke()
            return null
        }
        return try {
            online()
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                Log.i(TAG, "Queued $actionType (${e.javaClass.simpleName})")
                optimistic?.invoke()
                pendingDao.insert(PendingEditEntity(actionType = actionType, payloadJson = payload.toString()))
                null
            } else {
                throw e
            }
        }
    }

    // -- Replay (called by EditReplayWorker) -------------------------------------------------

    /** Replays all pending edits in order. Returns the number of successfully replayed edits. */
    suspend fun replayAll(): Int {
        val edits = pendingDao.getAll()
        if (edits.isEmpty()) return 0
        val s = session()
        var replayed = 0
        for (edit in edits) {
            try {
                val payload = JSONObject(edit.payloadJson)
                replay(s, edit.actionType, payload)
                pendingDao.delete(edit.id)
                replayed++
            } catch (e: Exception) {
                if (isNetworkError(e)) {
                    // Still offline — stop trying so the worker can retry later.
                    break
                }
                // Server error (e.g. 404 because the card was already deleted elsewhere):
                // increment attempts so stale edits eventually get cleaned up.
                Log.w(TAG, "Replay failed for ${edit.actionType}: ${e.message}")
                pendingDao.incrementAttempts(edit.id)
            }
        }
        pendingDao.deleteStale()
        return replayed
    }

    @Suppress("LongMethod")
    private suspend fun replay(
        s: com.brbrs.tavla.auth.TavlaSession,
        type: String,
        p: JSONObject,
    ) {
        when (type) {
            PendingEditType.CREATE_BOARD -> {
                api.createBoard(s, p.getString("title"), p.getString("color"))
            }
            PendingEditType.UPDATE_BOARD -> {
                api.updateBoard(s, p.getLong("boardId"), p.getString("title"), p.getString("color"), p.getBoolean("archived"))
            }
            PendingEditType.DELETE_BOARD -> {
                api.deleteBoard(s, p.getLong("boardId"))
            }
            PendingEditType.CREATE_STACK -> {
                api.createStack(s, p.getLong("boardId"), p.getString("title"), p.getInt("order"))
            }
            PendingEditType.RENAME_STACK -> {
                api.updateStack(s, p.getLong("boardId"), p.getLong("stackId"), p.getString("title"), p.getInt("order"))
            }
            PendingEditType.REORDER_STACK -> {
                api.updateStack(s, p.getLong("boardId"), p.getLong("stackId"), p.getString("title"), p.getInt("newOrder"))
            }
            PendingEditType.DELETE_STACK -> {
                api.deleteStack(s, p.getLong("boardId"), p.getLong("stackId"))
            }
            PendingEditType.CREATE_CARD -> {
                api.createCard(s, p.getLong("boardId"), p.getLong("stackId"), p.getString("title"), p.getInt("order"))
            }
            PendingEditType.UPDATE_CARD -> {
                api.updateCard(
                    s, p.getLong("boardId"), p.getLong("stackId"), p.getLong("cardId"),
                    title = p.getString("title"),
                    description = p.getString("description"),
                    order = p.getInt("order"),
                    ownerUid = p.getString("ownerUid"),
                    dueDateIso = p.optString("dueDateIso").takeIf { it.isNotEmpty() },
                    archived = p.getBoolean("archived"),
                    doneIso = if (p.has("doneIso")) p.optString("doneIso").takeIf { it.isNotEmpty() } else DeckApi.unsetDone(),
                )
            }
            PendingEditType.MOVE_CARD -> {
                api.moveCard(s, p.getLong("boardId"), p.getLong("stackId"), p.getLong("cardId"), p.getLong("targetStackId"), p.getInt("order"))
            }
            PendingEditType.DELETE_CARD -> {
                api.deleteCard(s, p.getLong("boardId"), p.getLong("stackId"), p.getLong("cardId"))
            }
            PendingEditType.SET_CARD_DONE -> {
                val boardId = p.getLong("boardId"); val stackId = p.getLong("stackId"); val cardId = p.getLong("cardId")
                // Refetch the card to get current fields, then send the done update.
                val card = api.getCard(s, boardId, stackId, cardId)
                api.updateCard(
                    s, boardId, stackId, cardId,
                    title = card.getString("title"),
                    description = card.optString("description", ""),
                    order = card.optInt("order"),
                    ownerUid = card.optJSONObject("owner")?.optString("uid") ?: s.username,
                    dueDateIso = card.optString("duedate").takeIf { it.isNotEmpty() && it != "null" },
                    archived = card.optBoolean("archived"),
                    doneIso = if (p.getBoolean("done")) p.optString("doneIso").takeIf { it.isNotEmpty() } else null,
                )
            }
            PendingEditType.SET_LABEL -> {
                val boardId = p.getLong("boardId"); val stackId = p.getLong("stackId"); val cardId = p.getLong("cardId")
                val labelId = p.getLong("labelId"); val assigned = p.getBoolean("assigned")
                if (assigned) api.assignLabel(s, boardId, stackId, cardId, labelId)
                else api.removeLabel(s, boardId, stackId, cardId, labelId)
            }
            PendingEditType.SET_ASSIGNEE -> {
                val boardId = p.getLong("boardId"); val stackId = p.getLong("stackId"); val cardId = p.getLong("cardId")
                val uid = p.getString("uid"); val assigned = p.getBoolean("assigned")
                if (assigned) api.assignUser(s, boardId, stackId, cardId, uid)
                else api.unassignUser(s, boardId, stackId, cardId, uid)
            }
            else -> Log.w(TAG, "Unknown edit type: $type")
        }
    }
}
