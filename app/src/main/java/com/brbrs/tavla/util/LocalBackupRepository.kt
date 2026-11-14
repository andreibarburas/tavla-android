package com.brbrs.tavla.util

import android.content.Context
import com.brbrs.tavla.data.BoardEntity
import com.brbrs.tavla.data.CardEntity
import com.brbrs.tavla.data.DeckDao
import com.brbrs.tavla.data.StackEntity
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tavla-native ZIP export / import for phone-migration scenarios (local mode).
 *
 * ZIP layout:
 *   tavla_backup.json  — boards, stacks & cards as a JSON object
 *   settings.json      — display preferences key/value map
 */
@Singleton
class LocalBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deckDao: DeckDao,
    private val displayPrefs: DisplayPreferencesRepository,
) {

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
    )

    // ─── Export ─────────────────────────────────────────────────────────────

    suspend fun export(): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(context.cacheDir, "tavla_backup_$stamp.zip")

            ZipOutputStream(BufferedOutputStream(outFile.outputStream())).use { zos ->
                zos.putNextEntry(ZipEntry("tavla_backup.json"))
                zos.write(buildDataJson().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(buildSettingsJson().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            outFile
        }
    }

    private suspend fun buildDataJson(): String {
        val boards = deckDao.getAllBoardsOnce()
        val stacks = deckDao.getAllStacksOnce()
        val cards = deckDao.getAllCardsOnce()

        val root = JSONObject()

        val boardsArr = JSONArray()
        boards.forEach { b ->
            boardsArr.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("color", b.color)
                put("archived", b.archived)
                put("ownerUid", b.ownerUid)
                put("labelsJson", b.labelsJson)
                put("usersJson", b.usersJson)
                put("canEdit", b.canEdit)
                put("canManage", b.canManage)
                put("isShared", b.isShared)
                put("lastModified", b.lastModified)
            })
        }
        root.put("boards", boardsArr)

        val stacksArr = JSONArray()
        stacks.forEach { s ->
            stacksArr.put(JSONObject().apply {
                put("id", s.id)
                put("boardId", s.boardId)
                put("title", s.title)
                put("sortOrder", s.sortOrder)
            })
        }
        root.put("stacks", stacksArr)

        val cardsArr = JSONArray()
        cards.forEach { c ->
            cardsArr.put(JSONObject().apply {
                put("id", c.id)
                put("boardId", c.boardId)
                put("stackId", c.stackId)
                put("title", c.title)
                put("description", c.description)
                put("sortOrder", c.sortOrder)
                put("dueDate", c.dueDate ?: JSONObject.NULL)
                put("doneAt", c.doneAt ?: JSONObject.NULL)
                put("createdAt", c.createdAt ?: JSONObject.NULL)
                put("archived", c.archived)
                put("ownerUid", c.ownerUid)
                put("labelsJson", c.labelsJson)
                put("assigneesJson", c.assigneesJson)
                put("commentsCount", c.commentsCount)
                put("attachmentCount", c.attachmentCount)
                put("lastModified", c.lastModified)
            })
        }
        root.put("cards", cardsArr)

        return root.toString(2)
    }

    private suspend fun buildSettingsJson(): String {
        val prefs = displayPrefs.preferences.first()
        return JSONObject().apply {
            put("text_size", prefs.textSize)
            put("use_custom_font", prefs.useCustomFont)
            put("show_archived_boards", prefs.showArchivedBoards)
            put("show_labels_on_cards", prefs.showLabelsOnCards)
            put("card_view_mode", prefs.cardViewMode)
            put("card_density", prefs.cardDensity)
        }.toString(2)
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    suspend fun import(zipFile: File): Result<ImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            var dataJson: String? = null
            var settingsJson: String? = null

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "tavla_backup.json" -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                        "settings.json" -> settingsJson = zis.readBytes().toString(Charsets.UTF_8)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Restore settings first (non-fatal if absent)
            settingsJson?.let { restoreSettings(it) }

            // Restore data
            val json = dataJson
                ?: throw IllegalArgumentException("tavla_backup.json not found in ZIP")

            importData(JSONObject(json))
        }
    }

    private suspend fun restoreSettings(json: String) {
        runCatching {
            val obj = JSONObject(json)
            val map = buildMap<String, Any> {
                if (obj.has("text_size")) put("text_size", obj.getString("text_size"))
                if (obj.has("use_custom_font")) put("use_custom_font", obj.getBoolean("use_custom_font"))
                if (obj.has("show_archived_boards")) put("show_archived_boards", obj.getBoolean("show_archived_boards"))
                if (obj.has("show_labels_on_cards")) put("show_labels_on_cards", obj.getBoolean("show_labels_on_cards"))
                if (obj.has("card_view_mode")) put("card_view_mode", obj.getString("card_view_mode"))
                if (obj.has("card_density")) put("card_density", obj.getString("card_density"))
            }
            displayPrefs.fromMap(map)
        }
    }

    private suspend fun importData(root: JSONObject): ImportResult {
        var imported = 0
        var skipped = 0

        // Import boards
        val boardsArr = root.optJSONArray("boards") ?: JSONArray()
        for (i in 0 until boardsArr.length()) {
            val obj = boardsArr.getJSONObject(i)
            val id = obj.getLong("id")
            if (deckDao.getBoard(id) != null) { skipped++; continue }
            deckDao.upsertBoards(listOf(BoardEntity(
                id = id,
                title = obj.getString("title"),
                color = obj.optString("color", ""),
                archived = obj.optBoolean("archived", false),
                ownerUid = obj.optString("ownerUid", ""),
                labelsJson = obj.optString("labelsJson", "[]"),
                usersJson = obj.optString("usersJson", "[]"),
                canEdit = obj.optBoolean("canEdit", true),
                canManage = obj.optBoolean("canManage", true),
                isShared = obj.optBoolean("isShared", false),
                lastModified = obj.optLong("lastModified", 0L),
            )))
            imported++
        }

        // Import stacks
        val stacksArr = root.optJSONArray("stacks") ?: JSONArray()
        val stacksToInsert = mutableListOf<StackEntity>()
        for (i in 0 until stacksArr.length()) {
            val obj = stacksArr.getJSONObject(i)
            stacksToInsert.add(StackEntity(
                id = obj.getLong("id"),
                boardId = obj.getLong("boardId"),
                title = obj.getString("title"),
                sortOrder = obj.optInt("sortOrder", 0),
            ))
        }
        if (stacksToInsert.isNotEmpty()) deckDao.upsertStacks(stacksToInsert)

        // Import cards
        val cardsArr = root.optJSONArray("cards") ?: JSONArray()
        val cardsToInsert = mutableListOf<CardEntity>()
        for (i in 0 until cardsArr.length()) {
            val obj = cardsArr.getJSONObject(i)
            val id = obj.getLong("id")
            if (deckDao.getCard(id) != null) { skipped++; continue }
            cardsToInsert.add(CardEntity(
                id = id,
                boardId = obj.getLong("boardId"),
                stackId = obj.getLong("stackId"),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                sortOrder = obj.optInt("sortOrder", 0),
                dueDate = if (obj.isNull("dueDate")) null else obj.optLong("dueDate"),
                doneAt = if (obj.isNull("doneAt")) null else obj.optLong("doneAt"),
                createdAt = if (obj.isNull("createdAt")) null else obj.optLong("createdAt"),
                archived = obj.optBoolean("archived", false),
                ownerUid = obj.optString("ownerUid", ""),
                labelsJson = obj.optString("labelsJson", "[]"),
                assigneesJson = obj.optString("assigneesJson", "[]"),
                commentsCount = obj.optInt("commentsCount", 0),
                attachmentCount = obj.optInt("attachmentCount", 0),
                lastModified = obj.optLong("lastModified", 0L),
            ))
            imported++
        }
        if (cardsToInsert.isNotEmpty()) deckDao.upsertCards(cardsToInsert)

        return ImportResult(imported, skipped)
    }
}
