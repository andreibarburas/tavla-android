package com.brbrs.tavla.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brbrs.tavla.ui.theme.DisplayPreferencesRepository
import com.brbrs.tavla.ui.theme.TavlaTheme
import com.brbrs.tavla.ui.theme.ThemeRepository
import com.brbrs.tavla.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var displayPrefs: DisplayPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        setContent {
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) { "light" -> false; "system" -> systemDark; else -> true }
            val display by displayPrefs.preferences.collectAsStateWithLifecycle(
                initialValue = com.brbrs.tavla.ui.theme.DisplayPreferences(),
            )
            TavlaTheme(
                isDark = isDark,
                isOled = themeMode == "oled",
                textScale = textSizeMultiplier(display.textSize),
                useCustomFont = display.useCustomFont,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShareScreen(
                        sharedText = sharedText,
                        onDone = { finish() },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}
