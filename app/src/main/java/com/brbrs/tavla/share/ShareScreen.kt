package com.brbrs.tavla.share

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.ui.components.BoardTile
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.theme.tavlaCard
import com.brbrs.tavla.ui.theme.tavlaChip

@Composable
fun ShareScreen(
    sharedText: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(Unit) { viewModel.setSharedText(sharedText) }
    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add to Tavla", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

            if (!ui.signedIn) {
                Text(
                    "Sign in to Tavla first, then try sharing again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                return@Column
            }

            OutlinedTextField(
                value = ui.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Card title") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Board", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.boards.isEmpty()) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(ui.boards, key = { it.id }) { board ->
                        val selected = ui.selectedBoard?.id == board.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tavlaCard(isDark, cornerRadius = 14.dp)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                    } else Modifier,
                                )
                                .clickable { viewModel.selectBoard(board) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BoardTile(
                                board.color,
                                size = 36.dp,
                                iconName = ui.boardIcons[board.id],
                                imagePath = ui.boardImages[board.id],
                            )
                            Text(
                                board.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (ui.selectedBoard != null) {
                Text("List", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ui.isLoadingStacks) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.stacks, key = { it.id }) { stack ->
                            val selected = ui.selectedStack?.id == stack.id
                            Box(
                                modifier = Modifier
                                    .tavlaChip(isDark, selected = selected)
                                    .clickable { viewModel.selectStack(stack) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    stack.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ShareActions(onCancel, ui, viewModel)
        }
    }
}

@Composable
private fun ShareActions(onCancel: () -> Unit, ui: ShareUiState, viewModel: ShareViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = viewModel::createCard,
            enabled = ui.selectedStack != null && ui.title.isNotBlank() && !ui.isSaving,
            modifier = Modifier.weight(2f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (ui.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Add card")
        }
    }
}
