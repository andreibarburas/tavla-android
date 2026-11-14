package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.Attachment
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.data.DeckComment
import com.brbrs.tavla.data.DeckLabel
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.data.DeckUser
import com.brbrs.tavla.data.Stack
import com.brbrs.tavla.di.ApplicationScope
import com.brbrs.tavla.reminders.ReminderScheduler
import com.brbrs.tavla.settings.CrossAppLink
import com.brbrs.tavla.settings.SettingsRepository
import com.brbrs.tavla.solidtime.STProject
import com.brbrs.tavla.solidtime.STTimeEntry
import com.brbrs.tavla.solidtime.SolidtimeRepository
import com.brbrs.tavla.solidtime.friendlyStError
import com.brbrs.tavla.solidtime.timer.RunningTimer
import com.brbrs.tavla.ui.components.friendlyError
import com.brbrs.tavla.util.addChecklistItem
import com.brbrs.tavla.util.toggleChecklistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CardUiState(
    val card: Card? = null,
    val board: Board? = null,
    val boardImage: String? = null,
    val stacks: List<Stack> = emptyList(),
    val username: String = "",
    /** Non-null while the user has unsaved title edits. */
    val titleDraft: String? = null,
    /** Non-null while the description editor is open. */
    val descriptionDraft: String? = null,
    val comments: List<DeckComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentDraft: String = "",
    val attachments: List<Attachment> = emptyList(),
    val attachmentsLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
    val links: List<CrossAppLink> = emptyList(),
    val isSaving: Boolean = false,
    val closed: Boolean = false,
    val message: String? = null,
) {
    val canEdit: Boolean get() = board?.canEdit ?: true
    val stackTitle: String get() = stacks.firstOrNull { it.id == card?.stackId }?.title.orEmpty()
    val isDone: Boolean get() = card?.doneAt != null
}

/** A linked contact as shown on the card -- blank name if the contact is no longer on-device. */
data class LinkedContact(val cardavUid: String, val displayName: String, val photoUri: String? = null)

