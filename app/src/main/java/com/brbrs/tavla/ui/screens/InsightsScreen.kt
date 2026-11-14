package com.brbrs.tavla.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.tavla.ui.components.EmptyState
import com.brbrs.tavla.ui.components.ScreenBackground
import com.brbrs.tavla.ui.components.SectionLabel
import com.brbrs.tavla.ui.components.TopBarIcon
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.glassCard
import com.brbrs.tavla.ui.viewmodels.InsightsUiState
import com.brbrs.tavla.ui.viewmodels.InsightsViewModel
import com.brbrs.tavla.ui.viewmodels.ListLoad
import com.brbrs.tavla.ui.viewmodels.WeekBucket
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { onBack() }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Insights", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    ui.board?.title?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (ui.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            if (ui.totalCards == 0) {
                EmptyState(
                    icon = Icons.Outlined.Schedule,
                    title = "Nothing to show yet",
                    body = "Once this board has some cards -- especially a few marked done -- insights will appear here.",
                )
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // -- Headline tiles --
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeadlineTile(
                        modifier = Modifier.weight(1f), isDark = isDark,
                        icon = Icons.Outlined.CheckCircle, value = "${ui.doneCards}", label = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    HeadlineTile(
                        modifier = Modifier.weight(1f), isDark = isDark,
                        icon = Icons.Outlined.Warning, value = "${ui.overdueNow}", label = "Overdue now",
                        tint = if (ui.overdueNow > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HeadlineTile(
                        modifier = Modifier.weight(1f), isDark = isDark,
                        icon = Icons.Outlined.Timer,
                        value = ui.avgDaysToComplete?.let { formatDays(it) } ?: "--",
                        label = "Avg. to finish",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!ui.hasTimingData) {
                    Text(
                        "\"Avg. to finish\" needs cards with both a creation date and a done date from Deck -- it'll fill in as cards get marked done.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // -- Done per week --
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Cards finished, last 8 weeks")
                    WeekBarChart(ui.doneByWeek, isDark, barColor = MaterialTheme.colorScheme.primary)
                }

                // -- Overdue by week --
                if (ui.overdueByWeek.any { it.count > 0 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Still-open cards, by the week they were due")
                        WeekBarChart(ui.overdueByWeek, isDark, barColor = MaterialTheme.colorScheme.error)
                        Text(
                            "Based on what's open right now, not a historical log -- a good read on which weeks' work is backing up.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // -- Busiest lists --
                if (ui.busiestLists.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Busiest lists")
                        BusiestListsChart(ui.busiestLists, isDark)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatDays(days: Double): String = when {
    days < 1.0 -> "<1 day"
    days < 2.0 -> "1 day"
    days < 14.0 -> "${days.roundToInt()} days"
    else -> "${(days / 7.0).roundToInt()} wks"
}

@Composable
private fun HeadlineTile(
    modifier: Modifier,
    isDark: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
) {
    Column(
        modifier = modifier
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small, dependency-free bar chart: 8 equal-width bars, tallest one anchoring the scale. */
@Composable
private fun WeekBarChart(buckets: List<WeekBucket>, isDark: Boolean, barColor: Color) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.06f else 0.03f)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { bucket ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (bucket.count > 0) {
                    Text(
                        "${bucket.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                // Column.weight only splits space proportionally when there's more than one
                // weighted child to compare against -- the empty spacer above the bar is what
                // makes the bar's own weight (and so its height) actually mean something.
                // Modifier.weight() requires a value strictly greater than zero, so both the bar
                // and the empty spacer above it need a floor -- without it, the tallest bucket
                // (fraction == 1f, spacer weight == 0f) crashes the whole screen.
                val fraction = (bucket.count.toFloat() / maxCount.toFloat()).coerceIn(0.03f, 0.97f)
                Spacer(modifier = Modifier.weight(1f - fraction))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .weight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (bucket.count > 0) barColor.copy(alpha = 0.85f) else barColor.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    bucket.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BusiestListsChart(lists: List<ListLoad>, isDark: Boolean) {
    val maxCount = (lists.maxOfOrNull { it.openCount } ?: 0).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.06f else 0.03f)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lists.forEach { list ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        list.stackTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${list.openCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(list.openCount.toFloat() / maxCount.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
