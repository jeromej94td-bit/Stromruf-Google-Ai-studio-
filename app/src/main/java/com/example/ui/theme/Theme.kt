package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================
// STROMRUF THEME – 5 echte Premium-Themes.
// Jedes Theme färbt die GESAMTE App: MaterialTheme-Scheme,
// Komponenten (über LocalThemeConfig) und Hintergrund.
// ============================================================

data class ThemeStyleConfig(
    val primaryColor: Color,      // Kern-Akzent
    val secondaryColor: Color,    // Zweit-Akzent
    val tertiaryColor: Color,     // KI / Besonderes
    val baseBackground: Color,
    val glowColor1: Color,        // Hintergrund-Glow 1
    val glowColor2: Color,        // Hintergrund-Glow 2
    val name: String,
    val cardBackground: Color,
    val cardBorder: Color,
    val metalAccent: Color = Plat_Core,
    val glassOverlay: Color = SlateElevated,
    // Metallischer Verlauf (hell → kern → tief) + Aura für Puls-Effekte
    val gradLight: Color = primaryColor,
    val gradDeep: Color = primaryColor,
    val auraColor: Color = primaryColor,
    val onAccent: Color = Color(0xFF06120C)
)

private val Platinum = ThemeStyleConfig(
    primaryColor = Plat_Core, secondaryColor = Steel_Light, tertiaryColor = AiViolet,
    baseBackground = Obsidian, glowColor1 = Plat_Deep, glowColor2 = Steel_Deep,
    name = "Platinum Metal", cardBackground = Graphite, cardBorder = BorderSubtle,
    metalAccent = Plat_Light, gradLight = Plat_Light, gradDeep = Plat_Deep,
    auraColor = Plat_Aura, onAccent = Color(0xFF06202E)
)
private val GoldLux = ThemeStyleConfig(
    primaryColor = Gold_Core, secondaryColor = Color(0xFFE8C87E), tertiaryColor = AiViolet,
    baseBackground = Color(0xFF0C0906), glowColor1 = Gold_Deep, glowColor2 = Color(0xFF4A3208),
    name = "Gold Luxury", cardBackground = Color(0xFF17130C), cardBorder = Color(0x1FFFD87A),
    metalAccent = Gold_Light, gradLight = Gold_Light, gradDeep = Gold_Deep,
    auraColor = Gold_Aura, onAccent = Color(0xFF241703)
)
private val CyberVolt = ThemeStyleConfig(
    primaryColor = Cyber_Core, secondaryColor = Plat_Core, tertiaryColor = AiViolet,
    baseBackground = Color(0xFF060B09), glowColor1 = Cyber_Deep, glowColor2 = Plat_Deep,
    name = "Cyber Voltage", cardBackground = Color(0xFF101915), cardBorder = Color(0x1F34D399),
    metalAccent = Cyber_Light, gradLight = Cyber_Light, gradDeep = Cyber_Deep,
    auraColor = Cyber_Aura, onAccent = Color(0xFF03271A)
)
private val RoseMetal = ThemeStyleConfig(
    primaryColor = Rose_Core, secondaryColor = Color(0xFFE9B8CE), tertiaryColor = AiViolet,
    baseBackground = Color(0xFF0D070B), glowColor1 = Rose_Deep, glowColor2 = Color(0xFF3B1027),
    name = "Rose Metal", cardBackground = Color(0xFF191016), cardBorder = Color(0x1FF472B6),
    metalAccent = Rose_Light, gradLight = Rose_Light, gradDeep = Rose_Deep,
    auraColor = Rose_Aura, onAccent = Color(0xFF2E0A1C)
)
private val Industrial = ThemeStyleConfig(
    primaryColor = Steel_Aura, secondaryColor = Steel_Light, tertiaryColor = AiViolet,
    baseBackground = Obsidian, glowColor1 = Steel_Deep, glowColor2 = Color(0xFF1A2C44),
    name = "Industrial Steel", cardBackground = Color(0xFF121821), cardBorder = BorderSubtle,
    metalAccent = Steel_Light, gradLight = Steel_Light, gradDeep = Steel_Deep,
    auraColor = Steel_Aura, onAccent = Color(0xFF0A1826)
)

fun getThemeStyleConfig(style: String): ThemeStyleConfig = when (style) {
    "gold_luxury"      -> GoldLux
    "cyber_voltage"    -> CyberVolt
    "rose_metal"       -> RoseMetal
    "industrial_steel" -> Industrial
    else               -> Platinum   // "platinum_metal" + Fallback
}

val LocalThemeConfig = staticCompositionLocalOf { Platinum }

// Bequeme Composable-Getter – überall dort nutzbar, wo früher
// feste Farben standen. Folgen automatisch dem gewählten Theme.
val ThemeAccent: Color    @Composable get() = LocalThemeConfig.current.primaryColor
val ThemeSecondary: Color @Composable get() = LocalThemeConfig.current.secondaryColor
val ThemeAura: Color      @Composable get() = LocalThemeConfig.current.auraColor

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeStyle: String = "platinum_metal",
    content: @Composable () -> Unit,
) {
    val config = getThemeStyleConfig(themeStyle)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = config.primaryColor,
            onPrimary = config.onAccent,
            primaryContainer = config.primaryColor.copy(alpha = 0.16f),
            onPrimaryContainer = config.primaryColor,
            secondary = config.secondaryColor,
            onSecondary = config.onAccent,
            secondaryContainer = config.secondaryColor.copy(alpha = 0.16f),
            onSecondaryContainer = config.secondaryColor,
            tertiary = config.tertiaryColor,
            onTertiary = Color(0xFF1E1633),
            tertiaryContainer = AiVioletDim,
            onTertiaryContainer = config.tertiaryColor,
            background = config.baseBackground,
            onBackground = TextPrimary,
            surface = config.cardBackground,
            onSurface = TextPrimary,
            surfaceVariant = SlateElevated,
            onSurfaceVariant = TextSecondary,
            outline = config.cardBorder,
            outlineVariant = config.cardBorder,
            error = CriticalRed,
            onError = Color(0xFF33110F),
            errorContainer = CriticalRedDim,
            onErrorContainer = CriticalRed
        )
    } else {
        lightColorScheme(
            primary = config.gradDeep,
            secondary = config.secondaryColor,
            tertiary = config.tertiaryColor,
            background = LightBackground,
            onBackground = LightText,
            surface = LightSurface,
            onSurface = LightText,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = Color(0xFF4A5560),
            outline = Color(0x1F10151B),
            error = Color(0xFFD64545)
        )
    }

    CompositionLocalProvider(LocalThemeConfig provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
