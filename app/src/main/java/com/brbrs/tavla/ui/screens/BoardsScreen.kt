package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.R
import com.brbrs.tavla.data.Board
import com.brbrs.tavla.data.BoardCardCount
import com.brbrs.tavla.data.CardWithContext
import com.brbrs.tavla.ui.components.BoardTile
import com.brbrs.tavla.ui.components.BoardTileBackground
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.DueBadge
import com.brbrs.tavla.ui.components.EmptyState
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.components.TitleColorDialog
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.components.colorFromHex
import com.brbrs.tavla.ui.theme.FabShape
import com.brbrs.tavla.ui.theme.LightBorderMed
import com.brbrs.tavla.ui.theme.LightSurface
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.viewmodels.BoardsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsScreen(
    onBoardClick: (Long) -> Unit,
    onCardClick: (boardId: Long, cardId: Long) -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val runningTimer by viewModel.runningTimer.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Board?>(null) }
    var deleting by remember { mutableStateOf<Board?>(null) }
    var pickingIcon by remember { mutableStateOf<Board?>(null) }

    // Board cover image via the system photo picker -- no storage permission needed, it returns
    // only the one image the user picks (#02).
    var coverTargetBoard by remember { mutableStateOf<Long?>(null) }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val boardId = coverTargetBoard
        coverTargetBoard = null
        if (uri != null && boardId != null) viewModel.setBoardCover(boardId, uri)
    }

    val pendingUndo by viewModel.pendingUndo.collectAsState()
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(pendingUndo) {
        val undo = pendingUndo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(undo.label, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoAction()
    }

    if (showCreate) {
        TitleColorDialog(
            title = "New board",
            confirmText = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { t, c -> viewModel.createBoard(t, c); showCreate = false },
        )
    }
    editing?.let { board ->
        TitleColorDialog(
            title = "Edit board",
            confirmText = "Save",
            initialTitle = board.title,
            initialColor = board.color,
            onDismiss = { editing = null },
            onConfirm = { t, c -> viewModel.updateBoard(board, t, c); editing = null },
        )
    }
    deleting?.let { board ->
        ConfirmDialog(
            title = "Delete \"${board.title}\"?",
            body = "The board, its lists and all cards are deleted on your Nextcloud for everyone it's shared with. " +
                "Deck keeps deleted boards in its trash for a while.",
            confirmText = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { viewModel.deleteBoard(board); deleting = null },
        )
    }
    pickingIcon?.let { board ->
        com.brbrs.tavla.ui.components.IconPickerDialog(
            currentIcon = uiState.boardIcons[board.id],
            onDismiss = { pickingIcon = null },
            onSelect = { icon -> viewModel.setBoardIcon(board.id, icon); pickingIcon = null },
        )
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            // -- Top bar: tinted logo as title, sync / theme / settings actions --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Image(
                        painter = painterResource(R.drawable.tavla_logo),
                        contentDescription = "Tavla",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.height(36.dp),
                    )
                    Text(
                        buildString {
                            val active = uiState.boards.count { !it.archived }
                            append(if (active == 1) "1 board" else "$active boards")
                            if (uiState.isRefreshing) append(" · syncing...")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isRefreshing) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        TopBarIcon(Icons.Outlined.Sync, "Sync") { viewModel.refresh() }
                    }
                    TopBarIcon(
                        when (uiState.themeMode) {
                            "light" -> Icons.Outlined.LightMode
                            "dark", "oled" -> Icons.Outlined.DarkMode
                            else -> Icons.Outlined.BrightnessAuto
                        },
                        "Theme: ${uiState.themeMode}",
                    ) { viewModel.cycleTheme() }
                    TopBarIcon(Icons.Outlined.CalendarMonth, "Calendar") { onCalendar() }
                    TopBarIcon(Icons.Outlined.Settings, "Settings") { onSettings() }
                }
            }

            SearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchChanged,
                isDark = isDark,
            )

            runningTimer?.let { rt ->
                if (rt.cardId != null && rt.boardId != null) {
                    RunningTimerBanner(rt, isDark) { onCardClick(rt.boardId, rt.cardId) }
                }
            }

            Spacer(Modifier.height(8.dp))

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // #01: while searching, a full-card match list replaces the due-soon strip --
                    // a search is a more specific ask than "what's coming up".
                    if (uiState.isSearching) {
                        item(key = "search_label") {
                            SectionLabel("Matching cards", trailing = uiState.searchResults.size.takeIf { it > 0 }?.toString())
                        }
                        if (uiState.searchResults.isEmpty()) {
                            item(key = "search_empty") {
                                Text(
                                    "No cards match \"${uiState.searchQuery.trim()}\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        } else {
                            items(uiState.searchResults, key = { "search_${it.card.id}" }) { item ->
                                SearchResultRow(item, isDark) { onCardClick(item.card.boardId, item.card.id) }
                            }
                        }
                        item(key = "search_spacer") { Spacer(Modifier.height(6.dp)) }
                    } else if (uiState.dueCards.isNotEmpty()) {
                        item(key = "due_label") {
                            SectionLabel("Due soon", trailing = "${uiState.dueCards.size}")
                        }
                        item(key = "due_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.dueCards, key = { "due_${it.card.id}" }) { item ->
                                    DueTile(item, isDark) { onCardClick(item.card.boardId, item.card.id) }
                                }
                            }
                        }
                        item(key = "due_spacer") { Spacer(Modifier.height(6.dp)) }
                    }

                    item(key = "boards_label") { SectionLabel("Boards") }

                    if (uiState.boards.isEmpty()) {
                        item(key = "empty") {
                            if (uiState.hasLoadedOnce) {
                                EmptyState(
                                    icon = Icons.Outlined.ViewColumn,
                                    title = if (uiState.searchQuery.isBlank()) "No boards yet" else "No boards match",
                                    body = if (uiState.searchQuery.isBlank())
                                        "Create your first board, or share one with yourself from Deck on the web."
                                    else "Try a different search.",
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    val isGrid = uiState.cardViewMode == "card"
                    val isCompact = uiState.cardDensity == "compact"

                    if (isGrid) {
                        // Card view: 2-column grid of boards. Board counts are typically small
                        // enough that a non-lazy grid inside a single LazyColumn item is fine.
                        item(key = "boards_grid") {
                            val rows = uiState.boards.chunked(2)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { rowBoards ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        rowBoards.forEach { board ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                BoardCard(
                                                    board = board,
                                                    counts = uiState.counts[board.id],
                                                    iconName = uiState.boardIcons[board.id],
                                                    imagePath = uiState.boardImages[board.id],
                                                    compact = isCompact,
                                                    isDark = isDark,
                                                    onClick = { onBoardClick(board.id) },
                                                    onEdit = { editing = board },
                                                    onPickIcon = { pickingIcon = board },
                                                    onPickCover = {
                                                        coverTargetBoard = board.id
                                                        coverPicker.launch(
                                                            androidx.activity.result.PickVisualMediaRequest(
                                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                            ),
                                                        )
                                                    },
                                                    onRemoveCover = { viewModel.removeBoardCover(board.id) },
                                                    hasCover = board.id in uiState.boardsWithCover,
                                                    onArchive = { viewModel.setArchived(board, !board.archived) },
                                                    onDelete = { deleting = board },
                                                )
                                            }
                                        }
                                        // Fill empty space in the last row if odd number of boards
                                        if (rowBoards.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        items(uiState.boards, key = { "board_${it.id}" }) { board ->
                            BoardRow(
                                board = board,
                                counts = uiState.counts[board.id],
                                iconName = uiState.boardIcons[board.id],
                                imagePath = uiState.boardImages[board.id],
                                compact = isCompact,
                                isDark = isDark,
                                onClick = { onBoardClick(board.id) },
                                onEdit = { editing = board },
                                onPickIcon = { pickingIcon = board },
                                onPickCover = {
                                    coverTargetBoard = board.id
                                    coverPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                onRemoveCover = { viewModel.removeBoardCover(board.id) },
                                hasCover = board.id in uiState.boardsWithCover,
                                onArchive = { viewModel.setArchived(board, !board.archived) },
                                onDelete = { deleting = board },
                            )
                        }
                    }

                    if (uiState.archivedCount > 0 && uiState.searchQuery.isBlank()) {
                        item(key = "archived_toggle") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { viewModel.setShowArchived(!uiState.showArchived) }) {
                                    Text(
                                        if (uiState.showArchived) "Hide archived boards"
                                        else "Show archived boards (${uiState.archivedCount})",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("New board", style = MaterialTheme.typography.titleMedium) },
            shape = FabShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, isDark: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.07f) else LightSurface)
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else LightBorderMed, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search boards and due cards...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DueTile(item: CardWithContext, isDark: Boolean, onClick: () -> Unit) {
    val boardColor = colorFromHex(item.boardColor)
    Column(
        modifier = Modifier
            .width(170.dp)
            .tavlaCard(isDark, cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item.card.dueDate?.let { DueBadge(it) }
        Text(
            item.card.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(boardColor))
            Text(
                item.boardTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** #01: one row per matching card, title + a snippet of description and which board it's on. */
@Composable
private fun SearchResultRow(item: CardWithContext, isDark: Boolean, onClick: () -> Unit) {
    val boardColor = colorFromHex(item.boardColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tavlaCard(isDark, cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(boardColor),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.card.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (item.card.doneAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.card.description.isNotBlank()) {
                Text(
                    item.card.description.replace('\n', ' ').trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                item.boardTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.card.dueDate?.let { DueBadge(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardRow(
    board: Board,
    counts: BoardCardCount?,
    iconName: String?,
    imagePath: String?,
    compact: Boolean = false,
    isDark: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPickIcon: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    hasCover: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pad = if (compact) 10.dp else 14.dp
    Box {
        BoardTileBackground(
            colorHex = board.color,
            imagePath = imagePath,
            isDark = isDark,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (board.canManage) menuOpen = true }),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            BoardTile(board.color, iconName = iconName, imagePath = imagePath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    board.title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = if (board.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        buildString {
                            if (board.archived) {
                                append("Archived")
                            } else {
                                val n = counts?.cardCount ?: 0
                                append(if (n == 1) "1 card" else "$n cards")
                                val due = counts?.dueCount ?: 0
                                if (due > 0) append(" · $due due soon")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!compact && board.isShared) {
                Icon(Icons.Outlined.People, "Shared board", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit title and color") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Choose icon") },
                leadingIcon = { Icon(com.brbrs.tavla.icons.IconCatalog.iconFor(iconName), null) },
                onClick = { menuOpen = false; onPickIcon() },
            )
            DropdownMenuItem(
                text = { Text(if (hasCover) "Change cover image" else "Choose cover image") },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { menuOpen = false; onPickCover() },
            )
            if (hasCover) {
                DropdownMenuItem(
                    text = { Text("Remove cover image") },
                    leadingIcon = { Icon(Icons.Outlined.HideImage, null) },
                    onClick = { menuOpen = false; onRemoveCover() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (board.archived) "Restore board" else "Archive board") },
                leadingIcon = { Icon(if (board.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                onClick = { menuOpen = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text("Delete board", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/** Card-style board tile for the 2-column grid view. Shows the board icon/color prominently with
 *  title and card count below, more like a kanban board thumbnail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardCard(
    board: Board,
    counts: BoardCardCount?,
    iconName: String?,
    imagePath: String?,
    compact: Boolean = false,
    isDark: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPickIcon: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    hasCover: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        BoardTileBackground(
            colorHex = board.color,
            imagePath = imagePath,
            isDark = isDark,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (board.canManage) menuOpen = true }),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardTile(board.color, iconName = iconName, imagePath = imagePath)
            Text(
                board.title,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                color = if (board.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!compact) {
                Text(
                    buildString {
                        if (board.archived) append("Archived")
                        else {
                            val n = counts?.cardCount ?: 0
                            append(if (n == 1) "1 card" else "$n cards")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (board.isShared) {
                Icon(Icons.Outlined.People, "Shared board", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit title and color") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Choose icon") },
                leadingIcon = { Icon(com.brbrs.tavla.icons.IconCatalog.iconFor(iconName), null) },
                onClick = { menuOpen = false; onPickIcon() },
            )
            DropdownMenuItem(
                text = { Text(if (hasCover) "Change cover image" else "Choose cover image") },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { menuOpen = false; onPickCover() },
            )
            if (hasCover) {
                DropdownMenuItem(
                    text = { Text("Remove cover image") },
                    leadingIcon = { Icon(Icons.Outlined.HideImage, null) },
                    onClick = { menuOpen = false; onRemoveCover() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (board.archived) "Restore board" else "Archive board") },
                leadingIcon = { Icon(if (board.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                onClick = { menuOpen = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text("Delete board", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/** A tap-through banner shown on the boards screen while a Solidtime timer is running. */
@Composable
private fun RunningTimerBanner(
    rt: com.brbrs.tavla.solidtime.timer.RunningTimer,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    var nowMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(rt.entryId) {
        while (true) { nowMs = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rt.cardTitle ?: rt.description.ifBlank { "Tracking time" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            rt.projectName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(
            com.brbrs.tavla.util.formatElapsedClock(nowMs - rt.startMs),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
