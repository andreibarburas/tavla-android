package com.brbrs.tavla.solidtime

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parses Solidtime JSON into models, and formats the one date shape the API expects on writes. */
object SolidtimeJson {

    /** Solidtime requires exactly this format on writes: "2024-02-26T17:17:17Z" (UTC). */
    private val writeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun formatInstant(epochMs: Long): String = writeFormat.format(Instant.ofEpochMilli(epochMs))

    fun parseInstant(value: Any?): Long? {
        val s = (value as? String)?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(s).toEpochMilli() }
            .getOrNull()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    // -- Collections: Solidtime wraps lists in { "data": [...], "meta": {...} } --

    fun dataArray(root: JSONObject): JSONArray = root.optJSONArray("data") ?: JSONArray()

    /** Last page number from a paginated collection; 1 when the server didn't report pagination. */
    fun lastPage(root: JSONObject): Int = root.optJSONObject("meta")?.optInt("last_page", 1) ?: 1

    // -- Memberships: GET /users/me/memberships --

    fun membership(json: JSONObject): STMembership {
        val org = json.optJSONObject("organization") ?: JSONObject()
        return STMembership(
            memberId = json.optString("id"),
            organizationId = org.optString("id"),
            organizationName = org.optString("name", org.optString("id")),
            role = json.optNullableString("role"),
        )
    }

    fun client(json: JSONObject): STClient = STClient(
        id = json.optString("id"),
        name = json.optString("name"),
        archived = json.optBoolean("is_archived", false),
    )

    fun project(json: JSONObject): STProject = STProject(
        id = json.optString("id"),
        name = json.optString("name"),
        color = json.optString("color").removePrefix("#"),
        clientId = json.optNullableString("client_id"),
        isBillable = json.optBoolean("is_billable", false),
        isArchived = json.optBoolean("is_archived", false),
        isPublic = json.optBoolean("is_public", false),
    )

    fun task(json: JSONObject): STTask = STTask(
        id = json.optString("id"),
        name = json.optString("name"),
        projectId = json.optString("project_id"),
        isDone = json.optBoolean("is_done", false) || json.optNullableString("done_at") != null,
    )

    fun tag(json: JSONObject): STTag = STTag(id = json.optString("id"), name = json.optString("name"))

    fun timeEntry(json: JSONObject): STTimeEntry {
        val tags = json.optJSONArray("tags") ?: JSONArray()
        return STTimeEntry(
            id = json.optString("id"),
            start = parseInstant(json.opt("start")) ?: 0L,
            end = parseInstant(json.opt("end")),
            durationSeconds = json.optInt("duration", 0),
            description = json.optNullableString("description").orEmpty(),
            projectId = json.optNullableString("project_id"),
            taskId = json.optNullableString("task_id"),
            billable = json.optBoolean("billable", false),
            tagIds = (0 until tags.length()).mapNotNull { tags.optString(it).takeIf { s -> s.isNotBlank() } },
            type = json.optString("type", "work"),
        )
    }
}
