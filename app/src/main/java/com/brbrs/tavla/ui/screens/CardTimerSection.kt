package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brbrs.tavla.solidtime.STProject
import com.brbrs.tavla.solidtime.STTimeEntry
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.viewmodels.CardTimerState
import com.brbrs.tavla.util.formatDuration
import com.brbrs.tavla.util.formatElapsedClock
import com.brbrs.tavla.util.formatTimestamp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The card's Solidtime time-tracking section. Only rendered when the card's board is linked to a
 * project. Shows a start form (editable description + billable) when idle, a live clock + Stop
 * while running, a note + Stop when a timer is running on another card, and the list of this
 * card's tracked entries with manual add / edit / delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardTimerSection(
    timer: CardTimerState,
    defaultDescription: String,
    canEdit: Boolean,
    onStart: (description: String, billable: Boolean) -> Unit,
    onStop: () -> Unit,
    onAddEntry: (start: Long, end: Long, description: String, billable: Boolean) -> Unit,
    onEditEntry: (entry: STTimeEntry, start: Long, end: Long, description: String, billable: Boolean) -> Unit,
    onDeleteEntry: (STTimeEntry) -> Unit,
    onLoadProjects: ((List<STProject>) -> Unit) -> Unit = {},
    onSetCardProject: (STProject, String?) -> Unit = { _, _ -> },
    onClearCardProject: () -> Unit = {},
) {
    if (!timer.linked) return
    val isDark = LocalIsDark.current
    var editorFor by remember { mutableStateOf<EntryEditorTarget?>(null) }
    var deleting by remember { mutableStateOf<STTimeEntry?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var projects by remember { mutableStateOf<List<STProject>?>(null) }

    if (showProjectPicker) {
        ProjectPickerDialog(
            projects = projects,
            currentOverrideName = timer.overrideProjectName,
            boardProjectName = timer.boardProjectName,
            onDismiss = { showProjectPicker = false; projects = null },
            onSelect = { project -> onSetCardProject(project, null); showProjectPicker = false; projects = null },
            onClear = { onClearCardProject(); showProjectPicker = false; projects = null },
        )
    }

    editorFor?.let { target ->
        EntryEditorDialog(
            target = target,
            defaultDescription = defaultDescription,
            billableDefault = timer.billableDefault,
            onDismiss = { editorFor = null },
            onConfirm = { start, end, desc, billable ->
                when (target) {
                    is EntryEditorTarget.New -> onAddEntry(start, end, desc, billable)
                    is EntryEditorTarget.Edit -> onEditEntry(target.entry, start, end, desc, billable)
                }
                editorFor = null
            },
        )
    }
    deleting?.let { e ->
        com.brbrs.tavla.ui.components.ConfirmDialog(
            title = "Delete this entry?",
            body = "It is removed from Solidtime.",
            confirmText = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { onDeleteEntry(e); deleting = null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Time tracking", modifier = Modifier.weight(1f), trailing = timer.projectName)
            if (timer.working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        }

        // Per-card project override row
        if (canEdit) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(
                        if (timer.hasOverride) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    .clickable {
                        showProjectPicker = true
                        onLoadProjects { projects = it }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (timer.hasOverride) {
                        Text(
                            "Project: ${timer.overrideProjectName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Overrides board default (${timer.boardProjectName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Project: ${timer.projectName ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Tap to override for this card",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        when {
            timer.runningThisCard -> RunningCard(timer, isDark, onStop)
            timer.running != null -> OtherTimerCard(isDark, onStop)
            canEdit -> StartCard(defaultDescription, timer.billableDefault, timer.working, isDark, onStart)
        }

        // Total + entries (completed only; the running one is shown above)
        val completed = timer.entries.filter { it.end != null }
        if (completed.isNotEmpty() || timer.totalSeconds > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total tracked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(formatDuration(timer.totalSeconds), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            completed.forEach { entry ->
                EntryRow(
                    entry = entry, isDark = isDark,
                    onClick = { if (canEdit) editorFor = EntryEditorTarget.Edit(entry) },
                    onLongClick = { if (canEdit) deleting = entry },
                )
            }
        }
        if (canEdit) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { editorFor = EntryEditorTarget.New }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add time entry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RunningCard(timer: CardTimerState, isDark: Boolean, onStop: () -> Unit) {
    val running = timer.running ?: return
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running.entryId) {
        while (true) { nowMs = System.currentTimeMillis(); delay(1000) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                formatElapsedClock(nowMs - running.startMs),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (running.isLocalOnly) "Tracking · will sync when online" else "Tracking now",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StopButton(onStop)
    }
}

@Composable
private fun OtherTimerCard(isDark: Boolean, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().tavlaCard(isDark, cornerRadius = 16.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("A timer is running elsewhere", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Solidtime allows one at a time. Stop it to start here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StopButton(onStop)
    }
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            .clickable(onClick = onStop),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Stop, "Stop timer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun StartCard(
    defaultDescription: String, billableDefault: Boolean, working: Boolean, isDark: Boolean,
    onStart: (String, Boolean) -> Unit,
) {
    var description by remember { mutableStateOf(defaultDescription) }
    var billable by remember { mutableStateOf(billableDefault) }
    Column(
        modifier = Modifier.fillMaxWidth().tavlaCard(isDark, cornerRadius = 16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("What are you working on?") }, singleLine = false, maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Billable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = billable, onCheckedChange = { billable = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !working) { onStart(description.trim().ifBlank { defaultDescription }, billable) },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Start timer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: STTimeEntry, isDark: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val durationSec = entry.end?.let { ((it - entry.start) / 1000).toInt() } ?: entry.durationSeconds
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.description.ifBlank { "(no description)" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTimestamp(entry.start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDuration(durationSec), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- Manual entry editor ----------------------------------------------------------------------

private sealed interface EntryEditorTarget {
    data object New : EntryEditorTarget
    data class Edit(val entry: STTimeEntry) : EntryEditorTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorDialog(
    target: EntryEditorTarget,
    defaultDescription: String,
    billableDefault: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long, description: String, billable: Boolean) -> Unit,
) {
    val editing = target as? EntryEditorTarget.Edit
    val now = System.currentTimeMillis()
    var start by remember { mutableLongStateOf(editing?.entry?.start ?: (now - 30 * 60_000L)) }
    var end by remember { mutableLongStateOf(editing?.entry?.end ?: now) }
    var description by remember { mutableStateOf(editing?.entry?.description ?: defaultDescription) }
    var billable by remember { mutableStateOf(editing?.entry?.billable ?: billableDefault) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"

    picking?.let { which ->
        DateTimePickerFlow(
            initialMs = if (which == "start") start else end,
            onDismiss = { picking = null },
            onPicked = { ms -> if (which == "start") start = ms else end = ms; picking = null },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Add time entry" else "Edit time entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Description") },
                    singleLine = false, maxLines = 2, keyboardOptions = KeyboardOptions(), modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField("Start", start) { picking = "start" }
                DateTimeField("End", end) { picking = "end" }
                if (end <= start) {
                    Text("End must be after start.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Billable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = billable, onCheckedChange = { billable = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start, end, description.trim(), billable) }, enabled = end > start) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

@Composable
private fun DateTimeField(label: String, ms: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        Spacer(Modifier.width(8.dp))
        Text(formatTimestamp(ms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- Project picker for per-card override --------------------------------------------------------

@Composable
private fun ProjectPickerDialog(
    projects: List<STProject>?,
    currentOverrideName: String?,
    boardProjectName: String?,
    onDismiss: () -> Unit,
    onSelect: (STProject) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose project for this card") },
        text = {
            if (projects == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (projects.isEmpty()) {
                Text("No projects found in Solidtime.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Alphabetical, so the list is predictable to scan even before searching.
                val sorted = remember(projects) { projects.sortedBy { it.name.lowercase() } }
                val filtered = remember(sorted, query) {
                    if (query.isBlank()) sorted else sorted.filter { it.name.contains(query, ignoreCase = true) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Only worth the extra row once there's enough to search through.
                    if (sorted.size > 6) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search projects...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Outlined.Close, "Clear search", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            "No projects match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        LazyColumn(
                            // Caps the dialog's height so it never grows past what the screen can
                            // show -- the previous plain Column had no scroll of its own and simply
                            // overflowed off-screen on any board with more than a handful of projects.
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (currentOverrideName != null && query.isBlank()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .clickable(onClick = onClear).padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "Use board default" + if (boardProjectName != null) " ($boardProjectName)" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            items(filtered, key = { it.id }) { project ->
                                val isSelected = project.name == currentOverrideName
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable { onSelect(project) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val color = runCatching { Color(android.graphics.Color.parseColor("#${project.color}")) }
                                        .getOrDefault(MaterialTheme.colorScheme.primary)
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        project.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

/** Date picker, then time picker, yielding a single epoch-ms value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerFlow(initialMs: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }

    if (pickedDateUtc == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(initialMs).atZone(zone).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { pickedDateUtc = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) { Text("Next", color = MaterialTheme.colorScheme.primary) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        ) { DatePicker(state = dateState) }
    } else {
        val existing = Instant.ofEpochMilli(initialMs).atZone(zone).toLocalTime()
        val timeState = rememberTimePickerState(initialHour = existing.hour, initialMinute = existing.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(pickedDateUtc!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val local = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                    onPicked(local.atZone(zone).toInstant().toEpochMilli())
                }) { Text("Set", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
}
