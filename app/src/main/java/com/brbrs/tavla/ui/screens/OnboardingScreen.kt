package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.R
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.glassCard
import com.brbrs.tavla.ui.viewmodels.OnboardingViewModel

/** First-run welcome. Shown once; afterwards it hands straight off to [onContinue].
 *  New users who tap "Get started" go to [onGetStarted] (mode picker). */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onGetStarted: () -> Unit = onContinue,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val isDark = LocalIsDark.current
    val done by viewModel.onboardingDone.collectAsState()

    // Set the instant "Get started" is tapped, so the markDone() write landing moments later
    // doesn't also trigger the returning-user effect below and race its own onGetStarted() nav.
    var navigatedFromButton by rememberSaveable { mutableStateOf(false) }

    // Returning / upgrading user who already completed onboarding — skip to AppLock
    LaunchedEffect(done) { if (done == true && !navigatedFromButton) onContinue() }
    if (done != false) return

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Image(
                painter = painterResource(R.drawable.tavla_logo),
                contentDescription = "Tavla",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.height(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your Nextcloud Deck boards, made for your phone.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Plan projects, chores and everything in between on kanban boards that live on your own server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OnboardingPoint(
                    Icons.Outlined.CloudOff, "Your boards, your server",
                    "Tavla talks only to your Nextcloud. No accounts, no tracking, no third-party servers.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.ViewColumn, "Works with Nextcloud Deck",
                    "Boards, lists, cards, labels, due dates, checklists and comments stay in sync with Deck on the web.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.OfflinePin, "Readable offline",
                    "Your boards are kept on this device, so they open instantly, even without a connection.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.Lock, "Open source & private",
                    "Free, GPLv3 and ad-free. Optional biometric app lock keeps your plans to yourself.", isDark,
                )
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = { navigatedFromButton = true; viewModel.markDone(); onGetStarted() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Get started", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, body: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
