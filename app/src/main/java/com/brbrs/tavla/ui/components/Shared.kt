package com.brbrs.tavla.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brbrs.tavla.data.DeckLabel
import com.brbrs.tavla.data.DeckUser
import com.brbrs.tavla.ui.theme.AmberWarn
import com.brbrs.tavla.ui.theme.GlowLagoon
import com.brbrs.tavla.ui.theme.LightBg
import com.brbrs.tavla.ui.theme.LightBorderSoft
import com.brbrs.tavla.ui.theme.LightGlow
import com.brbrs.tavla.ui.theme.LightSurface
import com.brbrs.tavla.ui.theme.LightSurface2
import com.brbrs.tavla.ui.theme.LocalIsDark
import com.brbrs.tavla.ui.theme.LocalIsOled
import com.brbrs.tavla.ui.theme.NavyDeep
import com.brbrs.tavla.ui.theme.NavyMid
import com.brbrs.tavla.util.DueState
import com.brbrs.tavla.util.dueState
import com.brbrs.tavla.util.formatDue

/** Parses a Deck hex color ("31CC7C" or "#31CC7C"); falls back to [fallback] on garbage. */
fun colorFromHex(hex: String, fallback: Color = Color(0xFF0082C9)): Color {
    val clean = hex.removePrefix("#").trim()
    return runCatching {
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            3 -> Color(("FF" + clean.map { "$it$it" }.joinToString("")).toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

fun Color.toHex(): String {
    fun c(v: Float) = (v * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return (c(red) + c(green) + c(blue)).uppercase()
}

/** Board and label colors offered when creating them -- Deck's own defaults plus the suite accents. */
val DeckColorPalette = listOf(
    "0082C9", "3BB8C8", "31CC7C", "4CAF78", "E0A06A", "FF8F00",
    "E53935", "E37D9C", "AB47BC", "6FA0E8", "A8726A", "7C8B99",
)

// ── Screen background ────────────────────────────────────────────────────────

/**
 * [boardColorHex] and [coverImagePath] let a per-board screen (the board's own list view, or a
 * card inside it) tint the background to that board instead of the generic app gradient: a
 * frosted (blurred, scrim-tinted) version of the board's cover image when it has one, or a soft
 * glow of the board's own color otherwise. Leave both null for the generic background (used by
 * the top-level Boards screen, which shows many boards at once and so has no single board to tint
 * toward).
 */
@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    boardColorHex: String? = null,
    coverImagePath: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalIsDark.current
    val isOled = LocalIsOled.current
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isOled -> Box(Modifier.fillMaxSize().background(Color.Black))
            coverImagePath != null -> FrostedCoverBackground(coverImagePath, isDark)
            boardColorHex != null -> BoardColorBackground(colorFromHex(boardColorHex), isDark)
            else -> DefaultAppBackground(isDark)
        }
        content()
    }
}

@Composable
private fun DefaultAppBackground(isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Brush.verticalGradient(0f to NavyDeep, 0.45f to NavyMid, 1f to NavyDeep)
                else Brush.verticalGradient(0f to LightBg, 0.5f to Color(0xFFE8F1F2), 1f to LightSurface2)
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(if (isDark) GlowLagoon else LightGlow, Color.Transparent),
                        radius = if (isDark) 650f else 750f,
                    )
                )
        )
    }
}

/** Same base gradient as [DefaultAppBackground], but the glow is tinted to the board's own color. */
@Composable
private fun BoardColorBackground(color: Color, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Brush.verticalGradient(0f to NavyDeep, 0.45f to NavyMid, 1f to NavyDeep)
                else Brush.verticalGradient(0f to LightBg, 0.5f to Color(0xFFE8F1F2), 1f to LightSurface2)
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = if (isDark) 0.38f else 0.22f), Color.Transparent),
                        radius = if (isDark) 900f else 1000f,
                    )
                )
        )
    }
}

/**
 * The board's cover image, blurred and tinted so it reads as a frosted pane behind the UI rather
 * than a photo competing with it. True gaussian blur ([Modifier.blur]) only renders on API 31+;
 * below that we decode the source at a tiny resolution instead and let the upscale itself stand
 * in for the blur, which looks close enough at the radius this screen needs.
 */
