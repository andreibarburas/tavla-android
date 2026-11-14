package com.brbrs.tavla.ui.theme

import com.brbrs.tavla.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayPreferences(
    /** "small", "default", "large", or "extra_large". */
    val textSize: String = "default",
    /** DM Serif Display + Inter Tight pairing (default) vs system fonts. */
    val useCustomFont: Boolean = true,
    /** Show archived boards at the bottom of the board list. */
    val showArchivedBoards: Boolean = false,
    /** Show label chips on cards in the board view. */
    val showLabelsOnCards: Boolean = true,
    /** "list" or "card". */
    val cardViewMode: String = "list",
    /** "comfortable" or "compact". */
    val cardDensity: String = "comfortable",
)

fun textSizeMultiplier(textSize: String): Float = when (textSize) {
    "small"       -> 0.9f
    "large"       -> 1.15f
    "extra_large" -> 1.3f
    else          -> 1.0f
}

/** Thin facade over [SettingsRepository] -- see its kdoc. Kept so existing screens don't change. */
@Singleton
class DisplayPreferencesRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val preferences: Flow<DisplayPreferences> = settingsRepository.settings.map { s ->
        DisplayPreferences(
            textSize = s.textSize,
            useCustomFont = s.useCustomFont,
            showArchivedBoards = s.showArchivedBoards,
            showLabelsOnCards = s.showLabelsOnCards,
            cardViewMode = s.cardViewMode,
            cardDensity = s.cardDensity,
        )
    }

    suspend fun setTextSize(size: String) = settingsRepository.setTextSize(size)
    suspend fun setUseCustomFont(enabled: Boolean) = settingsRepository.setUseCustomFont(enabled)
    suspend fun setShowArchivedBoards(enabled: Boolean) = settingsRepository.setShowArchivedBoards(enabled)
    suspend fun setShowLabelsOnCards(enabled: Boolean) = settingsRepository.setShowLabelsOnCards(enabled)
    suspend fun setCardViewMode(mode: String) = settingsRepository.setCardViewMode(mode)
    suspend fun setCardDensity(density: String) = settingsRepository.setCardDensity(density)

    /** Restore display preferences from a backup map (keys match the settings JSON keys). */
    suspend fun fromMap(map: Map<String, Any>) {
        (map["text_size"] as? String)?.let { setTextSize(it) }
        (map["use_custom_font"] as? Boolean)?.let { setUseCustomFont(it) }
        (map["show_archived_boards"] as? Boolean)?.let { setShowArchivedBoards(it) }
        (map["show_labels_on_cards"] as? Boolean)?.let { setShowLabelsOnCards(it) }
        (map["card_view_mode"] as? String)?.let { setCardViewMode(it) }
        (map["card_density"] as? String)?.let { setCardDensity(it) }
    }
}
