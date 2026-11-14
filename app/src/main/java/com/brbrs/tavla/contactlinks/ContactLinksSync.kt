package com.brbrs.tavla.contactlinks

import android.util.Log
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.contactlinks.data.CardContactLinkEntity
import com.brbrs.tavla.contactlinks.data.ContactLinksDao
import com.brbrs.tavla.data.DeckDao
import com.brbrs.tavla.settings.WebDavClient
import com.brbrs.tavla.sync.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs card <-> contact links to `Tavla/contact-links.json`, the sidecar Vinci's
 * ContactLinksRepository resolves to show a contact's "Linked items" section. Same pattern
 * as SolidtimeLinksSync: Room is the working copy, both directions are routed through
 * [WorkScheduler] (durable WorkManager jobs with retry/backoff) rather than a bare coroutine,
 * so a push that hadn't landed yet when the process died (e.g. right before an uninstall)
 * still gets retried, and a reinstall's pull actually restores what's on the server.
 *
 * Unlike the Solidtime links, Vinci only ever reads this file -- it never writes it -- so
 * there's no cross-writer conflict to worry about beyond Tavla's own other devices.
 */
@Singleton
class ContactLinksSync @Inject constructor(
    private val dao: ContactLinksDao,
    private val deckDao: DeckDao,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    private val workScheduler: WorkScheduler,
) {
    private val syncMutex = Mutex()
    private var lastEtag: String? = null

    /** Why the most recent push to Nextcloud didn't leave the local Room table's links in
     * sync -- including the silent case where there was no Nextcloud session to push to at
     * all (Local mode). Null once a push succeeds. A card's own links still work locally
     * either way; this only explains why they might not be showing up in Vinci yet. */
    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    /**
     * Pulls the remote file and merges it into Room. Call once after sign-in or when the boards
     * screen opens. Durable: enqueues a WorkManager job that retries with backoff until it
     * succeeds -- a fresh install depends on this actually landing to bring card-contact links
     * back, since Room was just wiped by the reinstall.
     */
    fun fetchFromRemote() {
        workScheduler.scheduleContactLinksPull()
    }

    /**
     * Call after any local add/remove of a card-contact link. Durable and debounced: enqueues a
     * WorkManager job a couple seconds out, surviving process death instead of being lost with
     * a cancelled coroutine delay.
     */
    fun notifyChanged() {
        workScheduler.scheduleContactLinksPush()
    }

    fun reset() { lastEtag = null; _lastSyncError.value = null }

    /** Durable push, called by [com.brbrs.tavla.contactlinks.ContactLinksPushWorker]. True on success. */
    suspend fun pushDurable(): Boolean = syncMutex.withLock {
        runCatching { pushNow() }
            .onSuccess { _lastSyncError.value = null }
            .onFailure {
                Log.w(TAG, "push failed: ${it.message}")
                _lastSyncError.value = it.message ?: "Sync failed"
            }
            .isSuccess
    }

    /** Durable pull, called by [com.brbrs.tavla.contactlinks.ContactLinksPullWorker]. True on success. */
    suspend fun pullDurable(): Boolean = syncMutex.withLock {
        runCatching {
            val session = authRepository.currentSession() ?: return@runCatching
            davClient.ensureFolder(session, REMOTE_FOLDER)
            val remote = davClient.getFile(session, REMOTE_FILE)
            if (remote == null) {
                pushNow()
            } else {
                lastEtag = remote.etag
                mergeRemoteIntoLocal(parseLinks(JSONObject(remote.content)))
            }
        }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            .isSuccess
    }

    // -- internals --------------------------------------------------------------------------

    private suspend fun pushNow() {
        val session = authRepository.currentSession()
        if (session == null) {
            // Local mode, or not signed in yet: nothing to push to, so a card's contact link
            // stays this-device-only and will never reach Vinci until Tavla is connected to
            // Nextcloud. Surfaced via lastSyncError rather than silently doing nothing.
            _lastSyncError.value = "Not synced to Nextcloud -- connect an account in Settings so Vinci can see this"
            return
        }
        davClient.ensureFolder(session, REMOTE_FOLDER)
        val json = buildJson().toString()
        // Unconditional PUT, deliberately: there is no merge step on a conflict anyway (see
        // mergeRemoteIntoLocal below -- this device's Room table is the sole authority), so
        // the If-Match/etag dance was pure risk with no actual benefit. It also turned out to
        // fail hard rather than soft on some setups -- a Nextcloud instance behind a proxy that
        // tags the GET response with a different ETag than the PUT's conditional check expects
        // (gzip-compressing proxies commonly do this) means the one built-in retry 412s again
        // too, and the whole push -- including links that had nothing to do with any conflict
        // -- is abandoned. Writing unconditionally sidesteps that family of bugs entirely.
        lastEtag = davClient.putFile(session, REMOTE_FILE, json, ifMatchEtag = null)
    }

    /**
     * Inserts every remote cardId -> contactUid pair that isn't already in Room (IGNORE on
     * conflict, so this is a plain union). This is what restores a card's linked contacts after
     * a reinstall -- Room was just wiped, so without this nothing locally knows about any link
     * until the user manually re-adds it. A card id with no local Deck row yet still gets its
     * link rows written; they'll simply have nothing to show in the UI until this device's own
     * Deck sync catches up and the card appears.
     */
    private suspend fun mergeRemoteIntoLocal(remoteLinks: List<CardContactLinkEntity>) {
        remoteLinks.forEach { dao.insert(it) }
    }

    private fun parseLinks(json: JSONObject): List<CardContactLinkEntity> {
        val arr = json.optJSONArray("links") ?: return emptyList()
        return (0 until arr.length()).flatMap { i ->
            runCatching {
                val entry = arr.getJSONObject(i)
                val cardId = entry.getString("itemId").toLong()
                val uids = entry.optJSONArray("contactUids") ?: JSONArray()
                (0 until uids.length()).mapNotNull { j ->
                    uids.optString(j).takeIf { it.isNotBlank() }?.let { CardContactLinkEntity(cardId, it) }
                }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun buildJson(): JSONObject {
        val allLinks = dao.getAll()
        val byCard = allLinks.groupBy { it.cardId }
        val links = JSONArray()
        for ((cardId, entries) in byCard) {
            val card = deckDao.getCard(cardId) ?: continue
            val board = deckDao.getBoard(card.boardId)
            val entry = JSONObject().apply {
                put("itemId", cardId.toString())
                put("title", card.title)
                put("snippet", board?.title.orEmpty())
                put("updatedAt", card.lastModified)
                put("contactUids", JSONArray().apply { entries.forEach { put(it.contactUid) } })
            }
            links.put(entry)
        }
        return JSONObject().put("links", links)
    }

    companion object {
        private const val TAG = "ContactLinksSync"
        private const val REMOTE_FOLDER = "Tavla"
        private const val REMOTE_FILE = "Tavla/contact-links.json"
    }
}

// Convenience used by the ViewModel for add/remove + resync in one place.
@Singleton
class ContactLinksRepository @Inject constructor(
    private val dao: ContactLinksDao,
    private val sync: ContactLinksSync,
) {
    val lastSyncError: StateFlow<String?> get() = sync.lastSyncError

    fun observeForCard(cardId: Long) = dao.observeForCard(cardId)

    suspend fun link(cardId: Long, contactUid: String) {
        dao.insert(CardContactLinkEntity(cardId, contactUid))
        sync.notifyChanged()
    }

    suspend fun unlink(cardId: Long, contactUid: String) {
        dao.delete(cardId, contactUid)
        sync.notifyChanged()
    }
}
