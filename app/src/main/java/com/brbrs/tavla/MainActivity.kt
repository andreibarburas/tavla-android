package com.brbrs.tavla

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.settings.SettingsRepository
import com.brbrs.tavla.solidtime.SolidtimeLinksSync
import com.brbrs.tavla.sync.WorkScheduler
import com.brbrs.tavla.ui.TavlaNavGraph
import com.brbrs.tavla.ui.theme.DisplayPreferences
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import com.brbrs.tavla.ui.theme.TavlaTheme
import com.brbrs.tavla.ui.theme.ThemeRepository
import com.brbrs.tavla.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// FragmentActivity (not ComponentActivity) is required for BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var displayPrefs: DisplayPreferencesRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var solidtimeLinksSync: SolidtimeLinksSync
    @Inject lateinit var deckRepository: DeckRepository

    // Timer-notification deep link: (boardId, cardId) to open, consumed once by the nav graph.
    private val deepLink = MutableStateFlow<Pair<Long, Long>?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* #04: nothing to do either way -- reminders just won't show until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDeepLink(intent)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // #03/#13: once signed in, keep background sync running at the chosen interval and pull
        // in any settings saved from another device.
        lifecycleScope.launch {
            if (authRepository.currentSession() != null && !authRepository.isLocalMode()) {
                settingsRepository.fetchFromRemote()
                solidtimeLinksSync.fetchFromRemote()
                val minutes = settingsRepository.settings.first().syncIntervalMinutes
                workScheduler.scheduleSync(minutes)
            }
        }

        setContent {
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light"  -> false
                "system" -> systemDark
                else     -> true // "dark" / "oled"
            }
            val isOled = themeMode == "oled"
            val display by displayPrefs.preferences.collectAsStateWithLifecycle(initialValue = DisplayPreferences())
            TavlaTheme(
                isDark = isDark,
                isOled = isOled,
                textScale = textSizeMultiplier(display.textSize),
                useCustomFont = display.useCustomFont,
            ) {
                val open by deepLink.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    TavlaNavGraph(
                        openBoardId = open?.first,
                        openCardId = open?.second,
                        onDeepLinkConsumed = { deepLink.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent?) {
        val boardId = intent?.getLongExtra(EXTRA_OPEN_BOARD_ID, -1L) ?: -1L
        val cardId = intent?.getLongExtra(EXTRA_OPEN_CARD_ID, -1L) ?: -1L
        if (boardId >= 0 && cardId >= 0) {
            deepLink.value = boardId to cardId
            return
        }

        // Cross-app deep link: tavla://card/{cardId}, sent by Vinci (or another suite app) from
        // its "Linked items". Only the card's own stable id travels across apps -- the board it
        // lives on has to be looked up locally before the nav graph (which routes on both) can
        // open it.
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (uri.scheme != "tavla" || uri.host != "card") return
        val linkedCardId = uri.lastPathSegment?.toLongOrNull() ?: return
        lifecycleScope.launch {
            val linkedBoardId = deckRepository.boardIdForCard(linkedCardId)
            if (linkedBoardId != null) deepLink.value = linkedBoardId to linkedCardId
        }
    }

    companion object {
        const val EXTRA_OPEN_BOARD_ID = "open_board_id"
        const val EXTRA_OPEN_CARD_ID = "open_card_id"
    }
}
