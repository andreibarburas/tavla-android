package com.brbrs.tavla.data

import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.auth.TavlaSession
import com.brbrs.tavla.network.DeckApi
import com.brbrs.tavla.network.DeckJson
import com.brbrs.tavla.network.NotModifiedException
import com.brbrs.tavla.sync.WorkScheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Deck data. Screens observe Room (so boards open instantly and
 * stay readable offline); network calls write the server's answer back into Room.
 */
@Singleton
class DeckRepository @Inject constructor(
    private val api: DeckApi,
    private val dao: DeckDao,
    private val auth: AuthRepository,
    private val editQueue: EditQueueManager,
    private val workScheduler: WorkScheduler,
) {
    private suspend fun isLocalMode() = auth.isLocalMode()

    /**
     * Returns the active session. In local mode no session exists, but write methods
     * are guarded by [EditQueueManager.tryOrQueue] which short-circuits before using
     * the session, so the [DUMMY_LOCAL_SESSION] is safe — it is never sent to the API.
     */
    private suspend fun session(): TavlaSession =
        auth.currentSession() ?: if (auth.isLocalMode()) DUMMY_LOCAL_SESSION
        else throw IllegalStateException("Not signed in")

    private companion object {
        val DUMMY_LOCAL_SESSION = TavlaSession(serverUrl = "", username = "", appPassword = "")
    }

    /**
     * Looks up which board a card lives on from its id alone -- used to resolve a
     * `tavla://card/{cardId}` cross-app deep link into the (boardId, cardId) pair the nav
     * graph actually navigates with. Null if the card isn't in the local cache (not yet
     * synced, or deleted).
     */
    suspend fun boardIdForCard(cardId: Long): Long? = dao.getCard(cardId)?.boardId

    /**
     * Whether a due-date reminder fired for [cardId] should actually be shown right now. An alarm
     * is a plain [android.app.AlarmManager] fire-and-forget: once scheduled, nothing automatically
     * cancels it if the card is later completed, archived, or deleted (locally, or remotely via
     * sync) through a path that doesn't happen to call [com.brbrs.tavla.reminders.ReminderScheduler]
     * -- so [com.brbrs.tavla.reminders.ReminderReceiver] re-checks the card's live state here before
     * ever showing a notification, rather than trusting the data baked into the alarm when it was
     * scheduled.
     */
    suspend fun isCardStillDue(cardId: Long): Boolean {
        val card = dao.getCard(cardId) ?: return false
        return !card.archived && card.doneAt == null
    }

    // -- Mapping ---------------------------------------------------------------------------

    private fun BoardEntity.toModel() = Board(
        id = id, title = title, color = color, archived = archived, ownerUid = ownerUid,
        labels = DeckJson.labelsFromJson(labelsJson), users = DeckJson.usersFromJson(usersJson),
        canEdit = canEdit, canManage = canManage, isShared = isShared,
    )

    private fun StackEntity.toModel() = Stack(id = id, boardId = boardId, title = title, order = sortOrder)

    private fun CardEntity.toModel() = Card(
        id = id, boardId = boardId, stackId = stackId, title = title, description = description,
        order = sortOrder, dueDate = dueDate, doneAt = doneAt, createdAt = createdAt, archived = archived, ownerUid = ownerUid,
        labels = DeckJson.labelsFromJson(labelsJson), assignees = DeckJson.usersFromJson(assigneesJson),
        commentsCount = commentsCount, attachmentCount = attachmentCount,
    )

    private fun DueCardRow.toModel() = CardWithContext(
        card = Card(
            id = id, boardId = boardId, stackId = stackId, title = title, description = description,
            order = sortOrder, dueDate = dueDate, doneAt = doneAt, createdAt = createdAt, archived = archived, ownerUid = ownerUid,
            labels = DeckJson.labelsFromJson(labelsJson), assignees = DeckJson.usersFromJson(assigneesJson),
            commentsCount = commentsCount, attachmentCount = attachmentCount,
        ),
        boardTitle = boardTitle,
        boardColor = boardColor,
    )

    // -- Observation ----------------------------------------------------------------------

    fun observeBoards(): Flow<List<Board>> = dao.observeBoards().map { list -> list.map { it.toModel() } }
    fun observeBoard(id: Long): Flow<Board?> = dao.observeBoard(id).map { it?.toModel() }
    fun observeStacks(boardId: Long): Flow<List<Stack>> = dao.observeStacks(boardId).map { l -> l.map { it.toModel() } }
    fun observeCards(boardId: Long): Flow<List<Card>> = dao.observeCardsForBoard(boardId).map { l -> l.map { it.toModel() } }
    fun observeCard(id: Long): Flow<Card?> = dao.observeCard(id).map { it?.toModel() }
    fun observeDueCards(until: Long): Flow<List<CardWithContext>> = dao.observeDueCards(until).map { l -> l.map { it.toModel() } }
    /** #01: every open card across every board, title+description searchable on the boards screen. */
    fun observeAllOpenCards(): Flow<List<CardWithContext>> = dao.observeAllOpenCards().map { l -> l.map { it.toModel() } }
    fun observeCardCounts(until: Long): Flow<Map<Long, BoardCardCount>> =
        dao.observeCardCounts(until).map { l -> l.associateBy { it.boardId } }

    // -- Refresh --------------------------------------------------------------------------

    /** Fetches all boards, then the stacks/cards of every active board (max 4 at a time).
     *  Everything is written in a single transaction so the UI sees one atomic update
     *  (no intermediate states where boards exist but their counts are stale or zero). */
    suspend fun refreshAll() {
        if (isLocalMode()) return
        val s = session()
        val arr = api.getBoards(s)
        val boards = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .filterNot { DeckJson.isDeleted(it) }
            .map { DeckJson.boardEntity(it) }
        // Fetch all board content concurrently, collecting results in memory.
        val gate = Semaphore(4)
        val results = coroutineScope {
            boards.filterNot { it.archived }.map { b ->
                async { gate.withPermit { runCatching { fetchBoardContent(s, b.id) }.getOrNull() } }
            }.awaitAll()
        }
        // Write boards + all their content in one transaction so observers see a single update.
        val batch = results.filterNotNull().associateBy({ it.boardId }, { Pair(it.stacks, it.cards) })
        dao.replaceAllContent(boards, batch)
    }

    private data class BoardContent(val boardId: Long, val stacks: List<StackEntity>, val cards: List<CardEntity>)

    /** Refreshes one board's metadata (labels, members, permissions) and its stacks and cards. */
    suspend fun refreshBoard(boardId: Long) {
        if (isLocalMode()) return
        val s = session()
        val boardJson = api.getBoard(s, boardId)
        dao.upsertBoards(listOf(DeckJson.boardEntity(boardJson)))
        refreshBoardContent(s, boardId, cachedLastModified = null) // force full fetch for single-board refresh
    }

    /**
     * @param cachedLastModified epoch seconds of the board's last known modification; when
     * non-null the request includes If-Modified-Since so the server can return 304.
     */
    /** Fetches stacks/cards for one board and returns them without touching the DB. */
    private suspend fun fetchBoardContent(s: TavlaSession, boardId: Long, cachedLastModified: Long? = null): BoardContent? {
        val stacksArr = try {
            if (cachedLastModified != null && cachedLastModified > 0) {
                val httpDate = java.time.ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(cachedLastModified),
                    java.time.ZoneOffset.UTC,
                ).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                api.getStacksIfModified(s, boardId, httpDate)
            } else {
                api.getStacks(s, boardId)
            }
        } catch (_: NotModifiedException) {
            return null // board hasn't changed
        }
        val stacks = mutableListOf<StackEntity>()
        val cards = mutableListOf<CardEntity>()
        for (i in 0 until stacksArr.length()) {
            val st = stacksArr.optJSONObject(i) ?: continue
            if (DeckJson.isDeleted(st)) continue
            val stack = DeckJson.stackEntity(st, boardId)
            stacks += stack
            val cardsArr = st.optJSONArray("cards") ?: continue
            for (j in 0 until cardsArr.length()) {
                val c = cardsArr.optJSONObject(j) ?: continue
                if (DeckJson.isDeleted(c)) continue
                cards += DeckJson.cardEntity(c, boardId, stack.id)
            }
        }
        return BoardContent(boardId, stacks, cards)
    }

    /** Single-board refresh that writes immediately (used by refreshBoard). */
    private suspend fun refreshBoardContent(s: TavlaSession, boardId: Long, cachedLastModified: Long? = null) {
        val content = fetchBoardContent(s, boardId, cachedLastModified) ?: return
        dao.replaceBoardContent(boardId, content.stacks, content.cards)
    }

    private suspend fun refetchCard(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long) {
        val json = api.getCard(s, boardId, stackId, cardId)
        dao.upsertCards(listOf(DeckJson.cardEntity(json, boardId, stackId)))
    }

    // -- Boards ---------------------------------------------------------------------------

    /** Schedule edit replay — no-op in local mode (nothing to replay to). */
    private suspend fun scheduleReplay() {
        if (!auth.isLocalMode()) workScheduler.scheduleEditReplay()
    }

    suspend fun createBoard(title: String, color: String): Long {
        if (isLocalMode()) {
            val localId = -(System.currentTimeMillis() / 1000)
            dao.upsertBoards(listOf(BoardEntity(
                id = localId, title = title, color = color, archived = false,
                ownerUid = "", labelsJson = "[]", usersJson = "[]",
                canEdit = true, canManage = true, isShared = false,
                lastModified = System.currentTimeMillis() / 1000,
            )))
            return localId
        }
        val s = session()
        val payload = JSONObject().put("title", title).put("color", color)
        val id = editQueue.tryOrQueue(PendingEditType.CREATE_BOARD, payload, online = {
            val json = api.createBoard(s, title, color)
            val id = json.getLong("id")
            dao.upsertBoards(listOf(DeckJson.boardEntity(api.getBoard(s, id))))
            refreshBoardContent(s, id)
            id
        })
        if (id == null) { scheduleReplay(); return -1L }
        return id
    }

    suspend fun updateBoard(board: Board, title: String = board.title, color: String = board.color, archived: Boolean = board.archived) {
        val s = session()
        val payload = JSONObject().put("boardId", board.id).put("title", title).put("color", color).put("archived", archived)
        val result = editQueue.tryOrQueue(PendingEditType.UPDATE_BOARD, payload, online = {
            api.updateBoard(s, board.id, title, color, archived)
            dao.upsertBoards(listOf(DeckJson.boardEntity(api.getBoard(s, board.id))))
        }, optimistic = {
            dao.getBoard(board.id)?.let { existing ->
                dao.upsertBoards(listOf(existing.copy(title = title, color = color, archived = archived)))
            }
        })
        if (result == null) scheduleReplay()
    }

    suspend fun deleteBoard(boardId: Long) {
        val payload = JSONObject().put("boardId", boardId)
        val result = editQueue.tryOrQueue(PendingEditType.DELETE_BOARD, payload, online = {
            api.deleteBoard(session(), boardId)
        }, optimistic = {
            // Optimistic: remove from local cache immediately
        })
        // Always remove locally so the UI updates instantly
        dao.deleteBoard(boardId)
        dao.deleteStacksForBoard(boardId)
        dao.deleteCardsForBoard(boardId)
        if (result == null) scheduleReplay()
    }

    // -- Stacks ---------------------------------------------------------------------------

    suspend fun createStack(boardId: Long, title: String, order: Int) {
        if (isLocalMode()) {
            val localId = -(System.currentTimeMillis() / 1000)
            dao.upsertStacks(listOf(StackEntity(id = localId, boardId = boardId, title = title, sortOrder = order)))
            return
        }
        val s = session()
        val payload = JSONObject().put("boardId", boardId).put("title", title).put("order", order)
        val result = editQueue.tryOrQueue(PendingEditType.CREATE_STACK, payload, online = {
            val json = api.createStack(s, boardId, title, order)
            dao.upsertStacks(listOf(DeckJson.stackEntity(json, boardId)))
        })
        if (result == null) scheduleReplay()
    }

    suspend fun renameStack(stack: Stack, title: String) {
        val s = session()
        val payload = JSONObject().put("boardId", stack.boardId).put("stackId", stack.id).put("title", title).put("order", stack.order)
        val result = editQueue.tryOrQueue(PendingEditType.RENAME_STACK, payload, online = {
            val json = api.updateStack(s, stack.boardId, stack.id, title, stack.order)
            dao.upsertStacks(listOf(DeckJson.stackEntity(json, stack.boardId)))
        }, optimistic = {
            dao.upsertStacks(listOf(StackEntity(id = stack.id, boardId = stack.boardId, title = title, sortOrder = stack.order)))
        })
        if (result == null) scheduleReplay()
    }

    /** #06: persists a new tab order after dragging a list's tab to a different position. */
    suspend fun reorderStack(stack: Stack, newOrder: Int) {
        val s = session()
        val payload = JSONObject().put("boardId", stack.boardId).put("stackId", stack.id).put("title", stack.title).put("newOrder", newOrder)
        val result = editQueue.tryOrQueue(PendingEditType.REORDER_STACK, payload, online = {
            val json = api.updateStack(s, stack.boardId, stack.id, stack.title, newOrder)
            dao.upsertStacks(listOf(DeckJson.stackEntity(json, stack.boardId)))
        }, optimistic = {
            dao.upsertStacks(listOf(StackEntity(id = stack.id, boardId = stack.boardId, title = stack.title, sortOrder = newOrder)))
        })
        if (result == null) scheduleReplay()
    }

    suspend fun deleteStack(stack: Stack) {
        val payload = JSONObject().put("boardId", stack.boardId).put("stackId", stack.id)
        val result = editQueue.tryOrQueue(PendingEditType.DELETE_STACK, payload, online = {
            api.deleteStack(session(), stack.boardId, stack.id)
        })
        dao.deleteStack(stack.id)
        dao.deleteCardsForStack(stack.id)
        if (result == null) scheduleReplay()
    }

    // -- Cards ----------------------------------------------------------------------------

    suspend fun createCard(boardId: Long, stackId: Long, title: String, order: Int): Long {
        if (isLocalMode()) {
            val localId = -(System.currentTimeMillis() / 1000)
            val now = System.currentTimeMillis() / 1000
            dao.upsertCards(listOf(CardEntity(
                id = localId, boardId = boardId, stackId = stackId, title = title,
                description = "", sortOrder = order, dueDate = null, doneAt = null,
                createdAt = now, archived = false, ownerUid = "",
                labelsJson = "[]", assigneesJson = "[]",
                commentsCount = 0, attachmentCount = 0, lastModified = now,
            )))
            return localId
        }
        val s = session()
        val payload = JSONObject().put("boardId", boardId).put("stackId", stackId).put("title", title).put("order", order)
        val id = editQueue.tryOrQueue(PendingEditType.CREATE_CARD, payload, online = {
            val json = api.createCard(s, boardId, stackId, title, order)
            val id = json.getLong("id")
            dao.upsertCards(listOf(DeckJson.cardEntity(json, boardId, stackId)))
            id
        })
        if (id == null) { scheduleReplay(); return -1L }
        return id
    }

    suspend fun updateCard(
        card: Card,
        title: String = card.title,
        description: String = card.description,
        dueDate: Long? = card.dueDate,
        archived: Boolean = card.archived,
        /** Pass a value to change done status; omit to leave it untouched (the common case). */
        doneAt: Long? = card.doneAt,
        touchDone: Boolean = false,
    ) {
        val s = session()
        val dueDateIso = dueDate?.let { DeckJson.formatDate(it) }
        val doneIso = if (touchDone) doneAt?.let { DeckJson.formatDate(it) } else com.brbrs.tavla.network.DeckApi.unsetDone()
        val ownerUid = card.ownerUid.ifBlank { s.username }
        val payload = JSONObject()
            .put("boardId", card.boardId).put("stackId", card.stackId).put("cardId", card.id)
            .put("title", title).put("description", description).put("order", card.order)
            .put("ownerUid", ownerUid).put("archived", archived)
        if (dueDateIso != null) payload.put("dueDateIso", dueDateIso)
        if (doneIso !== com.brbrs.tavla.network.DeckApi.unsetDone()) {
            if (doneIso != null) payload.put("doneIso", doneIso)
        }
        val result = editQueue.tryOrQueue(PendingEditType.UPDATE_CARD, payload, online = {
            api.updateCard(s, card.boardId, card.stackId, card.id, title = title, description = description,
                order = card.order, ownerUid = ownerUid, dueDateIso = dueDateIso, archived = archived, doneIso = doneIso)
            refetchCard(s, card.boardId, card.stackId, card.id)
        }, optimistic = {
            dao.getCard(card.id)?.let { existing ->
                dao.upsertCards(listOf(existing.copy(
                    title = title, description = description, dueDate = dueDate, archived = archived,
                    doneAt = if (touchDone) doneAt else existing.doneAt,
                )))
            }
        })
        if (result == null) scheduleReplay()
    }

    /** #09: marks a card done (now) or not done, if this Deck server supports the field. */
    suspend fun setCardDone(card: Card, done: Boolean) =
        updateCard(card, doneAt = if (done) System.currentTimeMillis() else null, touchDone = true)

    /** Moves a card to the end of [targetStackId]. */
    suspend fun moveCard(card: Card, targetStackId: Long, targetOrder: Int) {
        val s = session()
        val payload = JSONObject()
            .put("boardId", card.boardId).put("stackId", card.stackId)
            .put("cardId", card.id).put("targetStackId", targetStackId).put("order", targetOrder)
        val result = editQueue.tryOrQueue(PendingEditType.MOVE_CARD, payload, online = {
            api.moveCard(s, card.boardId, card.stackId, card.id, targetStackId, targetOrder)
            refetchCard(s, card.boardId, targetStackId, card.id)
        }, optimistic = {
            dao.getCard(card.id)?.let { existing ->
                dao.upsertCards(listOf(existing.copy(stackId = targetStackId, sortOrder = targetOrder)))
            }
        })
        if (result == null) scheduleReplay()
    }

    suspend fun deleteCard(card: Card) {
        val payload = JSONObject().put("boardId", card.boardId).put("stackId", card.stackId).put("cardId", card.id)
        val result = editQueue.tryOrQueue(PendingEditType.DELETE_CARD, payload, online = {
            api.deleteCard(session(), card.boardId, card.stackId, card.id)
        })
        dao.deleteCard(card.id)
        if (result == null) scheduleReplay()
    }

    suspend fun setLabel(card: Card, label: DeckLabel, assigned: Boolean) {
        val s = session()
        val payload = JSONObject()
            .put("boardId", card.boardId).put("stackId", card.stackId).put("cardId", card.id)
            .put("labelId", label.id).put("assigned", assigned)
        val result = editQueue.tryOrQueue(PendingEditType.SET_LABEL, payload, online = {
            if (assigned) api.assignLabel(s, card.boardId, card.stackId, card.id, label.id)
            else api.removeLabel(s, card.boardId, card.stackId, card.id, label.id)
            refetchCard(s, card.boardId, card.stackId, card.id)
        }, optimistic = {
            dao.getCard(card.id)?.let { existing ->
                val arr = org.json.JSONArray(existing.labelsJson)
                if (assigned) {
                    arr.put(JSONObject().put("id", label.id).put("title", label.title).put("color", label.color))
                } else {
                    val filtered = (0 until arr.length()).mapNotNull { i -> arr.getJSONObject(i).takeIf { it.getLong("id") != label.id } }
                    val newArr = org.json.JSONArray(); filtered.forEach { newArr.put(it) }
                    dao.upsertCards(listOf(existing.copy(labelsJson = newArr.toString())))
                    return@let
                }
                dao.upsertCards(listOf(existing.copy(labelsJson = arr.toString())))
            }
        })
        if (result == null) scheduleReplay()
    }

    suspend fun setAssignee(card: Card, user: DeckUser, assigned: Boolean) {
        val s = session()
        val payload = JSONObject()
            .put("boardId", card.boardId).put("stackId", card.stackId).put("cardId", card.id)
            .put("uid", user.uid).put("assigned", assigned)
        val result = editQueue.tryOrQueue(PendingEditType.SET_ASSIGNEE, payload, online = {
            if (assigned) api.assignUser(s, card.boardId, card.stackId, card.id, user.uid)
            else api.unassignUser(s, card.boardId, card.stackId, card.id, user.uid)
            refetchCard(s, card.boardId, card.stackId, card.id)
        }, optimistic = {
            dao.getCard(card.id)?.let { existing ->
                val arr = org.json.JSONArray(existing.assigneesJson)
                if (assigned) {
                    arr.put(JSONObject().put("uid", user.uid).put("displayname", user.displayName))
                } else {
                    val filtered = (0 until arr.length()).mapNotNull { i -> arr.getJSONObject(i).takeIf { it.optString("uid") != user.uid } }
                    val newArr = org.json.JSONArray(); filtered.forEach { newArr.put(it) }
                    dao.upsertCards(listOf(existing.copy(assigneesJson = newArr.toString())))
                    return@let
                }
                dao.upsertCards(listOf(existing.copy(assigneesJson = arr.toString())))
            }
        })
        if (result == null) scheduleReplay()
    }

    suspend fun createLabel(boardId: Long, title: String, color: String): DeckLabel {
        if (isLocalMode()) {
            val localId = -(System.currentTimeMillis() / 1000)
            val label = DeckLabel(localId, title, color)
            // Update the board's labelsJson to include the new label
            dao.getBoard(boardId)?.let { board ->
                val arr = org.json.JSONArray(board.labelsJson)
                arr.put(JSONObject().put("id", localId).put("title", title).put("color", color))
                dao.upsertBoards(listOf(board.copy(labelsJson = arr.toString())))
            }
            return label
        }
        val s = session()
        val json = api.createLabel(s, boardId, title, color)
        dao.upsertBoards(listOf(DeckJson.boardEntity(api.getBoard(s, boardId))))
        return DeckLabel(json.getLong("id"), json.optString("title", title), json.optString("color", color))
    }

    // -- Attachments (#08) ------------------------------------------------------------------

    suspend fun getAttachments(card: Card): List<Attachment> {
        if (isLocalMode()) return emptyList()
        val s = session()
        val arr = api.getAttachments(s, card.boardId, card.stackId, card.id)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { DeckJson.attachment(it) }
    }

    suspend fun uploadAttachment(card: Card, fileName: String, mimeType: String, bytes: ByteArray) {
        if (isLocalMode()) return
        val s = session()
        api.uploadAttachment(s, card.boardId, card.stackId, card.id, fileName, mimeType, bytes)
        refetchCard(s, card.boardId, card.stackId, card.id)
    }

    /** Downloads an attachment's bytes -- the caller writes them to a cache file and opens that. */
    suspend fun downloadAttachment(card: Card, attachmentId: Long): ByteArray {
        if (isLocalMode()) return ByteArray(0)
        return api.downloadAttachment(session(), card.boardId, card.stackId, card.id, attachmentId)
    }

    suspend fun deleteAttachment(card: Card, attachmentId: Long) {
        if (isLocalMode()) return
        val s = session()
        api.deleteAttachment(s, card.boardId, card.stackId, card.id, attachmentId)
        refetchCard(s, card.boardId, card.stackId, card.id)
    }

    // -- Comments -------------------------------------------------------------------------

    suspend fun getComments(cardId: Long): List<DeckComment> {
        if (isLocalMode()) return emptyList()
        val arr = api.getComments(session(), cardId)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .map { DeckJson.comment(it) }
            .sortedBy { it.createdAt }
    }

    suspend fun addComment(cardId: Long, message: String): DeckComment? {
        if (isLocalMode()) return null
        return DeckJson.comment(api.addComment(session(), cardId, message))
    }

    suspend fun deleteComment(cardId: Long, commentId: Long) {
        if (isLocalMode()) return
        api.deleteComment(session(), cardId, commentId)
    }

    // -- Housekeeping ---------------------------------------------------------------------

    /** Quick check used right after login: does this server have Deck enabled? */
    suspend fun probeDeck(session: TavlaSession): Result<Unit> = runCatching { api.getBoards(session); Unit }

    suspend fun clearCache() = dao.clearAll()
}