@Composable
private fun FrostedCoverBackground(path: String, isDark: Boolean) {
    val blurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val targetEdge = if (blurSupported) 480 else 48
    val bitmap = remember(path, targetEdge) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            val opts = android.graphics.BitmapFactory.Options().apply {
                var s = 1
                while (bounds.outHeight / (s * 2) >= targetEdge && bounds.outWidth / (s * 2) >= targetEdge) s *= 2
                inSampleSize = s
            }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) NavyDeep else LightBg)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = if (blurSupported) FilterQuality.High else FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurSupported) it.blur(60.dp) else it },
            )
        }
        // Tints the blurred photo to match the rest of the UI and keeps foreground content
        // legible -- the same role the "material" tint plays over a blurred background on iOS/macOS.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            listOf(NavyDeep.copy(alpha = 0.72f), NavyMid.copy(alpha = 0.78f), NavyDeep.copy(alpha = 0.82f))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(LightBg.copy(alpha = 0.78f), Color(0xFFE8F1F2).copy(alpha = 0.72f), LightSurface2.copy(alpha = 0.82f))
                        )
                    }
                )
        )
    }
}

// ── Top bar icon ─────────────────────────────────────────────────────────────

@Composable
fun TopBarIcon(icon: ImageVector, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Board color tile (#02: a chosen Material icon, or the kanban glyph by default) ─────────────

@Composable
fun BoardTile(colorHex: String, size: Dp = 44.dp, iconName: String? = null, imagePath: String? = null) {
    val color = colorFromHex(colorHex)
    // A cover image, when one is cached for this board, wins over the icon and the glyph.
    // [imagePath] is an absolute path to the cached file (see BoardCoverRepository).
    val cover = androidx.compose.runtime.remember(imagePath) {
        imagePath?.let { path ->
            runCatching {
                // Downsample to roughly the tile size so a large cover doesn't decode in full on
                // the main thread for a small avatar.
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return@runCatching null
                val opts = android.graphics.BitmapFactory.Options().apply {
                    var s = 1
                    while (bounds.outHeight / (s * 2) >= 256 && bounds.outWidth / (s * 2) >= 256) s *= 2
                    inSampleSize = s
                }
                android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = if (iconName != null) Alignment.Center else Alignment.TopCenter,
    ) {
        if (cover != null) {
            androidx.compose.foundation.Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (iconName != null) {
            Icon(
                imageVector = com.brbrs.tavla.icons.IconCatalog.iconFor(iconName),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.56f),
            )
        } else {
            Row(
                modifier = Modifier.padding(top = size * 0.25f),
                horizontalArrangement = Arrangement.spacedBy(size * 0.07f),
            ) {
                Box(Modifier.width(size * 0.14f).height(size * 0.5f).clip(RoundedCornerShape(2.dp)).background(color))
                Box(Modifier.width(size * 0.14f).height(size * 0.34f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.7f)))
                Box(Modifier.width(size * 0.14f).height(size * 0.2f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.45f)))
            }
        }
    }
}

// ── Board tile background (#03: frosted cover or board-color glow behind a whole tile/row on the
//    Boards overview screen -- the same treatment [ScreenBackground] gives a full screen, scaled
//    down to one card) ──────────────────────────────────────────────────────

/** Same clip/border [com.brbrs.tavla.ui.theme.tavlaCard] uses, minus its own flat background --
 *  the fill comes from [BoardTileBackground] instead. */
private fun Modifier.tavlaTileBase(isDark: Boolean, cornerRadius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this.clip(shape).border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    } else {
        this
            .shadow(elevation = 2.dp, shape = shape, ambientColor = Color(0x282091A3), spotColor = Color(0x142091A3))
            .clip(shape)
            .border(1.dp, LightBorderSoft, shape)
    }
}

/** A board tile/row on the Boards overview screen, with its own frosted cover image or
 *  board-color glow as a background -- a tile-scale version of [FrostedCoverBackground] /
 *  [BoardColorBackground]. */
@Composable
fun BoardTileBackground(
    colorHex: String,
    imagePath: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.tavlaTileBase(isDark, cornerRadius)) {
        if (imagePath != null) {
            TileFrostedCover(imagePath, isDark)
        } else {
            val color = colorFromHex(colorHex)
            Box(Modifier.matchParentSize().background(if (isDark) Color.White.copy(alpha = 0.06f) else LightSurface))
            Box(
                modifier = Modifier.matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = if (isDark) 0.40f else 0.26f), Color.Transparent),
                            radius = 220f,
                        )
                    )
            )
        }
        content()
    }
}

