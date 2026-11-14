package com.brbrs.tavla.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brbrs.tavla.R

// Palette sampled from the Tavla icon: a lagoon-teal bokeh over near-black navy,
// with a warm ember glow on the right.

// ── Dark palette ──────────────────────────────────────────────────────────────
val NavyDeep      = Color(0xFF080D16)
val NavyMid       = Color(0xFF0E1622)
val NavySurface   = Color(0xFF152030)
val NavySurface2  = Color(0xFF1C2A3D)
val GlassBorder   = Color(0x22FFFFFF)
val GlowLagoon    = Color(0x2E3BB8C8)   // ambient glow tint behind the header

// ── Lagoon accent (Tavla) ─────────────────────────────────────────────────────
val LagoonPrimary  = Color(0xFF3BB8C8)  // pops on dark
val LagoonDeep     = Color(0xFF2091A3)  // sampled straight from the icon
val LagoonLight    = Color(0xFF0E7383)  // light-mode primary (AA on light surfaces)
val EmberSecondary = Color(0xFFE0A06A)  // warm bokeh, for secondary accents

/** The active accent for the current mode -- dark: [LagoonPrimary], light: [LagoonLight]. */
val AccentPrimary = LagoonPrimary

// ── Semantic ──────────────────────────────────────────────────────────────────
val SlateText    = Color(0xFF94A3B8)
val White        = Color(0xFFF1F5F9)
val ErrorRed     = Color(0xFFEF5350)
val GreenOk      = Color(0xFF4CAF78)
val AmberWarn    = Color(0xFFFFB300)

// ── Light palette ─────────────────────────────────────────────────────────────
val LightBg         = Color(0xFFEEF4F5)
val LightSurface    = Color(0xFFF9FCFC)
val LightSurface2   = Color(0xFFE2ECEE)
val LightSurface3   = Color(0xFFD3E2E5)
val LightBorderSoft = Color(0x220E7383)
val LightBorderMed  = Color(0x330E7383)
val LightText       = Color(0xFF0D1B20)
val LightTextDim    = Color(0xFF4A5E64)
val LightGlow       = Color(0x2A2091A3)

val LocalIsDark = compositionLocalOf { true }
val LocalIsOled = compositionLocalOf { false }

// ── Shapes ────────────────────────────────────────────────────────────────────
// radius-md (suite design system default for SettingsCard/most list cards) / radius-lg (FAB).
val CardShape = RoundedCornerShape(16.dp)
val FabShape  = RoundedCornerShape(20.dp)

// ── Color schemes ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = LagoonPrimary,
    onPrimary            = NavyDeep,
    primaryContainer     = Color(0xFF12323A),
    onPrimaryContainer   = Color(0xFFB4ECF2),
    secondary            = EmberSecondary,
    onSecondary          = NavyDeep,
    secondaryContainer   = Color(0xFF2E2118),
    onSecondaryContainer = Color(0xFFF2D2B6),
    tertiary             = AmberWarn,
    onTertiary           = NavyDeep,
    background           = NavyDeep,
    onBackground         = White,
    surface              = NavyMid,
    onSurface            = White,
    surfaceVariant       = NavySurface,
    onSurfaceVariant     = SlateText,
    surfaceContainer     = NavySurface2,
    surfaceContainerHigh = NavySurface2,
    outline              = GlassBorder,
    outlineVariant       = Color(0x12FFFFFF),
    error                = ErrorRed,
    scrim                = Color(0x99000000),
)

private val LightColorScheme = lightColorScheme(
    primary              = LagoonLight,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFCFEEF2),
    onPrimaryContainer   = Color(0xFF002A31),
    secondary            = Color(0xFFA0602C),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFF6E2D0),
    onSecondaryContainer = Color(0xFF3A1E06),
    tertiary             = Color(0xFFB26A00),
    onTertiary           = Color.White,
    background           = LightBg,
    onBackground         = LightText,
    surface              = LightSurface,
    onSurface            = LightText,
    surfaceVariant       = LightSurface2,
    onSurfaceVariant     = LightTextDim,
    surfaceContainer     = LightSurface3,
    surfaceContainerHigh = LightSurface,
    outline              = LightBorderMed,
    outlineVariant       = LightBorderSoft,
    error                = Color(0xFFB00020),
    scrim                = Color(0x66000000),
)

