package com.brbrs.tavla.network

import com.brbrs.tavla.auth.TavlaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

class DeckApiException(val code: Int, message: String) : Exception(message)

/** Thrown when the server returns 304 Not Modified for a conditional request. */
class NotModifiedException : Exception("Not modified")

/**
 * Thin client for the Nextcloud Deck REST API (v1.0) and its OCS comments API.
 * OkHttp + org.json only -- no Retrofit/Gson, which break under R8 in release builds.
 * Every call returns the raw JSON; [DeckJson] turns it into rows/models.
 */
@Singleton
class DeckApi @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun restBase(s: TavlaSession) = s.serverUrl.trimEnd('/') + "/index.php/apps/deck/api/v1.0"
    private fun ocsBase(s: TavlaSession)  = s.serverUrl.trimEnd('/') + "/ocs/v2.php/apps/deck/api/v1.0"

    private suspend fun call(
        s: TavlaSession,
        method: String,
        url: String,
        body: JSONObject? = null,
        ifModifiedSince: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val reqBody = when {
            body != null       -> body.toString().toRequestBody(jsonType)
            method == "GET"    -> null
            method == "DELETE" -> null
            else               -> "{}".toRequestBody(jsonType)
        }
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", s.basicAuthHeader())
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
        if (ifModifiedSince != null) builder.header("If-Modified-Since", ifModifiedSince)
        val request = builder.method(method, reqBody).build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 304) throw NotModifiedException()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}"
                throw DeckApiException(response.code, msg)
            }
            text
        }
    }

    // -- Boards ---------------------------------------------------------------------------

    suspend fun getBoards(s: TavlaSession): JSONArray =
        JSONArray(call(s, "GET", "${restBase(s)}/boards?details=true"))

    suspend fun getBoard(s: TavlaSession, boardId: Long): JSONObject =
        JSONObject(call(s, "GET", "${restBase(s)}/boards/$boardId"))

    suspend fun createBoard(s: TavlaSession, title: String, color: String): JSONObject =
        JSONObject(call(s, "POST", "${restBase(s)}/boards", JSONObject().put("title", title).put("color", color)))

    suspend fun updateBoard(s: TavlaSession, boardId: Long, title: String, color: String, archived: Boolean): JSONObject =
        JSONObject(
            call(
                s, "PUT", "${restBase(s)}/boards/$boardId",
                JSONObject().put("title", title).put("color", color).put("archived", archived),
            )
        )

    suspend fun deleteBoard(s: TavlaSession, boardId: Long) {
        call(s, "DELETE", "${restBase(s)}/boards/$boardId")
    }

    // -- Stacks (each stack arrives with its non-archived cards embedded) ------------------

    suspend fun getStacks(s: TavlaSession, boardId: Long): JSONArray =
        JSONArray(call(s, "GET", "${restBase(s)}/boards/$boardId/stacks"))

    /** Conditional fetch: throws [NotModifiedException] when the board hasn't changed. */
    suspend fun getStacksIfModified(s: TavlaSession, boardId: Long, ifModifiedSince: String): JSONArray =
        JSONArray(call(s, "GET", "${restBase(s)}/boards/$boardId/stacks", ifModifiedSince = ifModifiedSince))

    suspend fun createStack(s: TavlaSession, boardId: Long, title: String, order: Int): JSONObject =
        JSONObject(call(s, "POST", "${restBase(s)}/boards/$boardId/stacks", JSONObject().put("title", title).put("order", order)))

    suspend fun updateStack(s: TavlaSession, boardId: Long, stackId: Long, title: String, order: Int): JSONObject =
        JSONObject(
            call(s, "PUT", "${restBase(s)}/boards/$boardId/stacks/$stackId", JSONObject().put("title", title).put("order", order))
        )

    suspend fun deleteStack(s: TavlaSession, boardId: Long, stackId: Long) {
        call(s, "DELETE", "${restBase(s)}/boards/$boardId/stacks/$stackId")
    }

    // -- Cards ----------------------------------------------------------------------------

    private fun cardUrl(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long) =
        "${restBase(s)}/boards/$boardId/stacks/$stackId/cards/$cardId"

    suspend fun getCard(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long): JSONObject =
        JSONObject(call(s, "GET", cardUrl(s, boardId, stackId, cardId)))

    suspend fun createCard(
        s: TavlaSession, boardId: Long, stackId: Long, title: String, order: Int,
    ): JSONObject =
        JSONObject(
            call(
                s, "POST", "${restBase(s)}/boards/$boardId/stacks/$stackId/cards",
                JSONObject().put("title", title).put("type", "plain").put("order", order),
            )
        )

    /** Deck's PUT requires title, type, owner and order on every update, so the full card is sent. */
    suspend fun updateCard(
        s: TavlaSession,
        boardId: Long,
        stackId: Long,
        cardId: Long,
        title: String,
        description: String,
        order: Int,
        ownerUid: String,
        dueDateIso: String?,
        archived: Boolean,
        doneIso: String? = UNSET_DONE,
    ): JSONObject {
        val body = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("type", "plain")
            .put("order", order)
            .put("owner", ownerUid)
            .put("duedate", dueDateIso ?: JSONObject.NULL)
            .put("archived", archived)
        // #09: "done" is omitted by default so plain field edits never touch it. Older Deck
        // servers ignore unknown fields on PUT, so sending it when explicitly requested is safe.
        if (doneIso !== UNSET_DONE) body.put("done", doneIso ?: JSONObject.NULL)
        return JSONObject(call(s, "PUT", cardUrl(s, boardId, stackId, cardId), body))
    }

    companion object {
        /** Sentinel so updateCard can tell "leave done alone" apart from "clear done" (null). */
        private val UNSET_DONE = "\u0000unset"

        /** Pass as updateCard's doneIso to leave the "done" field untouched by this update. */
        fun unsetDone(): String? = UNSET_DONE
    }

    suspend fun moveCard(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, targetStackId: Long, order: Int) {
        call(
            s, "PUT", cardUrl(s, boardId, stackId, cardId) + "/reorder",
            JSONObject().put("stackId", targetStackId).put("order", order),
        )
    }

    suspend fun deleteCard(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long) {
        call(s, "DELETE", cardUrl(s, boardId, stackId, cardId))
    }

    suspend fun assignLabel(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, labelId: Long) {
        call(s, "PUT", cardUrl(s, boardId, stackId, cardId) + "/assignLabel", JSONObject().put("labelId", labelId))
    }

    suspend fun removeLabel(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, labelId: Long) {
        call(s, "PUT", cardUrl(s, boardId, stackId, cardId) + "/removeLabel", JSONObject().put("labelId", labelId))
    }

    suspend fun assignUser(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, uid: String) {
        call(s, "PUT", cardUrl(s, boardId, stackId, cardId) + "/assignUser", JSONObject().put("userId", uid))
    }

    suspend fun unassignUser(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, uid: String) {
        call(s, "PUT", cardUrl(s, boardId, stackId, cardId) + "/unassignUser", JSONObject().put("userId", uid))
    }

    // -- Labels ---------------------------------------------------------------------------

    suspend fun createLabel(s: TavlaSession, boardId: Long, title: String, color: String): JSONObject =
        JSONObject(call(s, "POST", "${restBase(s)}/boards/$boardId/labels", JSONObject().put("title", title).put("color", color)))

    // -- Attachments (#08) ------------------------------------------------------------------

    suspend fun getAttachments(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long): JSONArray =
        JSONArray(call(s, "GET", "${cardUrl(s, boardId, stackId, cardId)}/attachments"))

    /** Uploads [fileBytes] as a new "deck_file" attachment named [fileName]. */
    suspend fun uploadAttachment(
        s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, fileName: String, mimeType: String, fileBytes: ByteArray,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("type", "deck_file")
            .addFormDataPart(
                "file", fileName,
                fileBytes.toRequestBody((mimeType.ifBlank { "application/octet-stream" }).toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("${cardUrl(s, boardId, stackId, cardId)}/attachments")
            .header("Authorization", s.basicAuthHeader())
            .header("OCS-APIRequest", "true")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DeckApiException(response.code, "Upload failed (HTTP ${response.code})")
            JSONObject(text)
        }
    }

    /** Downloads an attachment's raw bytes, for saving to a local cache file before opening it. */
    suspend fun downloadAttachment(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, attachmentId: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${cardUrl(s, boardId, stackId, cardId)}/attachments/$attachmentId")
                .header("Authorization", s.basicAuthHeader())
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DeckApiException(response.code, "Download failed (HTTP ${response.code})")
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    suspend fun deleteAttachment(s: TavlaSession, boardId: Long, stackId: Long, cardId: Long, attachmentId: Long) {
        call(s, "DELETE", "${cardUrl(s, boardId, stackId, cardId)}/attachments/$attachmentId")
    }

    // -- Comments (OCS API) ---------------------------------------------------------------

    suspend fun getComments(s: TavlaSession, cardId: Long): JSONArray {
        val root = JSONObject(call(s, "GET", "${ocsBase(s)}/cards/$cardId/comments?limit=100&offset=0"))
        return root.optJSONObject("ocs")?.optJSONArray("data") ?: JSONArray()
    }

    suspend fun addComment(s: TavlaSession, cardId: Long, message: String): JSONObject {
        val root = JSONObject(call(s, "POST", "${ocsBase(s)}/cards/$cardId/comments", JSONObject().put("message", message)))
        return root.optJSONObject("ocs")?.optJSONObject("data") ?: JSONObject()
    }

    suspend fun deleteComment(s: TavlaSession, cardId: Long, commentId: Long) {
        call(s, "DELETE", "${ocsBase(s)}/cards/$cardId/comments/$commentId")
    }
}
