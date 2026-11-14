package com.brbrs.tavla.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.glassCard
import com.brbrs.tavla.ui.viewmodels.SolidtimeSettingsViewModel

@Composable
fun SolidtimeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SolidtimeSettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect Solidtime?",
            body = "Tavla forgets the token and removes every board link and tracked-time mapping on this device. Nothing changes in Solidtime itself.",
            confirmText = "Disconnect",
            onDismiss = { confirmDisconnect = false },
            onConfirm = { confirmDisconnect = false; viewModel.disconnect() },
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp, top = 8.dp)) {
                TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { onBack() }
                Text("Solidtime", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(Modifier.height(16.dp))

            when {
                ui.connected -> ConnectedContent(ui, isDark, viewModel) { confirmDisconnect = true }
                ui.memberships.isNotEmpty() -> OrgPicker(ui, isDark, viewModel)
                else -> ConnectForm(ui, isDark, viewModel)
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Card(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun ConnectForm(ui: com.brbrs.tavla.ui.viewmodels.SolidtimeSettingsUiState, isDark: Boolean, vm: SolidtimeSettingsViewModel) {
    Text(
        "Track time on cards straight into your own Solidtime. Paste a personal access token from Solidtime, under Settings, API.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Card(isDark) {
        Column {
            OutlinedTextField(
                value = ui.urlInput,
                onValueChange = vm::onUrlChange,
                label = { Text("Solidtime server URL") },
                placeholder = { Text("https://solidtime.yourdomain.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.tokenInput,
                onValueChange = vm::onTokenChange,
                label = { Text("Personal access token") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::connect,
                enabled = !ui.connecting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (ui.connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Connect", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun UpdateTokenForm(ui: com.brbrs.tavla.ui.viewmodels.SolidtimeSettingsUiState, isDark: Boolean, vm: SolidtimeSettingsViewModel) {
    SectionLabel("Update token")
    Spacer(Modifier.height(8.dp))
    Card(isDark) {
        Column {
            Text(
                "Paste the new personal access token from Solidtime. Your board links, per-card " +
                    "projects and tracked time all stay exactly as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.urlInput,
                onValueChange = vm::onUrlChange,
                label = { Text("Solidtime server URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.tokenInput,
                onValueChange = vm::onTokenChange,
                label = { Text("New personal access token") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = vm::cancelUpdateToken, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = vm::updateToken,
                    enabled = !ui.connecting,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (ui.connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}

@Composable
private fun OrgPicker(ui: com.brbrs.tavla.ui.viewmodels.SolidtimeSettingsUiState, isDark: Boolean, vm: SolidtimeSettingsViewModel) {
    SectionLabel("Choose organization")
    Spacer(Modifier.height(8.dp))
    Card(isDark) {
        Column {
            ui.memberships.forEachIndexed { i, m ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectOrganization(m) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.organizationName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        m.role?.let { Text(it.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    ui: com.brbrs.tavla.ui.viewmodels.SolidtimeSettingsUiState,
    isDark: Boolean,
    vm: SolidtimeSettingsViewModel,
    onDisconnect: () -> Unit,
) {
    SectionLabel("Connected")
    Spacer(Modifier.height(8.dp))
    Card(isDark) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ui.organizationName ?: "Solidtime", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(ui.serverUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ui.tokenEncrypted) Icons.Outlined.Lock else Icons.Outlined.Warning, null,
                    tint = if (ui.tokenEncrypted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (ui.tokenEncrypted) "Token stored encrypted on this device" else "Token stored unencrypted (secure storage unavailable)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ui.tokenEncrypted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            if (ui.pendingCount > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.pendingCount} change(s) waiting to sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = vm::testConnection, enabled = !ui.testing, modifier = Modifier.weight(1f)) {
            if (ui.testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else Text("Test connection", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
            Text("Disconnect", color = MaterialTheme.colorScheme.error)
        }
    }
    if (!ui.showUpdateToken) {
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = vm::startUpdateToken, modifier = Modifier.fillMaxWidth()) {
            Text("Token expired? Update it", color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Spacer(Modifier.height(12.dp))
        UpdateTokenForm(ui, isDark, vm)
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Link a board to a Solidtime project from the board's menu, then start timers on its cards.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
