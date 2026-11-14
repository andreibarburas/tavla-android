package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.data.Stack
import com.brbrs.tavla.ui.components.AvatarStack
import com.brbrs.tavla.ui.components.ConfirmDialog
import com.brbrs.tavla.ui.components.DueBadge
import com.brbrs.tavla.ui.components.EmptyState
import com.brbrs.tavla.ui.components.LabelChip
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.TextInputDialog
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.components.colorFromHex
import com.brbrs.tavla.ui.theme.FabShape
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.theme.tavlaChip
import com.brbrs.tavla.ui.viewmodels.BoardViewModel
import com.brbrs.tavla.ui.viewmodels.CardFilter
import com.brbrs.tavla.util.descriptionWithoutChecklist
import com.brbrs.tavla.util.parseChecklist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardScreen(
    onBack: () -> Unit,
    onCardClick: (Long) -> Unit,
    onInsights: () -> Unit,
    onSolidtime: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val canEdit = uiState.board?.canEdit ?: true
    // #10: the board's own color accents the active tab and the FAB on this screen, so each
    // board feels distinct while the rest of the app stays on the suite's shared Lagoon accent.
    val accent = uiState.board?.color?.let { colorFromHex(it) } ?: MaterialTheme.colorScheme.primary

    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var addCardTo by remember { mutableStateOf<Stack?>(null) }
    var showAddStack by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Stack?>(null) }
    var deletingStack by remember { mutableStateOf<Stack?>(null) }

    val pagerState = rememberPagerState(pageCount = { uiState.stacks.size })
    val tabState = rememberLazyListState()

    val pendingUndo by viewModel.pendingUndo.collectAsState()
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(pendingUndo) {
        val undo = pendingUndo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(undo.label, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoAction()
    }
    LaunchedEffect(pagerState.currentPage, uiState.stacks.size) {
        if (uiState.stacks.isNotEmpty()) tabState.animateScrollToItem(pagerState.currentPage.coerceAtMost(uiState.stacks.lastIndex))
    }

    addCardTo?.let { stack ->
        TextInputDialog(
            title = "New card in ${stack.title}",
            label = "Card title",
            confirmText = "Add",
            onDismiss = { addCardTo = null },
            onConfirm = { viewModel.createCard(stack.id, it); addCardTo = null },
        )
    }
    if (showAddStack) {
        TextInputDialog(
            title = "New list",
            label = "List title",
            confirmText = "Add",
            onDismiss = { showAddStack = false },
            onConfirm = {
                viewModel.createStack(it)
                showAddStack = false
                scope.launch { pagerState.animateScrollToPage(uiState.stacks.size) }
            },
        )
    }
    renaming?.let { stack ->
        TextInputDialog(
            title = "Rename list",
            label = "List title",
            confirmText = "Save",
            initial = stack.title,
            onDismiss = { renaming = null },
            onConfirm = { viewModel.renameStack(stack, it); renaming = null },
        )
    }
    deletingStack?.let { stack ->
        val count = uiState.totalByStack[stack.id] ?: 0
        ConfirmDialog(
            title = "Delete \"${stack.title}\"?",
            body = if (count > 0) "This list and its $count cards are deleted on your Nextcloud." else "This empty list is deleted on your Nextcloud.",
            confirmText = "Delete",
            onDismiss = { deletingStack = null },
            onConfirm = { viewModel.deleteStack(stack); deletingStack = null },
        )
    }

    ScreenBackground(boardColorHex = uiState.board?.color, coverImagePath = uiState.boardImage) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -- Header --
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { onBack() }
                uiState.board?.let {
                    com.brbrs.tavla.ui.components.BoardTile(it.color, size = 34.dp, iconName = uiState.boardIcon, imagePath = uiState.boardImage)
                }
                Text(
                    uiState.board?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Box {
                    TopBarIcon(
                        Icons.Outlined.FilterList, "Filter cards",
                        active = uiState.filter != CardFilter.All || uiState.labelFilter != null,
                    ) { filterMenu = true }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        CardFilter.entries.forEach { f ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        f.label,
                                        color = if (uiState.filter == f) accent else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = { viewModel.setFilter(f); filterMenu = false },
                            )
                        }
                        val labels = uiState.board?.labels.orEmpty()
                        if (labels.isNotEmpty()) {
                            HorizontalDivider()
                            labels.forEach { label ->
                                DropdownMenuItem(
                                    text = { LabelChip(label, selected = uiState.labelFilter == label.id || uiState.labelFilter == null) },
                                    onClick = {
                                        viewModel.setLabelFilter(if (uiState.labelFilter == label.id) null else label.id)
                                        filterMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                Box {
                    TopBarIcon(Icons.Outlined.MoreVert, "More options") { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text("Add list") },
                                leadingIcon = { Icon(Icons.Outlined.ViewColumn, null) },
                                onClick = { moreMenu = false; showAddStack = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = { moreMenu = false; viewModel.refresh() },
                        )
                        DropdownMenuItem(
                            text = { Text("Board insights") },
                            leadingIcon = { Icon(Icons.Outlined.Insights, null) },
                            onClick = { moreMenu = false; onInsights() },
                        )
                        DropdownMenuItem(
                            text = { Text("Solidtime") },
                            leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                            onClick = { moreMenu = false; onSolidtime() },
                        )
                    }
                }
            }

            // -- Active filter pill --
            if (uiState.filter != CardFilter.All || uiState.labelFilter != null) {
                val labelName = uiState.board?.labels?.firstOrNull { it.id == uiState.labelFilter }?.title
                val text = listOfNotNull(
                    uiState.filter.takeIf { it != CardFilter.All }?.label,
                    labelName?.let { "Label: $it" },
                ).joinToString(" · ")
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .tavlaChip(isDark, selected = true, selectedColor = accent)
                        .clickable { viewModel.setFilter(CardFilter.All); viewModel.setLabelFilter(null) }
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = MaterialTheme.typography.labelLarge, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Close, "Clear filter", tint = accent, modifier = Modifier.size(14.dp))
                }
            }

            // -- Stack tabs --
            if (uiState.stacks.isNotEmpty()) {
                LazyRow(
                    state = tabState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(uiState.stacks, key = { _, s -> "tab_${s.id}" }) { index, stack ->
                        var stackMenu by remember { mutableStateOf(false) }
                        val selected = pagerState.currentPage == index
                        Box {
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .tavlaChip(isDark, selected = selected, selectedColor = accent)
                                    .combinedClickable(
                                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                        onLongClick = { if (canEdit) stackMenu = true },
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stack.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    "${uiState.cardsByStack[stack.id]?.size ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = stackMenu, onDismissRequest = { stackMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename list") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { stackMenu = false; renaming = stack },
                                )
                                if (index > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Move left") },
                                        leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                        onClick = { stackMenu = false; viewModel.reorderStack(stack, index - 1) },
                                    )
                                }
                                if (index < uiState.stacks.lastIndex) {
                                    DropdownMenuItem(
                                        text = { Text("Move right") },
                                        leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                        onClick = { stackMenu = false; viewModel.reorderStack(stack, index + 1) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete list", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { stackMenu = false; deletingStack = stack },
                                )
                            }
                        }
                    }
                    if (canEdit) {
                        item(key = "tab_add") {
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .tavlaChip(isDark, selected = false)
                                    .clickable { showAddStack = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Add, "Add list", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // -- Stacks --
            Box(modifier = Modifier.weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        uiState.stacks.isEmpty() && uiState.isRefreshing -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accent)
                            }
                        }
                        uiState.stacks.isEmpty() -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Outlined.ViewColumn,
                                        title = "No lists yet",
                                        body = "Boards are made of lists, like To do, Doing and Done. Add the first one to start adding cards.",
                                    )
                                }
                                if (canEdit) {
                                    item {
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            OutlinedButton(onClick = { showAddStack = true }) { Text("Add a list") }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
                                pageSpacing = 12.dp,
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.Top,
                                key = { uiState.stacks.getOrNull(it)?.id ?: it.toLong() },
                            ) { page ->
                                val stack = uiState.stacks.getOrNull(page) ?: return@HorizontalPager
                                val cards = uiState.cardsByStack[stack.id].orEmpty()
                                StackColumn(
                                    stack = stack,
                                    cards = cards,
                                    totalInStack = uiState.totalByStack[stack.id] ?: 0,
                                    allStacks = uiState.stacks,
                                    pageIndex = page,
                                    showLabels = uiState.showLabels,
                                    isDark = isDark,
                                    canEdit = canEdit,
                                    runningCardId = uiState.runningCardId,
                                    onCardClick = onCardClick,
                                    onAddCard = { addCardTo = stack },
                                    onReorder = { card, targetStackId, targetIndex -> viewModel.reorderCard(card, targetStackId, targetIndex) },
                                    onRequestPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                                    onToggleDone = { card, done -> viewModel.setCardDone(card, done) },
                                )
                            }
                        }
                    }
                }

                // #01: add a card to whatever list is currently visible, without scrolling to the bottom.
                if (canEdit && uiState.stacks.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { uiState.stacks.getOrNull(pagerState.currentPage)?.let { addCardTo = it } },
                        icon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text("Add card", style = MaterialTheme.typography.titleMedium) },
                        shape = FabShape,
                        containerColor = accent,
                        contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        )
    }
}

