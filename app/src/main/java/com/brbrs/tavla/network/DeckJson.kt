package com.brbrs.tavla.network

import com.brbrs.tavla.data.BoardEntity
import com.brbrs.tavla.data.CardEntity
import com.brbrs.tavla.data.DeckComment
import com.brbrs.tavla.data.DeckLabel
import com.brbrs.tavla.data.DeckUser
import com.brbrs.tavla.data.StackEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Converts Deck API JSON into Room rows / models, and back. Tolerant of fields older Deck versions omit. */
object DeckJson {

    // -- Dates

    fun parseDate(value: Any?): Long? {
        val s = (value as? String)?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(s).toEpochMilli() }
            .getOrNull()
    }

    fun formatDate(epochMs: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    // -- Small helpers

    private fun JSONObject.optNullableString(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    /** Deck returns owners either as a user object or as a bare uid string, depending on version/endpoint. */
    private fun ownerUid(json: JSONObject): String = when (val o = json.opt("owner")) {
        is JSONObject -> o.optString("uid", o.optString("primaryKey", ""))
        is String     -> o
        else          -> ""
    }

    private fun ownerUser(json: JSONObject): DeckUser? = when (val o = json.opt("owner")) {
        is JSONObject -> {
            val uid = o.optString("uid", o.optString("primaryKey", ""))
            if (uid.isBlank()) null else DeckUser(uid, o.optString("displayname", uid))
        }
        is String -> if (o.isBlank()) null else DeckUser(o, o)
        else -> null
    }

    fun parseLabels(arr: JSONArray?): List<DeckLabel> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val l = arr.optJSONObject(i) ?: return@mapNotNull null
            DeckLabel(id = l.optLong("id"), title = l.optString("title"), color = l.optString("color", "31CC7C"))
        }
    }

    fun labelsToJson(labels: List<DeckLabel>): String = JSONArray().apply {
        labels.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("color", it.color)) }
    }.toString()

    fun labelsFromJson(json: String): List<DeckLabel> =
        runCatching { parseLabels(JSONArray(json)) }.getOrDefault(emptyList())

    fun usersToJson(users: List<DeckUser>): String = JSONArray().apply {
        users.forEach { put(JSONObject().put("uid", it.uid).put("displayName", it.displayName)) }
    }.toString()

    fun usersFromJson(json: String): List<DeckUser> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val u = arr.optJSONObject(i) ?: return@mapNotNull null
            DeckUser(u.optString("uid"), u.optString("displayName", u.optString("uid")))
        }
    }.getOrDefault(emptyList())

    /** Assigned users arrive as { participant: { uid, displayname, type } }; type 0 = user. */
    private fun parseAssignees(arr: JSONArray?): List<DeckUser> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val p = a.optJSONObject("participant") ?: return@mapNotNull null
            val uid = p.optString("uid", p.optString("primaryKey", ""))
            if (uid.isBlank()) null else DeckUser(uid, p.optString("displayname", uid))
        }
    }

    // -- Boards

    fun isDeleted(json: JSONObject): Boolean = json.optLong("deletedAt", 0L) > 0L

    fun boardEntity(json: JSONObject): BoardEntity {
        val users = linkedMapOf<String, DeckUser>()
        ownerUser(json)?.let { users[it.uid] = it }
        json.optJSONArray("users")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.optJSONObject(i) ?: continue
                val uid = u.optString("uid", u.optString("primaryKey", ""))
                if (uid.isNotBlank()) users.putIfAbsent(uid, DeckUser(uid, u.optString("displayname", uid)))
            }
        }
        val acl = json.optJSONArray("acl")
        acl?.let { arr ->
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                if (entry.optInt("type", 0) != 0) continue // groups/circles can't be assigned directly
                val p = entry.optJSONObject("participant") ?: continue
                val uid = p.optString("uid", p.optString("primaryKey", ""))
                if (uid.isNotBlank()) users.putIfAbsent(uid, DeckUser(uid, p.optString("displayname", uid)))
            }
        }
        val perms = json.optJSONObject("permissions")
        return BoardEntity(
            id           = json.getLong("id"),
            title        = json.optString("title"),
            color        = json.optString("color", "0082C9").removePrefix("#"),
            archived     = json.optBoolean("archived", false),
            ownerUid     = ownerUid(json),
            labelsJson   = labelsToJson(parseLabels(json.optJSONArray("labels"))),
            usersJson    = usersToJson(users.values.toList()),
            canEdit      = perms?.optBoolean("PERMISSION_EDIT", true) ?: true,
            canManage    = perms?.optBoolean("PERMISSION_MANAGE", true) ?: true,
            isShared     = (acl?.length() ?: 0) > 0,
            lastModified = json.optLong("lastModified", 0L),
        )
    }

    // -- Stacks & cards

    fun stackEntity(json: JSONObject, boardId: Long): StackEntity = StackEntity(
        id        = json.getLong("id"),
        boardId   = boardId,
        title     = json.optString("title"),
        sortOrder = json.optInt("order", 0),
    )

    fun cardEntity(json: JSONObject, boardId: Long, fallbackStackId: Long = 0L): CardEntity = CardEntity(
        id              = json.getLong("id"),
        boardId         = boardId,
        stackId         = json.optLong("stackId", fallbackStackId),
        title           = json.optString("title"),
        description     = json.optNullableString("description"),
        sortOrder       = json.optInt("order", 0),
        dueDate         = parseDate(json.opt("duedate")),
        // "done" is a nullable datetime on newer Deck versions (#09); absent entirely on older
        // ones, in which case optString/opt() below just yields null and the feature no-ops.
        doneAt          = parseDate(json.opt("done")),
        // createdAt is usually a unix-seconds field on Deck cards, but handle an ISO string too
        // in case a server version differs -- used only for the board insights screen, never
        // required, so a missing/unparseable value just excludes the card from that average.
        createdAt       = parseDate(json.opt("createdAt"))
            ?: json.optLong("createdAt", 0L).takeIf { it > 0 }?.times(1000L),
        archived        = json.optBoolean("archived", false),
        ownerUid        = ownerUid(json),
        labelsJson      = labelsToJson(parseLabels(json.optJSONArray("labels"))),
        assigneesJson   = usersToJson(parseAssignees(json.optJSONArray("assignedUsers"))),
        commentsCount   = json.optInt("commentsCount", 0),
        attachmentCount = json.optInt("attachmentCount", 0),
        lastModified    = json.optLong("lastModified", 0L),
    )

    // -- Attachments (#08)

    fun attachment(json: JSONObject): com.brbrs.tavla.data.Attachment {
        val name = json.optString("data", json.optString("filename", "file")).substringAfterLast('/')
        val extended = json.optJSONObject("extendedData")
        // createdAt can be an ISO datetime (OCS) or unix seconds (Deck's own field) depending on version.
        val created = parseDate(json.opt("createdAt")) ?: (json.optLong("createdAt", 0L) * 1000L)
        return com.brbrs.tavla.data.Attachment(
            id = json.optLong("id"),
            fileName = name,
            extension = name.substringAfterLast('.', ""),
            sizeBytes = extended?.optLong("filesize", 0L) ?: 0L,
            createdAt = created,
        )
    }

    // -- Comments

    fun comment(json: JSONObject): DeckComment = DeckComment(
        id               = json.optLong("id"),
        message          = json.optString("message"),
        actorId          = json.optString("actorId"),
        actorDisplayName = json.optString("actorDisplayName", json.optString("actorId")),
        createdAt        = parseDate(json.opt("creationDateTime")) ?: 0L,
    )
}
