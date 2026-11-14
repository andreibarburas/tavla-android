package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.solidtime.STProject
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.components.colorFromHex
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.glassCard
import com.brbrs.tavla.ui.theme.tavlaChip
import com.brbrs.tavla.ui.viewmodels.SolidtimeBoardViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SolidtimeBoardScreen(
    onBack: () -> Unit,
    viewModel: SolidtimeBoardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    var confirmUnlink by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    if (confirmUnlink) {
        ConfirmDialog(
            title = "Unlink this board?",
            body = "Time already tracked stays in Solidtime. The board just stops showing timers. Nothing is deleted.",
            confirmText = "Unlink",
            onDismiss = { confirmUnlink = false },
            onConfirm = { confirmUnlink = false; viewModel.unlink() },
        )
    }
    if (showCreate) {
        CreateProjectDialog(
            defaultName = ui.boardTitle,
            onDismiss = { showCreate = false },
            onConfirm = { name, billable -> viewModel.createAndLink(name, "3BB8C8", billable, null); showCreate = false },
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { onBack() }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Solidtime", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(ui.boardTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                !ui.connected -> NotConnectedNotice()
                ui.isLinked -> LinkedContent(ui, isDark, viewModel, onChange = { changing = true; viewModel.loadProjects() }, onUnlink = { confirmUnlink = true })
                else -> UnlinkedContent(ui, isDark, viewModel, onCreate = { showCreate = true })
            }

            // "Change project" picker over an already-linked board
            if (changing && ui.isLinked) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Pick a different project")
                Spacer(Modifier.height(8.dp))
                ProjectPicker(ui, isDark, currentProjectId = ui.link?.projectId) { viewModel.linkTo(it); changing = false }
            }

            Spacer(Modifier.height(24.dp).then(Modifier.navigationBarsPadding()))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
    }
}

@Composable
private fun NotConnectedNotice() {
    Text(
        "Connect Solidtime first in Settings, then come back here to link this board to a project.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun card(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun UnlinkedContent(
    ui: com.brbrs.tavla.ui.viewmodels.SolidtimeBoardUiState,
    isDark: Boolean,
    vm: SolidtimeBoardViewModel,
    onCreate: () -> Unit,
) {
    Text(
        "Link this board to one Solidtime project. Timers started on its cards log time to that project.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp, tint = if (isDark) Color.White else Color.Black)
            .clickable(onClick = onCreate).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Create a new project", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("Named \"${ui.boardTitle}\" in Solidtime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel("Or pick an existing project")
    Spacer(Modifier.height(8.dp))
    ProjectPicker(ui, isDark, currentProjectId = null) { vm.linkTo(it) }
}

@Composable
private fun ProjectPicker(
    ui: com.brbrs.tavla.ui.viewmodels.SolidtimeBoardUiState,
    isDark: Boolean,
    currentProjectId: String?,
    onPick: (STProject) -> Unit,
) {
    if (ui.loadingProjects) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    if (ui.projects.isEmpty()) {
        Text("No projects in this organization yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    // Pre-highlight a project whose name matches the board title.
    val suggestedId = ui.projects.firstOrNull { it.name.equals(ui.boardTitle, ignoreCase = true) }?.id
    card(isDark) {
        Column {
            ui.projects.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colorFromHex(p.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.id == suggestedId && currentProjectId == null) {
                                Spacer(Modifier.width(8.dp))
                                Text("match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        val client = p.clientId?.let { ui.clientNames[it] }
                        val meta = listOfNotNull(client, if (p.isBillable) "billable" else null).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.id == currentProjectId) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedContent(
    ui: com.brbrs.tavla.ui.viewmodels.SolidtimeBoardUiState,
    isDark: Boolean,
    vm: SolidtimeBoardViewModel,
    onChange: () -> Unit,
    onUnlink: () -> Unit,
) {
    val link = ui.link ?: return
    SectionLabel("Linked project")
    Spacer(Modifier.height(8.dp))
    card(isDark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(colorFromHex(link.projectColor)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(link.projectName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                val meta = listOfNotNull(link.clientName, if (link.projectBillable) "billable by default" else null).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel("Per-board options")
    Spacer(Modifier.height(8.dp))
    card(isDark) {
        Column {
            ToggleRow(
                title = "Create a task per card",
                subtitle = "Also make a Solidtime task named after the card and attach its entries",
                checked = link.createTasks, onCheckedChange = vm::setCreateTasks,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ToggleRow(
                title = "Tag entries with the list name",
                subtitle = "E.g. entries from a \"Bugs\" list get a \"Bugs\" tag",
                checked = link.useListTag, onCheckedChange = vm::setUseListTag,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("Auto-stop list")
    Spacer(Modifier.height(8.dp))
    Text(
        "Moving a card into this list stops its running timer.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneChip("None", selected = link.doneStackId == null, isDark) { vm.setDoneStack(null) }
        ui.stacks.forEach { s ->
            DoneChip(s.title, selected = link.doneStackId == s.id, isDark) { vm.setDoneStack(s.id) }
        }
    }

    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onChange, modifier = Modifier.weight(1f)) { Text("Change project", color = MaterialTheme.colorScheme.primary) }
        TextButton(onClick = onUnlink, modifier = Modifier.weight(1f)) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DoneChip(label: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.tavlaChip(isDark, selected).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun CreateProjectDialog(defaultName: String, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var billable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Solidtime project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Project name") },
                    singleLine = true, keyboardOptions = KeyboardOptions(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Billable by default", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = billable, onCheckedChange = { billable = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), billable) }, enabled = name.isNotBlank()) { Text("Create & link", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