private data class CardLocal(
    val titleDraft: String? = null,
    val descriptionDraft: String? = null,
    val comments: List<DeckComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentDraft: String = "",
    val attachments: List<Attachment> = emptyList(),
    val attachmentsLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
    val isSaving: Boolean = false,
    val closed: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class CardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeckRepository,
    authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val solidtime: SolidtimeRepository,
    coverRepository: com.brbrs.tavla.settings.BoardCoverRepository,
    private val contactLinks: com.brbrs.tavla.contactlinks.ContactLinksRepository,
    private val deviceContacts: com.brbrs.tavla.contactlinks.DeviceContactsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val cardId: Long = checkNotNull(savedStateHandle.get<Long>("cardId"))
    private val boardId: Long = checkNotNull(savedStateHandle.get<Long>("boardId"))
    private val local = MutableStateFlow(CardLocal())

    // Resolved display names for this card's linked contacts, re-resolved whenever the set of
    // linked uids changes. A uid with no match (contact removed from the device) is dropped
    // from display but stays linked -- it'll resolve again if the contact reappears.
    private val _linkedContacts = MutableStateFlow<List<LinkedContact>>(emptyList())
    val linkedContactsState: StateFlow<List<LinkedContact>> = _linkedContacts.asStateFlow()

    /** Surfaces why the last contact-link push to Nextcloud failed (or didn't happen at all --
     * e.g. this device isn't signed into Nextcloud), so a silently-dropped sync isn't a mystery
     * on the Vinci side. Null when the last push succeeded or nothing has been linked yet. */
    val linkSyncError: StateFlow<String?> = contactLinks.lastSyncError

    init {
        viewModelScope.launch {
            contactLinks.observeForCard(cardId).collect { uids ->
                val details = deviceContacts.detailsFor(uids.toSet())
                _linkedContacts.value = uids.map { uid ->
                    val d = details[uid]
                    LinkedContact(uid, d?.first.orEmpty(), d?.second)
                }
            }
        }
    }

    fun linkContact(contact: com.brbrs.tavla.contactlinks.PickableContact) {
        viewModelScope.launch { contactLinks.link(cardId, contact.cardavUid) }
    }

    fun unlinkContact(cardavUid: String) {
        viewModelScope.launch { contactLinks.unlink(cardId, cardavUid) }
    }

    suspend fun searchContacts(query: String) = deviceContacts.search(query)

    val uiState: StateFlow<CardUiState> = combine(
        repository.observeCard(cardId),
        repository.observeBoard(boardId),
        repository.observeStacks(boardId),
        combine(
            authRepository.session.map { it?.username.orEmpty() },
            settingsRepository.settings.map { it.cardLinks[cardId].orEmpty() },
            coverRepository.readyCovers.map { it[boardId] },
        ) { u, links, cover -> Triple(u, links, cover) },
        local,
    ) { card, board, stacks, (username, links, boardImage), l ->
        CardUiState(
            card = card, board = board, boardImage = boardImage, stacks = stacks, username = username,
            titleDraft = l.titleDraft, descriptionDraft = l.descriptionDraft,
            comments = l.comments, commentsLoading = l.commentsLoading, commentDraft = l.commentDraft,
            attachments = l.attachments, attachmentsLoading = l.attachmentsLoading, uploadingAttachment = l.uploadingAttachment,
            links = links,
            isSaving = l.isSaving, closed = l.closed, message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardUiState())

    // -- Solidtime time tracking -------------------------------------------------------------
    // Declared before init: loadCardTime()/refreshActiveTimer() run with Dispatchers.Main.immediate
    // and execute synchronously during construction, so timerLocal must exist first.

    private val timerLocal = MutableStateFlow(CardTimerLocal())

    init {
        loadComments()
        loadAttachments()
        // Skip Solidtime calls entirely when it's not set up -- otherwise every card open would
        // surface a "Not connected to Solidtime" message to people who don't use the integration.
        if (solidtime.isConnected) {
            refreshActiveTimer()
            loadCardTime()
        }
    }

    val timerState: StateFlow<CardTimerState> = combine(
        solidtime.observeBoardLink(boardId),
        solidtime.observeCardProject(cardId),
        solidtime.runningTimer,
        combine(solidtime.connection, timerLocal) { conn, l -> conn to l },
    ) { link, cardProject, running, (conn, l) ->
        val effectiveName = cardProject?.projectName ?: link?.projectName
        val effectiveBillable = cardProject?.projectBillable ?: link?.projectBillable ?: true
        CardTimerState(
            linked = conn.connected && link != null,
            projectName = effectiveName,
            overrideProjectName = cardProject?.projectName,
            boardProjectName = link?.projectName,
            billableDefault = effectiveBillable,
            running = running,
            runningThisCard = running?.cardId == cardId,
            totalSeconds = l.total,
            entries = l.entries,
            loading = l.loading,
            working = l.working,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardTimerState())

    fun consumeTimerMessage() = timerLocal.update { it.copy(message = null) }

    fun refreshActiveTimer() {
        viewModelScope.launch { runCatching { solidtime.refreshActiveTimer() } }
    }

    fun loadCardTime() {
        viewModelScope.launch {
            timerLocal.update { it.copy(loading = true) }
            val r = solidtime.cardTime(cardId, boardId)
            timerLocal.update {
                it.copy(
                    loading = false,
                    total = r.getOrNull()?.totalSeconds ?: it.total,
                    entries = r.getOrNull()?.entries ?: it.entries,
                    message = r.exceptionOrNull()?.let(::friendlyStError) ?: it.message,
                )
            }
        }
    }

    private fun stackTitleFor(c: Card): String = uiState.value.stacks.firstOrNull { it.id == c.stackId }?.title.orEmpty()

    fun startTimer(descriptionOverride: String?, billableOverride: Boolean?) {
        val c = card ?: return
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = solidtime.startTimer(boardId, cardId, c.title, stackTitleFor(c), descriptionOverride, billableOverride)
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    fun stopTimer() {
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = solidtime.stopActiveTimer()
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    fun addManualEntry(start: Long, end: Long, description: String, billable: Boolean) {
        val c = card ?: return
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = solidtime.addManualEntry(boardId, cardId, c.title, start, end, description, billable)
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    fun updateEntry(entry: STTimeEntry, start: Long, end: Long?, description: String, billable: Boolean) {
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = solidtime.updateEntry(entry, start, end, description, billable)
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    fun deleteEntry(entry: STTimeEntry) {
        viewModelScope.launch {
            val r = solidtime.deleteEntry(entry)
            timerLocal.update { it.copy(message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    // -- Per-card project override

    /** Loads the list of available Solidtime projects for the picker. */
    fun loadProjects(onResult: (List<STProject>) -> Unit) {
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = solidtime.listProjects()
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            r.getOrNull()?.let(onResult)
        }
    }

    fun setCardProject(project: STProject, clientName: String?) {
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = runCatching { solidtime.setCardProject(cardId, project, clientName) }
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    fun clearCardProject() {
        viewModelScope.launch {
            timerLocal.update { it.copy(working = true) }
            val r = runCatching { solidtime.clearCardProject(cardId) }
            timerLocal.update { it.copy(working = false, message = r.exceptionOrNull()?.let(::friendlyStError)) }
            if (r.isSuccess) loadCardTime()
        }
    }

    private val card: Card? get() = uiState.value.card

    fun consumeMessage() = local.update { it.copy(message = null) }

    /** Runs a write, shows a spinner, and reports failures as a snackbar. */
    private fun write(success: String? = null, after: () -> Unit = {}, block: suspend (Card) -> Unit) {
        val c = card ?: return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true) }
            val r = runCatching { block(c) }
            local.update { it.copy(isSaving = false, message = r.exceptionOrNull()?.let(::friendlyError) ?: success) }
            if (r.isSuccess) after()
        }
    }

    // -- Title

    fun onTitleChanged(value: String) = local.update { it.copy(titleDraft = value) }

    fun saveTitle() {
        val draft = local.value.titleDraft?.trim() ?: return
        val c = card ?: return
        if (draft.isBlank() || draft == c.title) { local.update { it.copy(titleDraft = null) }; return }
        write(after = { local.update { it.copy(titleDraft = null) } }) { repository.updateCard(it, title = draft) }
    }

    // -- Description & checklist

    fun startEditingDescription() = local.update { it.copy(descriptionDraft = card?.description.orEmpty()) }
    fun onDescriptionChanged(value: String) = local.update { it.copy(descriptionDraft = value) }
    fun cancelDescription() = local.update { it.copy(descriptionDraft = null) }

    fun saveDescription() {
        val draft = local.value.descriptionDraft ?: return
        val c = card ?: return
        if (draft == c.description) { local.update { it.copy(descriptionDraft = null) }; return }
        write(after = { local.update { it.copy(descriptionDraft = null) } }) { repository.updateCard(it, description = draft) }
    }

    fun toggleChecklist(lineIndex: Int) = write { c ->
        repository.updateCard(c, description = toggleChecklistItem(c.description, lineIndex))
    }

    fun addChecklist(text: String) {
        if (text.isBlank()) return
        write { c -> repository.updateCard(c, description = addChecklistItem(c.description, text)) }
    }

    // -- Meta

    fun setDueDate(epochMs: Long?) = write { c ->
        repository.updateCard(c, dueDate = epochMs)
        reminderScheduler.reschedule(c.copy(dueDate = epochMs))
    }

    /** #09: marks the card done or not done. Harmless no-op field on Deck servers that don't support it yet. */
    fun setDone(done: Boolean) = write(if (done) "Marked done" else "Marked not done") { c ->
        repository.setCardDone(c, done)
        if (done) reminderScheduler.cancel(c.id, c.boardId)
    }

    fun setLabel(label: DeckLabel, assigned: Boolean) = write { repository.setLabel(it, label, assigned) }

    fun createAndAssignLabel(title: String, color: String) = write { c ->
        val label = repository.createLabel(c.boardId, title.trim(), color)
        repository.setLabel(c, label, true)
    }

    fun setAssignee(user: DeckUser, assigned: Boolean) = write { repository.setAssignee(it, user, assigned) }

    fun moveTo(stack: Stack) = write("Moved to ${stack.title}") { c ->
        if (c.stackId != stack.id) {
            // Append to the end of the target list; the server re-indexes the rest.
            repository.moveCard(c, stack.id, 9_999)
            // Solidtime: moving into the board's designated "done" list auto-stops a running timer.
            val link = solidtime.getBoardLink(boardId)
            if (link?.doneStackId == stack.id && solidtime.runningTimer.value?.cardId == cardId) {
                solidtime.stopActiveTimer()
            }
        }
    }

    fun setArchived(archived: Boolean) = write(
        success = if (archived) "Card archived" else "Card restored",
        after = { if (archived) local.update { it.copy(closed = true) } },
    ) { c ->
        repository.updateCard(c, archived = archived)
        if (archived) reminderScheduler.cancel(c.id, c.boardId)
    }

    fun delete() = write(after = { local.update { it.copy(closed = true) } }) { c ->
        reminderScheduler.cancel(c.id, c.boardId)
        repository.deleteCard(c)
    }

    // -- Attachments (#08)

    fun loadAttachments() {
        val c = card ?: return
        viewModelScope.launch {
            local.update { it.copy(attachmentsLoading = true) }
            val r = runCatching { repository.getAttachments(c) }
            local.update {
                it.copy(
                    attachmentsLoading = false,
                    attachments = r.getOrDefault(it.attachments),
                    message = r.exceptionOrNull()?.let { e -> "Attachments: " + friendlyError(e) } ?: it.message,
                )
            }
        }
    }

    fun uploadAttachment(fileName: String, mimeType: String, bytes: ByteArray) {
        val c = card ?: return
        viewModelScope.launch {
            local.update { it.copy(uploadingAttachment = true) }
            val r = runCatching { repository.uploadAttachment(c, fileName, mimeType, bytes) }
            local.update { it.copy(uploadingAttachment = false, message = r.exceptionOrNull()?.let(::friendlyError)) }
            if (r.isSuccess) loadAttachments()
        }
    }

    /** Downloads the attachment's bytes; the screen writes them to a cache file and opens it. */
    suspend fun downloadAttachment(attachment: Attachment): Result<ByteArray> {
        val c = card ?: return Result.failure(IllegalStateException("No card"))
        return runCatching { repository.downloadAttachment(c, attachment.id) }
            .onFailure { local.update { s -> s.copy(message = friendlyError(it)) } }
    }

    fun deleteAttachment(attachment: Attachment) {
        val c = card ?: return
        viewModelScope.launch {
            val r = runCatching { repository.deleteAttachment(c, attachment.id) }
            local.update {
                if (r.isSuccess) it.copy(attachments = it.attachments.filterNot { a -> a.id == attachment.id })
                else it.copy(message = friendlyError(r.exceptionOrNull()!!))
            }
        }
    }

    // -- Cross-app links (#12)

    /** See [CrossAppLink]'s kdoc for the deep-link contract other BarburasLab apps need to support. */
    fun addLink(appPackage: String, appLabel: String, label: String, uri: String) {
        settingsRepository.addCardLink(cardId, CrossAppLink(UUID.randomUUID().toString(), appPackage, appLabel, label, uri))
    }

    fun removeLink(linkId: String) = settingsRepository.removeCardLink(cardId, linkId)

    // -- Comments

    fun loadComments() {
        viewModelScope.launch {
            local.update { it.copy(commentsLoading = true) }
            val r = runCatching { repository.getComments(cardId) }
            local.update {
                it.copy(
                    commentsLoading = false,
                    comments = r.getOrDefault(it.comments),
                    message = r.exceptionOrNull()?.let { e -> "Comments: " + friendlyError(e) } ?: it.message,
                )
            }
        }
    }

    fun onCommentChanged(value: String) = local.update { it.copy(commentDraft = value) }

    fun postComment() {
        val text = local.value.commentDraft.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true) }
            val r = runCatching { repository.addComment(cardId, text) }
            r.onSuccess { comment ->
                local.update { it.copy(isSaving = false, commentDraft = "", comments = if (comment != null) it.comments + comment else it.comments) }
            }.onFailure { e ->
                local.update { it.copy(isSaving = false, message = friendlyError(e)) }
            }
        }
    }

    fun deleteComment(comment: DeckComment) {
        viewModelScope.launch {
            val r = runCatching { repository.deleteComment(cardId, comment.id) }
            local.update {
                if (r.isSuccess) it.copy(comments = it.comments.filterNot { c -> c.id == comment.id })
                else it.copy(message = friendlyError(r.exceptionOrNull()!!))
            }
        }
    }

    /**
     * Called when leaving the screen: pending title/description edits are saved on the
     * application scope so the request survives this ViewModel being cleared.
     */
    fun saveDraftsOnExit() {
        val c = card ?: return
        val l = local.value
        val newTitle = l.titleDraft?.trim()?.takeIf { it.isNotBlank() && it != c.title }
        val newDesc = l.descriptionDraft?.takeIf { it != c.description }
        if (newTitle == null && newDesc == null) return
        appScope.launch {
            runCatching {
                repository.updateCard(c, title = newTitle ?: c.title, description = newDesc ?: c.description)
            }
        }
    }
}

/** UI state for the card's time-tracking section. */
data class CardTimerState(
    val linked: Boolean = false,
    /** The effective project name (override if set, else board default). */
    val projectName: String? = null,
    /** Non-null when this card has a per-card project override. */
    val overrideProjectName: String? = null,
    /** The board's default project name (shown when an override is active so the user sees what they're overriding). */
    val boardProjectName: String? = null,
    val billableDefault: Boolean = true,
    val running: RunningTimer? = null,
    val runningThisCard: Boolean = false,
    val totalSeconds: Int = 0,
    val entries: List<STTimeEntry> = emptyList(),
    val loading: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
) {
    val hasOverride: Boolean get() = overrideProjectName != null
}

private data class CardTimerLocal(
    val total: Int = 0,
    val entries: List<STTimeEntry> = emptyList(),
    val loading: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)
