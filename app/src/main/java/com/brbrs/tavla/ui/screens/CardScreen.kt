package com.brbrs.tavla.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.data.DeckComment
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.LabelChip
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.components.TitleColorDialog
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.components.UserAvatar
import com.brbrs.tavla.ui.components.dueColor
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.theme.tavlaChip
import com.brbrs.tavla.ui.viewmodels.CardUiState
import com.brbrs.tavla.ui.viewmodels.CardViewModel
import com.brbrs.tavla.util.descriptionWithoutChecklist
import com.brbrs.tavla.util.formatDue
import com.brbrs.tavla.util.formatTimestamp
import com.brbrs.tavla.util.parseChecklist
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CardScreen(
    onBack: () -> Unit,
    viewModel: CardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val timer by viewModel.timerState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    val card = ui.card

    var menuOpen by remember { mutableStateOf(false) }
    var stackMenu by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showNewLabel by remember { mutableStateOf(false) }
    var showAssignees by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingComment by remember { mutableStateOf<DeckComment?>(null) }
    var showAddLink by remember { mutableStateOf(false) }
    var sendToApp by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    val linkedContacts by viewModel.linkedContactsState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Contact linking needs READ_CONTACTS -- requested lazily, only when the picker is opened,
    // rather than at startup, since most people never touch this feature.
    val contactsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showContactPicker = true }
    val hasContactsPermission = {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val requestContactPicker = {
        if (hasContactsPermission()) showContactPicker = true
        else contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
    }
    var pendingCapturePath by remember { mutableStateOf<java.io.File?>(null) }

    // #08: camera capture goes to a cache file first, then is uploaded as an attachment.
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCapturePath
        if (success && file != null) {
            viewModel.uploadAttachment(file.name, "image/jpeg", file.readBytes())
        }
        file?.delete()
        pendingCapturePath = null
    }
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val resolver = context.contentResolver
                val name = com.brbrs.tavla.util.queryDisplayName(resolver, uri) ?: "file"
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                viewModel.uploadAttachment(name, mime, bytes)
            }
        }
    }
    val onPickCamera: () -> Unit = {
        val dir = java.io.File(context.cacheDir, "captures").apply { mkdirs() }
        val file = java.io.File(dir, "tavla_${System.currentTimeMillis()}.jpg")
        pendingCapturePath = file
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }
    val onPickFile: () -> Unit = { filePickerLauncher.launch("*/*") }
    val onOpenAttachment: (com.brbrs.tavla.data.Attachment) -> Unit = { attachment ->
        scope.launch {
            viewModel.downloadAttachment(attachment).onSuccess { bytes ->
                runCatching {
                    val dir = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
                    val file = java.io.File(dir, attachment.fileName)
                    file.writeBytes(bytes)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val mime = context.contentResolver.getType(uri)
                        ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(attachment.extension)
                        ?: "*/*"
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }
    val onOpenLink: (com.brbrs.tavla.settings.CrossAppLink) -> Unit = { link ->
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link.uri)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            // The other app doesn't register this scheme yet -- fall back to opening it if installed.
            runCatching { context.packageManager.getLaunchIntentForPackage(link.appPackage)?.let { context.startActivity(it) } }
        }
    }

    fun leave() { viewModel.saveDraftsOnExit(); onBack() }
    BackHandler { leave() }

    LaunchedEffect(ui.closed) { if (ui.closed) onBack() }
    LaunchedEffect(timer.message) { timer.message?.let { snackbar.showSnackbar(it); viewModel.consumeTimerMessage() } }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    // -- Dialogs --
    if (showLabels && card != null) {
        val boardLabels = ui.board?.labels.orEmpty()
        AlertDialog(
            onDismissRequest = { showLabels = false },
            title = { Text("Labels") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (boardLabels.isEmpty()) {
                        Text("This board has no labels yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    boardLabels.forEach { label ->
                        val assigned = card.labels.any { it.id == label.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !ui.isSaving) { viewModel.setLabel(label, !assigned) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = assigned, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            LabelChip(label)
                        }
                    }
                    if (ui.board?.canManage != false) {
                        TextButton(onClick = { showNewLabel = true }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New label")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLabels = false }) { Text("Done", color = MaterialTheme.colorScheme.primary) } },
        )
    }
    if (showNewLabel) {
        TitleColorDialog(
            title = "New label",
            confirmText = "Create",
            onDismiss = { showNewLabel = false },
            onConfirm = { t, c -> viewModel.createAndAssignLabel(t, c); showNewLabel = false },
        )
    }
    if (showAssignees && card != null) {
        val users = ui.board?.users.orEmpty()
        AlertDialog(
            onDismissRequest = { showAssignees = false },
            title = { Text("Assigned to") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (users.isEmpty()) {
                        Text("Share this board with people in Deck to assign cards to them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    users.forEach { user ->
                        val assigned = card.assignees.any { it.uid == user.uid }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !ui.isSaving) { viewModel.setAssignee(user, !assigned) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = assigned, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            UserAvatar(user, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (user.uid == ui.username) "${user.displayName} (you)" else user.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssignees = false }) { Text("Done", color = MaterialTheme.colorScheme.primary) } },
        )
    }
    if (showDate) {
        val zone = ZoneId.systemDefault()
        val initialUtc = card?.dueDate?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { pickedDateUtc = dateState.selectedDateMillis; showDate = false }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                Row {
                    if (card?.dueDate != null) {
                        TextButton(onClick = { viewModel.setDueDate(null); showDate = false }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDate = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
    pickedDateUtc?.let { dateUtc ->
        val zone = ZoneId.systemDefault()
        val existing = card?.dueDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
        val timeState = rememberTimePickerState(
            initialHour = existing?.hour ?: 17,
            initialMinute = existing?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickedDateUtc = null },
            title = { Text("Time") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(dateUtc).atZone(ZoneOffset.UTC).toLocalDate()
                    val local = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                    viewModel.setDueDate(local.atZone(zone).toInstant().toEpochMilli())
                    pickedDateUtc = null
                }) { Text("Set due date", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pickedDateUtc = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this card?",
            body = "It's removed from the board on your Nextcloud for everyone. Archiving keeps it out of sight instead.",
            confirmText = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.delete() },
        )
    }
    deletingComment?.let { c ->
        ConfirmDialog(
            title = "Delete comment?",
            body = "\"${c.message.take(80)}\"",
            confirmText = "Delete",
            onDismiss = { deletingComment = null },
            onConfirm = { viewModel.deleteComment(c); deletingComment = null },
        )
    }
    if (showAddLink) {
        AddLinkDialog(
            onDismiss = { showAddLink = false },
            onConfirm = { pkg, label, name, uri -> viewModel.addLink(pkg, label, name, uri); showAddLink = false },
        )
    }
    sendToApp?.let { pkg ->
        val appLabel = if (pkg == "com.brbrs.nota") "Nóta" else "Koto"
        SendToAppDialog(
            appLabel = appLabel,
            initialTitle = card?.title.orEmpty(),
            body = card?.description.orEmpty(),
            onDismiss = { sendToApp = null },
            onConfirm = { title, body ->
                com.brbrs.tavla.sharesend.SendToAppHelper.send(context, pkg, title, body)
                sendToApp = null
            },
        )
    }
    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onSearch = { query -> viewModel.searchContacts(query) },
            onPick = { contact -> viewModel.linkContact(contact); showContactPicker = false },
        )
    }

    ScreenBackground(boardColorHex = ui.board?.color, coverImagePath = ui.boardImage) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // -- Header --
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { leave() }
                Spacer(Modifier.weight(1f))
                if (ui.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                if (card != null && ui.canEdit) {
                    TopBarIcon(
                        if (ui.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.CheckCircleOutline,
                        if (ui.isDone) "Mark not done" else "Mark done",
                        active = ui.isDone,
                    ) { viewModel.setDone(!ui.isDone) }
                    TopBarIcon(
                        if (card.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        if (card.archived) "Restore card" else "Archive card",
                    ) { viewModel.setArchived(!card.archived) }
                    Box {
                        TopBarIcon(Icons.Outlined.MoreVert, "More options") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Send to Nóta") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                                onClick = { menuOpen = false; sendToApp = "com.brbrs.nota" },
                            )
                            DropdownMenuItem(
                                text = { Text("Send to Koto") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                                onClick = { menuOpen = false; sendToApp = "com.brbrs.koto" },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete card", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }

            if (card == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Stack + title + labels
                item(key = "head") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .tavlaChip(isDark, selected = false)
                                    .clickable(enabled = ui.canEdit && ui.stacks.size > 1) { stackMenu = true }
                                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(ui.stackTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                if (ui.canEdit && ui.stacks.size > 1) {
                                    Icon(Icons.Outlined.ExpandMore, "Move to another list", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(expanded = stackMenu, onDismissRequest = { stackMenu = false }) {
                                ui.stacks.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                s.title,
                                                color = if (s.id == card.stackId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = { stackMenu = false; viewModel.moveTo(s) },
                                    )
                                }
                            }
                        }

                        TitleEditor(ui, card, onChange = viewModel::onTitleChanged, onSave = viewModel::saveTitle)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            card.labels.forEach { label ->
                                Box(Modifier.clip(RoundedCornerShape(50)).clickable(enabled = ui.canEdit) { showLabels = true }) {
                                    LabelChip(label)
                                }
                            }
                            if (ui.canEdit) {
                                Row(
                                    modifier = Modifier
                                        .tavlaChip(isDark, selected = false)
                                        .clickable { showLabels = true }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (card.labels.isEmpty()) "Add label" else "Label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Due + assignees tiles
                item(key = "meta") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetaTile(
                            title = "Due",
                            modifier = Modifier.weight(1f),
                            isDark = isDark,
                            accent = card.dueDate?.let { dueColor(it) },
                            onClick = if (ui.canEdit) ({ showDate = true }) else null,
                        ) {
                            Text(
                                card.dueDate?.let { formatDue(it) } ?: "No due date",
                                style = MaterialTheme.typography.titleSmall,
                                color = card.dueDate?.let { dueColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MetaTile(
                            title = "Assigned",
                            modifier = Modifier.weight(1f),
                            isDark = isDark,
                            onClick = if (ui.canEdit) ({ showAssignees = true }) else null,
                        ) {
                            if (card.assignees.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nobody", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    UserAvatar(card.assignees.first(), 22.dp)
                                    Text(
                                        card.assignees.first().displayName +
                                            if (card.assignees.size > 1) " +${card.assignees.size - 1}" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                item(key = "description") {
                    DescriptionSection(
                        ui = ui,
                        card = card,
                        onStartEdit = viewModel::startEditingDescription,
                        onChange = viewModel::onDescriptionChanged,
                        onCancel = viewModel::cancelDescription,
                        onSave = viewModel::saveDescription,
                    )
                }

                // Checklist
                item(key = "checklist") {
                    ChecklistSection(
                        card = card,
                        enabled = ui.canEdit && !ui.isSaving && ui.descriptionDraft == null,
                        onToggle = viewModel::toggleChecklist,
                        onAdd = viewModel::addChecklist,
                    )
                }

                // Attachments (#08)
                item(key = "attachments") {
                    AttachmentsSection(
                        ui = ui,
                        onPickCamera = onPickCamera,
                        onPickFile = onPickFile,
                        onOpen = { attachment -> onOpenAttachment(attachment) },
                        onDelete = viewModel::deleteAttachment,
                    )
                }

                // Linked items (#12) -- links to a Nóta note or Vinci contact, or any pasted link.
                item(key = "links") {
                    LinksSection(
                        links = ui.links,
                        canEdit = ui.canEdit,
                        onAdd = { showAddLink = true },
                        onOpen = { link -> onOpenLink(link) },
                        onRemove = viewModel::removeLink,
                    )
                }

                // Linked contacts -- cross-app integration with Vinci (see cross-app-linking
                // notes). Distinct from "Linked items" (#12) above: that's a card pointing out
                // at anything in any app; this is specifically the reverse index Vinci's
                // ContactLinksRepository reads to show this card on a contact's page.
                item(key = "linked_contacts") {
                    val linkSyncError by viewModel.linkSyncError.collectAsState()
                    LinkedContactsSection(
                        contacts = linkedContacts,
                        canEdit = ui.canEdit,
                        onAdd = { requestContactPicker() },
                        onRemove = viewModel::unlinkContact,
                        syncError = linkSyncError.takeIf { linkedContacts.isNotEmpty() },
                    )
                }

                // Solidtime time tracking -- only shows when the board is linked to a project.
                if (timer.linked) {
                    item(key = "timer") {
                        CardTimerSection(
                            timer = timer,
                            defaultDescription = ui.card?.title.orEmpty(),
                            canEdit = ui.canEdit,
                            onStart = { desc, billable -> viewModel.startTimer(desc, billable) },
                            onStop = viewModel::stopTimer,
                            onAddEntry = { start, end, desc, billable -> viewModel.addManualEntry(start, end, desc, billable) },
                            onEditEntry = { entry, start, end, desc, billable -> viewModel.updateEntry(entry, start, end, desc, billable) },
                            onDeleteEntry = viewModel::deleteEntry,
                            onLoadProjects = viewModel::loadProjects,
                            onSetCardProject = viewModel::setCardProject,
                            onClearCardProject = viewModel::clearCardProject,
                        )
                    }
                }

                // Comments
                item(key = "comments_label") {
                    SectionLabel(
                        "Comments",
                        trailing = if (ui.commentsLoading) "loading..." else ui.comments.size.takeIf { it > 0 }?.toString(),
                    )
                }
                item(key = "comments_body") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tavlaCard(isDark, cornerRadius = 16.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        if (!ui.commentsLoading && ui.comments.isEmpty()) {
                            Text(
                                "No comments yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        } else {
                            Column {
                                ui.comments.forEach { c ->
                                    val mine = c.actorId == ui.username
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .combinedClickable(onClick = {}, onLongClick = { if (mine) deletingComment = c })
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                    ) {
                                        UserAvatar(com.brbrs.tavla.data.DeckUser(c.actorId, c.actorDisplayName), 30.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(c.actorDisplayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(Modifier.width(8.dp))
                                                Text(formatTimestamp(c.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(c.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -- Comment composer --
            if (ui.descriptionDraft == null) {
                CommentBar(
                    value = ui.commentDraft,
                    onChange = viewModel::onCommentChanged,
                    onSend = viewModel::postComment,
                    enabled = !ui.isSaving,
                    isDark = isDark,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
        )
    }
}

@Composable
private fun TitleEditor(ui: CardUiState, card: Card, onChange: (String) -> Unit, onSave: () -> Unit) {
    val value = ui.titleDraft ?: card.title
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = ui.canEdit,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            modifier = Modifier.weight(1f),
        )
        if (ui.titleDraft != null && ui.titleDraft != card.title) {
            IconButton(onClick = onSave) {
                Icon(Icons.Outlined.Check, "Save title", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MetaTile(
    title: String,
    modifier: Modifier,
    isDark: Boolean,
    accent: Color? = null,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .then(
                if (accent != null) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.10f))
                else Modifier.tavlaCard(isDark, cornerRadius = 16.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun DescriptionSection(
    ui: CardUiState,
    card: Card,
    onStartEdit: () -> Unit,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val isDark = LocalIsDark.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Description", modifier = Modifier.weight(1f))
            if (ui.canEdit && ui.descriptionDraft == null) {
                IconButton(onClick = onStartEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, "Edit description", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val draft = ui.descriptionDraft
            if (draft != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Markdown works. Checklist items look like: - [ ] Buy paint") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = onSave) { Text("Save", color = MaterialTheme.colorScheme.primary) }
                    }
                }
            } else {
                val prose = remember(card.description) { descriptionWithoutChecklist(card.description) }
                if (prose.isBlank()) {
                    Text(
                        if (ui.canEdit) "Add a description..." else "No description.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = ui.canEdit, onClick = onStartEdit)
                            .padding(vertical = 4.dp),
                    )
                } else {
                    // #07: a lightweight renderer (no external library) for headings, bold/italic,
                    // links, lists and blockquotes -- plain text still looks exactly as before.
                    com.brbrs.tavla.markdown.MarkdownText(
                        text = prose,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = ui.canEdit, onClick = onStartEdit),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistSection(
    card: Card,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
    onAdd: (String) -> Unit,
) {
    val items = remember(card.description) { parseChecklist(card.description) }
    var newItem by remember { mutableStateOf("") }
    val isDark = LocalIsDark.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Checklist", trailing = if (items.isNotEmpty()) "${items.count { it.done }} of ${items.size}" else null)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (items.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { items.count { it.done }.toFloat() / items.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onToggle(item.lineIndex) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = null,
                            enabled = enabled,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(8.dp),
                        )
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                        )
                    }
                }
                if (enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        OutlinedTextField(
                            value = newItem,
                            onValueChange = { newItem = it },
                            placeholder = { Text("Add an item") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (newItem.isNotBlank()) { onAdd(newItem); newItem = "" } }),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        IconButton(onClick = { onAdd(newItem); newItem = "" }, enabled = newItem.isNotBlank()) {
                            Icon(Icons.Outlined.Add, "Add checklist item", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentBar(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    isDark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .tavlaCard(isDark, cornerRadius = 23.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Write a comment...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(if (value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                "Send comment",
                tint = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// -- Attachments (#08) ------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentsSection(
    ui: CardUiState,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onOpen: (com.brbrs.tavla.data.Attachment) -> Unit,
    onDelete: (com.brbrs.tavla.data.Attachment) -> Unit,
) {
    val isDark = LocalIsDark.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                "Attachments",
                modifier = Modifier.weight(1f),
                trailing = ui.attachments.size.takeIf { it > 0 }?.toString(),
            )
            if (ui.canEdit) {
                if (ui.uploadingAttachment) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    IconButton(onClick = onPickCamera, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.CameraAlt, "Add photo", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onPickFile, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.UploadFile, "Add file", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (ui.attachmentsLoading && ui.attachments.isEmpty()) {
                Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (ui.attachments.isEmpty()) {
                Text(
                    "No attachments. Add a photo of a receipt or whiteboard, or any file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    ui.attachments.forEach { attachment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = { onOpen(attachment) }, onLongClick = { if (ui.canEdit) onDelete(attachment) })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (attachment.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")) Icons.Outlined.CameraAlt
                                else Icons.Outlined.InsertDriveFile,
                                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (attachment.sizeBytes > 0) {
                                    Text(formatFileSize(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

// -- Linked items (#12) ------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinksSection(
    links: List<com.brbrs.tavla.settings.CrossAppLink>,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onOpen: (com.brbrs.tavla.settings.CrossAppLink) -> Unit,
    onRemove: (String) -> Unit,
) {
    val isDark = LocalIsDark.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Linked items", modifier = Modifier.weight(1f), trailing = links.size.takeIf { it > 0 }?.toString())
            if (canEdit) {
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, "Add link", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (links.isEmpty()) {
                Text(
                    "Link a Vinci contact, a Nóta note, or any other BarburasLab item.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    links.forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = { onOpen(link) }, onLongClick = { onRemove(link.id) })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(link.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(link.appLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * #12: the person names what they're linking to and pastes a share link, since Tavla has no way
 * to browse Vinci's contacts or Nóta's notes from inside this app. See [com.brbrs.tavla.settings.CrossAppLink]
 * for the deep-link contract those apps need to support so the link opens the exact item.
 */
@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (appPackage: String, appLabel: String, label: String, uri: String) -> Unit,
) {
    var appChoice by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    val isDark = LocalIsDark.current
    val apps = listOf(
        Triple("com.brbrs.vinci", "Vinci", "vinci://"),
        Triple("com.brbrs.nota", "Nóta", "nota://"),
        Triple("com.brbrs.koto", "Koto", "koto://"),
        Triple("", "Other app", ""),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add linked item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEachIndexed { index, (_, name, _) ->
                        val selected = appChoice == index
                        Box(
                            modifier = Modifier
                                .tavlaChip(isDark = isDark, selected = selected)
                                .clickable { appChoice = index }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("What is it? (e.g. \"Sarah\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Link") },
                    placeholder = { Text(apps[appChoice].third.ifBlank { "https:// or app://..." }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Paste a link shared from the other app. If it doesn't support links yet, this just opens the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(apps[appChoice].first, apps[appChoice].second, label.ifBlank { apps[appChoice].second }, uri) },
                enabled = uri.isNotBlank(),
            ) { Text("Add", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

/**
 * Lets the person review/edit the title before sending this card to another suite app as a
 * new item there (a generic share -- see [com.brbrs.tavla.sharesend.SendToAppHelper]).
 */
@Composable
private fun SendToAppDialog(
    appLabel: String,
    initialTitle: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send to $appLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (body.isNotBlank()) {
                    Text(
                        "The card's description will be sent along with it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, body) }, enabled = title.isNotBlank()) {
                Text("Send", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

// -- Linked contacts (cross-app integration) -----------------------------------------------------
// The reverse of LinksSection above: this is the index Vinci's ContactLinksRepository reads to
// show this card on a contact's "Linked items" section. See cross-app-linking notes.

/** Small circular avatar for a device contact -- their actual photo when they have one synced,
 * falling back to a generic person glyph otherwise. Used by both the picker list and the
 * already-linked list so a card shows the same face in both places.
 *
 * Tavla has no image-loading library wired in (unlike Vinci/Coil), so this decodes the
 * contact's thumbnail URI straight off the ContactsContract content provider instead of
 * pulling in a new dependency for one small avatar. */
@Composable
private fun ContactPhotoAvatar(photoUri: String?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        bitmap = if (photoUri.isNullOrBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(photoUri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Person, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkedContactsSection(
    contacts: List<com.brbrs.tavla.ui.viewmodels.LinkedContact>,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onRemove: (cardavUid: String) -> Unit,
    syncError: String? = null,
) {
    val isDark = LocalIsDark.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Linked contacts", modifier = Modifier.weight(1f), trailing = contacts.size.takeIf { it > 0 }?.toString())
            if (canEdit) {
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.PersonAdd, "Link a contact", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (contacts.isEmpty()) {
                Text(
                    "Link this card to a Vinci contact -- it'll show up on their profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            } else {
                Column {
                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ContactPhotoAvatar(contact.photoUri, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                contact.displayName.ifBlank { "Contact no longer on this device" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (contact.displayName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (canEdit) {
                                IconButton(onClick = { onRemove(contact.cardavUid) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.Close, "Unlink ${contact.displayName.ifBlank { "contact" }}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (syncError != null) {
                        Text(
                            syncError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Searches the device's address book (same Nextcloud-synced contacts Vinci reads) by name. */
@Composable
private fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> List<com.brbrs.tavla.contactlinks.PickableContact>,
    onPick: (com.brbrs.tavla.contactlinks.PickableContact) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.brbrs.tavla.contactlinks.PickableContact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        results = runCatching { onSearch(query) }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search contacts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Text("Searching...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (results.isEmpty()) {
                    Text(
                        "No contacts found. Only contacts synced with a Nextcloud address book can be linked.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        results.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(contact) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContactPhotoAvatar(contact.photoUri, size = 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
