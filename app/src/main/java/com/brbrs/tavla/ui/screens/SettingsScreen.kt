package com.brbrs.tavla.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.R
import com.brbrs.tavla.di.APP_VERSION
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.glassCard
import com.brbrs.tavla.ui.theme.tavlaChip
import com.brbrs.tavla.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onSolidtime: () -> Unit,
    onConnectNextcloud: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importLocalBackup(it) }
    }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LaunchedEffect(uiState.loggedOut) { if (uiState.loggedOut) onLoggedOut() }
    LaunchedEffect(uiState.message) { uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    // Navigate to login when "Connect to Nextcloud" is tapped in local mode
    val doConnect by viewModel.connectNextcloud.collectAsStateWithLifecycle()
    LaunchedEffect(doConnect) { if (doConnect) { viewModel.consumeConnectNextcloud(); onConnectNextcloud() } }

    // Share backup ZIP when export finishes
    val backupFile by viewModel.localBackupResult.collectAsStateWithLifecycle()
    LaunchedEffect(backupFile) {
        backupFile?.let { file ->
            context.startActivity(Intent.createChooser(viewModel.localBackupShareIntentFor(file), "Share backup"))
            viewModel.consumeBackupResult()
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Tavla forgets this server and removes the offline copy of your boards from this device. " +
                "Everything stays on your Nextcloud.",
            confirmText = "Sign out",
            onDismiss = { confirmLogout = false },
            onConfirm = { confirmLogout = false; viewModel.logout() },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear offline copy?",
            body = "Boards are removed from this device and downloaded again from Nextcloud. Nothing on the server changes.",
            confirmText = "Clear",
            destructive = false,
            onDismiss = { confirmClear = false },
            onConfirm = { confirmClear = false; viewModel.clearCache() },
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(24.dp))

            // -- Account / Storage
            if (uiState.isLocalMode) {
                SettingsSectionLabel("Storage")
                Spacer(Modifier.height(8.dp))
                SettingsCard(isDark) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Local mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Boards are stored on this device only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TextButton(onClick = { viewModel.connectToNextcloud() }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to Nextcloud", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                SettingsSectionLabel("Account")
                Spacer(Modifier.height(8.dp))
                SettingsCard(isDark) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Connected to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(uiState.serverUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(uiState.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign out", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // -- Security
            SettingsSectionLabel("Security")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                SettingsToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "App lock",
                    subtitle = if (uiState.biometricAvailable) "Require biometrics or your screen lock when opening Tavla"
                               else "Set up a fingerprint, face unlock or screen lock on this device first",
                    checked = uiState.appLockEnabled,
                    enabled = uiState.biometricAvailable || uiState.appLockEnabled,
                    onCheckedChange = viewModel::setAppLock,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Display
            SettingsSectionLabel("Display")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    options = listOf("system" to "System", "light" to "Light", "dark" to "Dark", "oled" to "OLED Black"),
                    selected = uiState.themeMode,
                    isDark = isDark,
                    onSelect = viewModel::setThemeMode,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.TextFields,
                    title = "Text size",
                    options = listOf("small" to "Small", "default" to "Default", "large" to "Large", "extra_large" to "Extra Large"),
                    selected = uiState.textSize,
                    isDark = isDark,
                    onSelect = viewModel::setTextSize,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.FontDownload,
                        title = "DM Serif + Inter Tight",
                        subtitle = "Use the custom font pairing instead of system fonts",
                        checked = uiState.useCustomFont,
                        onCheckedChange = viewModel::setUseCustomFont,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsToggleRow(
                        icon = Icons.Outlined.LocalOffer,
                        title = "Labels on cards",
                        subtitle = "Show label chips on cards in the board view",
                        checked = uiState.showLabelsOnCards,
                        onCheckedChange = viewModel::setShowLabelsOnCards,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsToggleRow(
                        icon = Icons.Outlined.Archive,
                        title = "Archived boards",
                        subtitle = "List archived boards below your active ones",
                        checked = uiState.showArchivedBoards,
                        onCheckedChange = viewModel::setShowArchivedBoards,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.ViewAgenda,
                    title = "Card view",
                    options = listOf("list" to "List", "card" to "Card"),
                    selected = uiState.cardViewMode,
                    isDark = isDark,
                    onSelect = viewModel::setCardViewMode,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.TableRows,
                    title = "Card density",
                    options = listOf("comfortable" to "Comfortable", "compact" to "Compact"),
                    selected = uiState.cardDensity,
                    isDark = isDark,
                    onSelect = viewModel::setCardDensity,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Sync & reminders (#03, #04) — hidden in local mode
            if (!uiState.isLocalMode) {
            SettingsSectionLabel("Sync & reminders")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.CloudSync,
                    title = "Background sync",
                    options = listOf("15" to "15 min", "30" to "30 min", "60" to "1 hour", "180" to "3 hours"),
                    selected = uiState.syncIntervalMinutes.toString(),
                    isDark = isDark,
                    onSelect = { viewModel.setSyncIntervalMinutes(it.toInt()) },
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Due date reminders",
                        subtitle = if (uiState.notificationsPermitted) "A local notification before a card is due"
                                   else "Notifications are off for Tavla in your phone's settings",
                        checked = uiState.remindersEnabled,
                        enabled = uiState.notificationsPermitted,
                        onCheckedChange = viewModel::setRemindersEnabled,
                    )
                    if (uiState.remindersEnabled && uiState.notificationsPermitted) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Remind me", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(60 to "1 hour before", 1440 to "1 day before").forEach { (minutes, label) ->
                                    val isSelected = minutes in uiState.reminderOffsetsMinutes
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .tavlaChip(isDark = isDark, selected = isSelected)
                                            .clickable { viewModel.toggleReminderOffset(minutes) }
                                            .padding(vertical = 10.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (uiState.needsExactAlarmPermission) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ActionRow(
                            icon = Icons.Outlined.Alarm,
                            title = "Precise reminders",
                            subtitle = "Allow alarms so reminders arrive exactly on time",
                            action = "Allow",
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            } // end if (!isLocalMode) — Sync & reminders

            Spacer(Modifier.height(24.dp))

            // -- Integrations
            SettingsSectionLabel("Integrations")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ActionRow(
                    icon = Icons.Outlined.Timer,
                    title = "Solidtime",
                    subtitle = "Track time on cards into your own Solidtime",
                    action = "Open",
                    onAction = onSolidtime,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Data
            SettingsSectionLabel("Data")
            Spacer(Modifier.height(8.dp))
            if (uiState.isLocalMode) {
                SettingsCard(isDark) {
                    Column {
                        ActionRow(
                            icon = Icons.Outlined.FileUpload,
                            title = "Back up to file",
                            subtitle = "Export all boards, lists and cards as a ZIP",
                            busy = uiState.isSyncing, // reuse for export spinner
                            action = "Export",
                            onAction = viewModel::exportLocalBackup,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ActionRow(
                            icon = Icons.Outlined.FileDownload,
                            title = "Restore from backup",
                            subtitle = "Import boards from a Tavla backup ZIP",
                            action = "Import",
                            onAction = { importPicker.launch("application/zip") },
                        )
                    }
                }
            } else {
                SettingsCard(isDark) {
                    Column {
                        ActionRow(
                            icon = Icons.Outlined.CloudSync,
                            title = "Sync all boards",
                            subtitle = "Download the latest boards, lists and cards from Nextcloud",
                            busy = uiState.isSyncing,
                            action = "Sync",
                            onAction = viewModel::syncNow,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ActionRow(
                            icon = Icons.Outlined.CleaningServices,
                            title = "Clear offline copy",
                            subtitle = "Remove boards stored on this device. Nextcloud is untouched",
                            action = "Clear",
                            onAction = { confirmClear = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // -- Support
            SettingsSectionLabel("Support")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Favorite),
                        title = "Buy me a coffee",
                        subtitle = "If Tavla keeps your plans on track, a small tip means a lot",
                    ) { open("https://bunq.me/barburasdonations?description=Donation%20from%20Tavla") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Language),
                        title = "More by andrei BARBURAS",
                        subtitle = "See what else I'm building at barburas.com",
                    ) { open("https://barburas.com") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.PrivacyTip),
                        title = "Privacy Policy",
                        subtitle = "How Tavla handles your data",
                    ) { open("https://barburas.com/privacy-policy/") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Code),
                        title = "View on GitHub",
                        subtitle = "Feedback, feature requests and source code",
                    ) { open("https://github.com/andreibarburas/android-apps") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = painterResource(R.drawable.si_reddit),
                        title = "Join r/BarburasLab",
                        subtitle = "Discuss Tavla and my other apps on Reddit",
                    ) { open("https://www.reddit.com/r/BarburasLab/") }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "Tavla $APP_VERSION · by andrei BARBURAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    // label-medium per the suite design system (uppercase, accent at 60% alpha) -- was labelSmall.
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f),
    ) { content() }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = MaterialTheme.colorScheme.primary,
                checkedTrackColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ChipGroup(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    isDark: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tavlaChip(isDark = isDark, selected = isSelected)
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    busy: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun LinkRow(painter: Painter, title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(painter = painter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