// True-black OLED variant -- same accents, pure-black surfaces.
private val OledColorScheme = DarkColorScheme.copy(
    background       = Color.Black,
    surface          = Color.Black,
    surfaceVariant   = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
)

// ── Font families ─────────────────────────────────────────────────────────────

// DM Serif Display -- display/headline styles (screen titles, board and card titles)
val DMSerifDisplayFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
)

// Inter Tight -- all UI body/label/title text
val InterTightFamily = FontFamily(
    Font(R.font.inter_tight_thin,        FontWeight.Thin),
    Font(R.font.inter_tight_extra_light, FontWeight.ExtraLight),
    Font(R.font.inter_tight_light,       FontWeight.Light),
    Font(R.font.inter_tight_regular,     FontWeight.Normal),
    Font(R.font.inter_tight_medium,      FontWeight.Medium),
    Font(R.font.inter_tight_semi_bold,   FontWeight.SemiBold),
    Font(R.font.inter_tight_bold,        FontWeight.Bold),
    Font(R.font.inter_tight_extra_bold,  FontWeight.ExtraBold),
    Font(R.font.inter_tight_black,       FontWeight.Black),
)

// ── Typography ────────────────────────────────────────────────────────────────

fun buildTypography(displayFont: FontFamily, bodyFont: FontFamily) = Typography(
    displayLarge   = TextStyle(fontFamily = displayFont, fontSize = 44.sp, fontWeight = FontWeight.Normal,   lineHeight = 52.sp, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = displayFont, fontSize = 34.sp, fontWeight = FontWeight.Normal,   lineHeight = 42.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = displayFont, fontSize = 26.sp, fontWeight = FontWeight.Normal,   lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontFamily = displayFont, fontSize = 22.sp, fontWeight = FontWeight.Normal,   lineHeight = 30.sp),
    // title-*/label-* line-heights below were missing (TextUnit.Unspecified) -- added to match the
    // suite design system's type tokens exactly.
    titleLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
    titleSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Medium,   lineHeight = 18.sp, letterSpacing = (-0.1).sp),
    bodyLarge      = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.Normal,   lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    bodyMedium     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp, letterSpacing = (-0.05).sp),
    bodySmall      = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.Normal,   lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.04.sp),
    labelMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp, letterSpacing = 0.06.sp),
    labelSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 10.sp, fontWeight = FontWeight.Medium,   lineHeight = 12.sp, letterSpacing = 0.08.sp),
)

// Default: system serif + system sans-serif
val AppTypography = buildTypography(FontFamily.Serif, FontFamily.SansSerif)

// Paired: DM Serif Display headlines + Inter Tight body
val AppTypographyPaired = buildTypography(DMSerifDisplayFamily, InterTightFamily)

fun scaledTypography(scale: Float, useCustomFont: Boolean): Typography {
    val base = if (useCustomFont) AppTypographyPaired else AppTypography
    fun TextStyle.scaled() = copy(
        fontSize   = fontSize * scale,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    )
    return Typography(
        displayLarge   = base.displayLarge.scaled(),
        headlineLarge  = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall  = base.headlineSmall.scaled(),
        titleLarge     = base.titleLarge.scaled(),
        titleMedium    = base.titleMedium.scaled(),
        titleSmall     = base.titleSmall.scaled(),
        bodyLarge      = base.bodyLarge.scaled(),
        bodyMedium     = base.bodyMedium.scaled(),
        bodySmall      = base.bodySmall.scaled(),
        labelLarge     = base.labelLarge.scaled(),
        labelMedium    = base.labelMedium.scaled(),
        labelSmall     = base.labelSmall.scaled(),
    )
}

@Composable
fun TavlaTheme(
    isDark: Boolean = true,
    isOled: Boolean = false,
    textScale: Float = 1.0f,
    useCustomFont: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = when {
        textScale != 1.0f -> scaledTypography(textScale, useCustomFont)
        useCustomFont     -> AppTypographyPaired
        else              -> AppTypography
    }
    val colorScheme = when {
        isOled -> OledColorScheme
        isDark -> DarkColorScheme
        else   -> LightColorScheme
    }
    CompositionLocalProvider(LocalIsDark provides isDark, LocalIsOled provides isOled) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
