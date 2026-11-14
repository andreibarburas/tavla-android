package com.brbrs.tavla.solidtime

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

/** Thrown for any non-2xx Solidtime response; [code] is the HTTP status (0 for network failures). */
class SolidtimeApiException(val code: Int, message: String) : Exception(message)

/**
 * OkHttp + org.json client for the Solidtime REST API v1 (no Retrofit/Gson, per suite rules).
 * Auth is a Bearer personal access token. Base is "{serverUrl}/api/v1". Organization-scoped
 * endpoints take the organization id in the path; the signed-in user's member id (needed to
 * create time entries) comes from GET /users/me/memberships.
 *
 * Every call takes the server URL and token explicitly so the client stays stateless -- the
 * repository owns the credentials.
 */
@Singleton
class SolidtimeApi @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun base(serverUrl: String) = serverUrl.trimEnd('/') + "/api/v1"

    private suspend fun call(
        serverUrl: String,
        token: String,
        method: String,
        path: String,
        body: JSONObject? = null,
    ): String = withContext(Dispatchers.IO) {
        val reqBody = when {
            body != null -> body.toString().toRequestBody(jsonType)
            method == "POST" || method == "PUT" || method == "PATCH" -> "{}".toRequestBody(jsonType)
            else -> null
        }
        val request = Request.Builder()
            .url(base(serverUrl) + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, reqBody)
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw SolidtimeApiException(0, e.message ?: e.javaClass.simpleName)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { m -> m.isNotBlank() } ?: "HTTP ${it.code}"
                throw SolidtimeApiException(it.code, msg)
            }
            text
        }
    }

    /** Fetches every page of a paginated collection endpoint and returns the merged data array. */
    private suspend fun collectAll(serverUrl: String, token: String, pathNoPage: String): JSONArray {
        val merged = JSONArray()
        var page = 1
        val sep = if (pathNoPage.contains("?")) "&" else "?"
        while (page <= 50) { // hard cap so a misbehaving server can't loop forever
            val root = JSONObject(call(serverUrl, token, "GET", "$pathNoPage${sep}page=$page"))
            val data = SolidtimeJson.dataArray(root)
            for (i in 0 until data.length()) merged.put(data.get(i))
            if (page >= SolidtimeJson.lastPage(root)) break
            page++
        }
        return merged
    }

    // -- Connection / identity ---------------------------------------------------------------

    /** The signed-in user's memberships, independent of organization. Used to pick an org + member id. */
    suspend fun getMemberships(serverUrl: String, token: String): List<STMembership> {
        val root = JSONObject(call(serverUrl, token, "GET", "/users/me/memberships"))
        val arr = SolidtimeJson.dataArray(root)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::membership) }
    }

    // -- Projects / clients / tasks / tags ---------------------------------------------------

    suspend fun getProjects(serverUrl: String, token: String, orgId: String): List<STProject> {
        val arr = collectAll(serverUrl, token, "/organizations/$orgId/projects")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::project) }
    }

    suspend fun createProject(
        serverUrl: String, token: String, orgId: String,
        name: String, color: String, isBillable: Boolean, clientId: String?,
    ): STProject {
        val body = JSONObject()
            .put("name", name)
            .put("color", if (color.startsWith("#")) color else "#$color")
            .put("is_billable", isBillable)
            .put("is_public", false)
            .put("client_id", clientId ?: JSONObject.NULL) // 'present' rule: key must be sent
        val root = JSONObject(call(serverUrl, token, "POST", "/organizations/$orgId/projects", body))
        return SolidtimeJson.project(root.optJSONObject("data") ?: root)
    }

    suspend fun getClients(serverUrl: String, token: String, orgId: String): List<STClient> {
        val arr = collectAll(serverUrl, token, "/organizations/$orgId/clients")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::client) }
    }

    suspend fun getTasks(serverUrl: String, token: String, orgId: String): List<STTask> {
        val arr = collectAll(serverUrl, token, "/organizations/$orgId/tasks")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::task) }
    }

    suspend fun createTask(serverUrl: String, token: String, orgId: String, name: String, projectId: String): STTask {
        val body = JSONObject().put("name", name).put("project_id", projectId)
        val root = JSONObject(call(serverUrl, token, "POST", "/organizations/$orgId/tasks", body))
        return SolidtimeJson.task(root.optJSONObject("data") ?: root)
    }

    suspend fun getTags(serverUrl: String, token: String, orgId: String): List<STTag> {
        val arr = collectAll(serverUrl, token, "/organizations/$orgId/tags")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::tag) }
    }

    suspend fun createTag(serverUrl: String, token: String, orgId: String, name: String): STTag {
        val body = JSONObject().put("name", name)
        val root = JSONObject(call(serverUrl, token, "POST", "/organizations/$orgId/tags", body))
        return SolidtimeJson.tag(root.optJSONObject("data") ?: root)
    }

    // -- Time entries ------------------------------------------------------------------------

    /** The user's single running timer (across all orgs), or null if none is running (API 404). */
    suspend fun getActiveTimer(serverUrl: String, token: String): STTimeEntry? {
        return try {
            val root = JSONObject(call(serverUrl, token, "GET", "/users/me/time-entries/active"))
            root.optJSONObject("data")?.let(SolidtimeJson::timeEntry)
        } catch (e: SolidtimeApiException) {
            if (e.code == 404) null else throw e
        }
    }

    private fun timeEntryBody(
        memberId: String, start: Long, end: Long?, billable: Boolean,
        projectId: String?, taskId: String?, description: String, tagIds: List<String>,
    ): JSONObject = JSONObject().apply {
        put("member_id", memberId)
        put("start", SolidtimeJson.formatInstant(start))
        put("end", if (end == null) JSONObject.NULL else SolidtimeJson.formatInstant(end))
        put("billable", billable)
        put("type", "work")
        put("description", description)
        put("project_id", projectId ?: JSONObject.NULL)
        put("task_id", taskId ?: JSONObject.NULL)
        put("tags", JSONArray(tagIds))
    }

    suspend fun createTimeEntry(
        serverUrl: String, token: String, orgId: String,
        memberId: String, start: Long, end: Long?, billable: Boolean,
        projectId: String?, taskId: String?, description: String, tagIds: List<String>,
    ): STTimeEntry {
        val body = timeEntryBody(memberId, start, end, billable, projectId, taskId, description, tagIds)
        val root = JSONObject(call(serverUrl, token, "POST", "/organizations/$orgId/time-entries", body))
        return SolidtimeJson.timeEntry(root.optJSONObject("data") ?: root)
    }

    /** Updates a time entry. Sending the whole body (with [end] set) is how a running timer is stopped. */
    suspend fun updateTimeEntry(
        serverUrl: String, token: String, orgId: String, entryId: String,
        memberId: String, start: Long, end: Long?, billable: Boolean,
        projectId: String?, taskId: String?, description: String, tagIds: List<String>,
    ): STTimeEntry {
        val body = timeEntryBody(memberId, start, end, billable, projectId, taskId, description, tagIds)
        val root = JSONObject(call(serverUrl, token, "PUT", "/organizations/$orgId/time-entries/$entryId", body))
        return SolidtimeJson.timeEntry(root.optJSONObject("data") ?: root)
    }

    suspend fun deleteTimeEntry(serverUrl: String, token: String, orgId: String, entryId: String) {
        call(serverUrl, token, "DELETE", "/organizations/$orgId/time-entries/$entryId")
    }

    /** Recent entries for one project (used to show a card's tracked time). Newest first from the server. */
    suspend fun getTimeEntriesForProject(
        serverUrl: String, token: String, orgId: String, memberId: String, projectId: String,
    ): List<STTimeEntry> {
        val path = "/organizations/$orgId/time-entries?member_id=$memberId&project_ids[]=$projectId"
        val arr = collectAll(serverUrl, token, path)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SolidtimeJson::timeEntry) }
    }
}