/**
 * One list's cards. #06: long-press a card to drag it -- up/down reorders within this list;
 * holding near the left/right edge for a moment flips to the adjacent list so the card can be
 * dropped there. #11: haptic tick on drag start and on drop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackColumn(
    stack: Stack,
    cards: List<Card>,
    totalInStack: Int,
    allStacks: List<Stack>,
    pageIndex: Int,
    showLabels: Boolean,
    isDark: Boolean,
    canEdit: Boolean,
    runningCardId: Long?,
    onCardClick: (Long) -> Unit,
    onAddCard: () -> Unit,
    onReorder: (Card, Long, Int) -> Unit,
    onRequestPage: (Int) -> Unit,
    onToggleDone: (Card, Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // A local, reorderable mirror of the list: dragging needs to show cards swap places
    // immediately, before the server round-trip updates the real data back through Room.
    val orderedCards = remember(stack.id) { mutableStateListOf<Card>().apply { addAll(cards) } }
    var draggingId by remember(stack.id) { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var edgeTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(cards, draggingId) {
        // Don't clobber an in-progress drag with a server/Room update that hasn't caught up yet.
        if (draggingId == null) {
            orderedCards.clear()
            orderedCards.addAll(cards)
        }
    }

    // Holding near the edge for a short moment pages to the next/previous list, mid-drag.
    LaunchedEffect(edgeTarget) {
        val target = edgeTarget ?: return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        if (edgeTarget == target) {
            onRequestPage(target)
            edgeTarget = null
        }
    }

    fun endDrag(card: Card) {
        val fromIndex = orderedCards.indexOfFirst { it.id == card.id }
        if (fromIndex >= 0) onReorder(card, stack.id, fromIndex)
        draggingId = null
        dragOffsetY = 0f
        dragOffsetX = 0f
        edgeTarget = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> pageWidthPx = coordinates.size.width.toFloat() },
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(orderedCards, key = { _, c -> "card_${c.id}" }) { index, card ->
            val isDragging = draggingId == card.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            translationX = dragOffsetX
                            scaleX = 1.03f; scaleY = 1.03f
                            shadowElevation = 12f
                        }
                    }
                    .onGloballyPositioned { coordinates -> itemHeightPx = coordinates.size.height.toFloat() },
            ) {
                CardItem(
                    card = card,
                    stacks = allStacks,
                    showLabels = showLabels,
                    isDark = isDark,
                    canEdit = canEdit,
                    tracking = card.id == runningCardId,
                    onClick = { if (draggingId == null) onCardClick(card.id) },
                    onMove = { target -> onReorder(card, target.id, 0) },
                    onToggleDone = { done -> onToggleDone(card, done) },
                    dragHandleModifier = if (!canEdit) Modifier else Modifier.pointerInput(card.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = card.id
                                dragOffsetY = 0f
                                dragOffsetX = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { endDrag(card) },
                            onDragCancel = { draggingId = null; dragOffsetY = 0f; dragOffsetX = 0f; edgeTarget = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                dragOffsetX += dragAmount.x

                                val sidewaysThresholdPx = pageWidthPx.takeIf { it > 0f }?.times(0.35f) ?: 260f
                                edgeTarget = when {
                                    dragOffsetX < -sidewaysThresholdPx && pageIndex > 0 -> pageIndex - 1
                                    dragOffsetX > sidewaysThresholdPx && pageIndex < allStacks.lastIndex -> pageIndex + 1
                                    else -> null
                                }

                                if (itemHeightPx > 0f) {
                                    val moveBy = (dragOffsetY / itemHeightPx).toInt()
                                    if (moveBy != 0) {
                                        val from = orderedCards.indexOfFirst { it.id == card.id }
                                        val to = (from + moveBy).coerceIn(0, orderedCards.lastIndex)
                                        if (from in orderedCards.indices && to != from) {
                                            orderedCards.add(to, orderedCards.removeAt(from))
                                            dragOffsetY -= moveBy * itemHeightPx
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }
        if (cards.isEmpty()) {
            item {
                Text(
                    if (totalInStack > 0) "No cards match the filter." else "No cards in this list yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                )
            }
        }
        if (canEdit) {
            item(key = "add_${stack.id}") {
                AddCardButton(isDark, onClick = onAddCard)
            }
        }
    }
}

@Composable
private fun AddCardButton(isDark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add card", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CardItem(
    card: Card,
    stacks: List<Stack>,
    showLabels: Boolean,
    isDark: Boolean,
    canEdit: Boolean,
    tracking: Boolean = false,
    onClick: () -> Unit,
    onMove: (Stack) -> Unit,
    onToggleDone: (Boolean) -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    var moveMenu by remember { mutableStateOf(false) }
    val checklist = remember(card.description) { parseChecklist(card.description) }
    val hasProse = remember(card.description) { descriptionWithoutChecklist(card.description).isNotBlank() }
    val done = card.doneAt != null

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Done/archived cards are dimmed so they're easy to tell apart from ongoing ones.
                .alpha(if (done || card.archived) 0.55f else 1f)
                .tavlaCard(isDark, cornerRadius = 16.dp)
                .combinedClickable(onClick = onClick, onLongClick = { if (canEdit && stacks.size > 1) moveMenu = true })
                .padding(start = 6.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (canEdit) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    "Drag to reorder or move to another list",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp).then(dragHandleModifier),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showLabels && card.labels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        card.labels.forEach { LabelChip(it) }
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canEdit) {
                        Icon(
                            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            if (done) "Mark not done" else "Mark done",
                            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).clickable { onToggleDone(!done) },
                        )
                    }
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tracking) {
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Outlined.Timer, "Timer running", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }

                val hasMeta = card.dueDate != null || checklist.isNotEmpty() || card.commentsCount > 0 ||
                    card.attachmentCount > 0 || hasProse || card.assignees.isNotEmpty() || card.archived
                if (hasMeta) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (card.archived) MetaIcon(Icons.Outlined.Archive, "Archived", MaterialTheme.colorScheme.onSurfaceVariant)
                        card.dueDate?.let { DueBadge(it) }
                        if (checklist.isNotEmpty()) {
                            val doneCount = checklist.count { it.done }
                            MetaIcon(Icons.Outlined.CheckBox, "$doneCount/${checklist.size}",
                                if (doneCount == checklist.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasProse) MetaIcon(Icons.Outlined.Description, null)
                        if (card.commentsCount > 0) MetaIcon(Icons.Outlined.ChatBubbleOutline, "${card.commentsCount}")
                        if (card.attachmentCount > 0) MetaIcon(Icons.Outlined.AttachFile, "${card.attachmentCount}")
                        Spacer(Modifier.weight(1f))
                        if (card.assignees.isNotEmpty()) AvatarStack(card.assignees)
                    }
                }
            }
        }
        DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
            Text(
                "Move to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            stacks.filter { it.id != card.stackId }.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.title) },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                    onClick = { moveMenu = false; onMove(target) },
                )
            }
        }
    }
}

@Composable
private fun MetaIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String?,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
