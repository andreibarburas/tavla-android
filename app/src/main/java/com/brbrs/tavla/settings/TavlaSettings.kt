package com.brbrs.tavla.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * A link from a card to something in another BarburasLab app (#12).
 *
 * Contract other suite apps need to support for the link to open their exact screen rather than
 * just the app: a single ACTION_VIEW intent with this [uri] (e.g. "vinci://contact/42" or
 * "nota://note/17") handled by an <intent-filter> with that custom scheme. Tavla falls back to
 * just launching [appPackage] if no app declares that scheme.
 */
data class CrossAppLink(
    val id: String,
    val appPackage: String,
    val appLabel: String,
    val label: String,
    val uri: String,
)

/**
 * Everything Tavla needs that Deck has no field for. Kept in one small JSON file in the user's
 * own Nextcloud (see [NextcloudSettingsStore]) so it follows them to a new device; nothing here
 * is visible to anyone else a board is shared with.
 */
data class TavlaSettings(
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = true,
    val showArchivedBoards: Boolean = false,
    val showLabelsOnCards: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val remindersEnabled: Boolean = true,
    /** Minutes before a due date to notify, e.g. [60, 1440] for "1 hour" and "1 day" before. */
    val reminderOffsetsMinutes: List<Int> = listOf(60, 1440),
    /** Board id -> Material icon name from [com.brbrs.tavla.icons.IconCatalog]. */
    val boardIcons: Map<Long, String> = emptyMap(),
    /**
     * Board id -> filename of a local cover image under [com.brbrs.tavla.settings.BoardCovers].
     * The reference syncs via this file, but the image bytes stay on the device, so on another
     * device the file is absent and the board falls back to its icon or glyph.
     */
    val boardImages: Map<Long, String> = emptyMap(),
    /** Card id -> its cross-app links, newest last. */
    val cardLinks: Map<Long, List<CrossAppLink>> = emptyMap(),
    /** Board id -> stack id Tavla should treat as "Done" for that board (optional, per board). */
    val doneStacks: Map<Long, Long> = emptyMap(),
    /** "list" (default vertical list) or "card" (grid / card view). */
    val cardViewMode: String = "list",
    /** "comfortable" (current look) or "compact" (hides meta icons, tighter padding). */
    val cardDensity: String = "comfortable",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("themeMode", themeMode)
        put("textSize", textSize)
        put("useCustomFont", useCustomFont)
        put("showArchivedBoards", showArchivedBoards)
        put("showLabelsOnCards", showLabelsOnCards)
        put("syncIntervalMinutes", syncIntervalMinutes)
        put("remindersEnabled", remindersEnabled)
        put("reminderOffsetsMinutes", JSONArray(reminderOffsetsMinutes))
        put("boardIcons", JSONObject().apply { boardIcons.forEach { (k, v) -> put(k.toString(), v) } })
        put("boardImages", JSONObject().apply { boardImages.forEach { (k, v) -> put(k.toString(), v) } })
        put("doneStacks", JSONObject().apply { doneStacks.forEach { (k, v) -> put(k.toString(), v) } })
        put("cardViewMode", cardViewMode)
        put("cardDensity", cardDensity)
        put(
            "cardLinks",
            JSONObject().apply {
                cardLinks.forEach { (cardId, links) ->
                    put(
                        cardId.toString(),
                        JSONArray().apply {
                            links.forEach { l ->
                                put(
                                    JSONObject()
                                        .put("id", l.id)
                                        .put("appPackage", l.appPackage)
                                        .put("appLabel", l.appLabel)
                                        .put("label", l.label)
                                        .put("uri", l.uri),
                                )
                            }
                        },
                    )
                }
            },
        )
    }

    companion object {
        fun fromJson(json: JSONObject): TavlaSettings {
            val boardIcons = mutableMapOf<Long, String>()
            json.optJSONObject("boardIcons")?.let { obj ->
                obj.keys().forEach { k -> boardIcons[k.toLongOrNull() ?: return@forEach] = obj.getString(k) }
            }
            val boardImages = mutableMapOf<Long, String>()
            json.optJSONObject("boardImages")?.let { obj ->
                obj.keys().forEach { k -> boardImages[k.toLongOrNull() ?: return@forEach] = obj.getString(k) }
            }
            val doneStacks = mutableMapOf<Long, Long>()
            json.optJSONObject("doneStacks")?.let { obj ->
                obj.keys().forEach { k -> doneStacks[k.toLongOrNull() ?: return@forEach] = obj.getLong(k) }
            }
            val cardLinks = mutableMapOf<Long, List<CrossAppLink>>()
            json.optJSONObject("cardLinks")?.let { obj ->
                obj.keys().forEach { k ->
                    val cardId = k.toLongOrNull() ?: return@forEach
                    val arr = obj.optJSONArray(k) ?: return@forEach
                    cardLinks[cardId] = (0 until arr.length()).mapNotNull { i ->
                        val l = arr.optJSONObject(i) ?: return@mapNotNull null
                        CrossAppLink(
                            id = l.optString("id"),
                            appPackage = l.optString("appPackage"),
                            appLabel = l.optString("appLabel"),
                            label = l.optString("label"),
                            uri = l.optString("uri"),
                        )
                    }
                }
            }
            val offsets = json.optJSONArray("reminderOffsetsMinutes")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }
            } ?: listOf(60, 1440)
            return TavlaSettings(
                themeMode = json.optString("themeMode", "system"),
                textSize = json.optString("textSize", "default"),
                useCustomFont = json.optBoolean("useCustomFont", true),
                showArchivedBoards = json.optBoolean("showArchivedBoards", false),
                showLabelsOnCards = json.optBoolean("showLabelsOnCards", true),
                syncIntervalMinutes = json.optInt("syncIntervalMinutes", 60),
                remindersEnabled = json.optBoolean("remindersEnabled", true),
                reminderOffsetsMinutes = offsets,
                boardIcons = boardIcons,
                boardImages = boardImages,
                doneStacks = doneStacks,
                cardLinks = cardLinks,
                cardViewMode = json.optString("cardViewMode", "list"),
                cardDensity = json.optString("cardDensity", "comfortable"),
            )
        }
    }
}