/** Tile-scale frosted cover: a small, blurred (API 31+) or tiny-upscaled (fallback) crop of the
 *  board's cover image, tinted just enough to keep the tile's title and count legible over it.
 *  Uses `Modifier.matchParentSize()` throughout -- not [fillMaxSize] --
 *  so this layer sizes itself to the content (the title/count Row or Column) the enclosing
 *  [BoardTileBackground] Box wraps, rather than expanding to fill all the space the lazy
 *  list/grid happens to make available. */
@Composable
private fun BoxScope.TileFrostedCover(path: String, isDark: Boolean) {
    val blurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val targetEdge = if (blurSupported) 220 else 32
    val bitmap = remember(path, targetEdge) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            val opts = android.graphics.BitmapFactory.Options().apply {
                var s = 1
                while (bounds.outHeight / (s * 2) >= targetEdge && bounds.outWidth / (s * 2) >= targetEdge) s *= 2
                inSampleSize = s
            }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier = Modifier.matchParentSize().background(if (isDark) Color.White.copy(alpha = 0.06f) else LightSurface)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = if (blurSupported) FilterQuality.High else FilterQuality.Low,
                modifier = Modifier.matchParentSize()
                    .let { if (blurSupported) it.blur(28.dp) else it },
            )
        }
        Box(
            modifier = Modifier.matchParentSize()
                .background(if (isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f))
        )
    }
}

// ── Labels ───────────────────────────────────────────────────────────────────

@Composable
fun LabelChip(label: DeckLabel, modifier: Modifier = Modifier, selected: Boolean = true) {
    val isDark = LocalIsDark.current
    val base = colorFromHex(label.color)
    val fg = if (isDark) lerp(base, Color.White, 0.45f) else lerp(base, Color.Black, 0.45f)
    val bg = base.copy(alpha = if (selected) (if (isDark) 0.20f else 0.16f) else 0.06f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) fg else fg.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Avatars ──────────────────────────────────────────────────────────────────

private val avatarColors = listOf(
    Color(0xFF2E5A66), Color(0xFF5A3E4A), Color(0xFF3E4A6E), Color(0xFF5E4B2E), Color(0xFF34573F), Color(0xFF4F3B66),
)

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
fun UserAvatar(user: DeckUser, size: Dp = 26.dp) {
    val avatarViewModel: com.brbrs.tavla.ui.viewmodels.UserAvatarViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val bitmap = avatarViewModel.avatarFor(user.uid)
    val bg = avatarColors[(user.uid.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                initials(user.displayName.ifBlank { user.uid }),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AvatarStack(users: List<DeckUser>, size: Dp = 24.dp, max: Int = 3) {
    val step = size - 7.dp
    val shown = users.take(max)
    Box {
        shown.forEachIndexed { i, u ->
            Box(Modifier.padding(start = step * i)) { UserAvatar(u, size) }
        }
        if (users.size > max) {
            Box(
                modifier = Modifier
                    .padding(start = step * shown.size)
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${users.size - max}", fontSize = (size.value * 0.38f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Due date ─────────────────────────────────────────────────────────────────

@Composable
fun dueColor(dueMs: Long): Color = when (dueState(dueMs)) {
    DueState.Overdue -> MaterialTheme.colorScheme.error
    DueState.Today   -> if (LocalIsDark.current) AmberWarn else Color(0xFFB26A00)
    DueState.Soon    -> MaterialTheme.colorScheme.secondary
    DueState.Later   -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun DueBadge(dueMs: Long) {
    val color = dueColor(dueMs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Schedule, null, tint = color, modifier = Modifier.size(14.dp))
        Text(formatDue(dueMs), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Human-readable error for network failures, shared by all screens. */
fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is com.brbrs.tavla.network.DeckApiException && e.code == 401 -> "Nextcloud rejected the login. Sign in again from Settings."
        e is com.brbrs.tavla.network.DeckApiException && e.code == 403 -> "You don't have permission to do that on this board."
        e is com.brbrs.tavla.network.DeckApiException && e.code == 404 -> "Not found on the server. It may have been deleted."
        msg.contains("Unable to resolve host") -> "Offline -- showing what's saved on this device."
        msg.contains("timeout", ignoreCase = true) -> "The server took too long to answer."
        msg.contains("CLEARTEXT") -> "This server requires HTTPS."
        msg.isBlank() -> "Something went wrong (${e.javaClass.simpleName})."
        else -> msg
    }
}
