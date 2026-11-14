package com.brbrs.tavla.ui.theme

import com.brbrs.tavla.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "system", "light", "dark", or "oled". A thin facade over [SettingsRepository] -- the setting
 * itself now lives in the user's Nextcloud (#13) so it follows them to a new device, but every
 * other screen still reads/writes it through this same small API.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val themeMode: Flow<String> = settingsRepository.settings.map { it.themeMode }

    suspend fun setThemeMode(mode: String) = settingsRepository.setThemeMode(mode)

    /** Top-bar toggle: system -> light -> dark -> oled -> system, matching the suite design
     *  system's theme-toggle order (OLED is a genuine fourth option, not just a Settings-only
     *  dark-theme variant). */
    suspend fun cycleThemeMode() {
        val next = when (themeMode.first()) {
            "system" -> "light"
            "light"  -> "dark"
            "dark"   -> "oled"
            else     -> "system"
        }
        setThemeMode(next)
    }
}
