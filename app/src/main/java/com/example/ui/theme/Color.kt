package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// STROMRUF – METALLIC PREMIUM PALETTE (2026)
// Tiefe durch metallische Verläufe, Aura-Glow und Layer.
// ============================================================

// --- Neutrale Flächen (tiefer Premium-Dark-Look, mehr Kontrast) ---
val Obsidian       = Color(0xFF04060A)
val ObsidianSoft   = Color(0xFF0A0D14)
val Graphite       = Color(0xFF10151F)
val GraphiteLight  = Color(0xFF19202C)
val SlateElevated  = Color(0xFF1C2431)
val SlateHigh      = Color(0xFF273143)

// --- Metall-Kanten & Highlights (3D-Wirkung) ---
val BorderSubtle   = Color(0x1AFFFFFF)
val BorderStrong   = Color(0x33FFFFFF)
val EdgeHighlight  = Color(0x2EFFFFFF)
val EdgeShadow     = Color(0x59000000)

// --- Text (höherer Kontrast für Premium-Wirkung) ---
val TextPrimary    = Color(0xFFF5F8FC)
val TextSecondary  = Color(0xFFA3AEBD)
val TextMuted      = Color(0xFF636E7E)

// --- Funktionale Zustandsfarben (themeunabhängig) ---
val WarnAmber      = Color(0xFFF5B14C)
val WarnAmberDim   = Color(0x29F5B14C)
val CriticalRed    = Color(0xFFFF6B6B)
val CriticalRedDim = Color(0x29FF6B6B)
val SuccessGreen   = Color(0xFF34D399)
val SuccessDim     = Color(0x2934D399)

// ============================================================
// THEME-AKZENTE – je 3 Verlaufsstops (hell→kern→tief) + Aura
// ============================================================

// PLATINUM METAL – kühles Silber-Cyan
val Plat_Light  = Color(0xFFE8F4FF); val Plat_Core = Color(0xFF60C5FA)
val Plat_Deep   = Color(0xFF0284C7); val Plat_Aura = Color(0xFF38BDF8)

// GOLD LUXURY – Champagner-Gold
val Gold_Light  = Color(0xFFFFF0BF); val Gold_Core = Color(0xFFF2C14E)
val Gold_Deep   = Color(0xFFA8720A); val Gold_Aura = Color(0xFFFFD54F)

// CYBER VOLTAGE – Emerald-Energie
val Cyber_Light = Color(0xFFA7F3D0); val Cyber_Core = Color(0xFF34D399)
val Cyber_Deep  = Color(0xFF047857); val Cyber_Aura = Color(0xFF00FFA3)

// ROSE METAL – Rosé-Gold
val Rose_Light  = Color(0xFFFFD9EA); val Rose_Core = Color(0xFFF472B6)
val Rose_Deep   = Color(0xFF9D174D); val Rose_Aura = Color(0xFFFB7EC0)

// INDUSTRIAL STEEL – Blaustahl
val Steel_Light = Color(0xFFDBE7F5); val Steel_Core = Color(0xFF7C9CC4)
val Steel_Deep  = Color(0xFF2C4666); val Steel_Aura = Color(0xFF60A5FA)

// --- KI-Akzent (themeübergreifend) ---
val AiViolet    = Color(0xFFA78BFA)
val AiVioletDim = Color(0x29A78BFA)

// ============================================================
// FIRE / EMBER – für die feurige Hotbox-Aura & -Pulsierung
// Warme Glut-Töne, die mit dem Theme-Akzent verschmolzen werden.
// ============================================================
val Fire_Hot    = Color(0xFFFFE08A)  // heißer, fast weißer Kern
val Fire_Core   = Color(0xFFFF7A18)  // leuchtendes Orange
val Fire_Ember  = Color(0xFFFF9D3C)  // Glut-Orange
val Fire_Deep   = Color(0xFFE0320B)  // tiefes Rot-Orange

// --- Light Mode ---
val LightBackground = Color(0xFFF4F6F8)
val LightSurface    = Color(0xFFFFFFFF)
val LightText       = Color(0xFF10151B)
val LightSurfaceVariant = Color(0xFFE7ECF0)

// ============================================================
// LEGACY-ALIASE (Altcode-Kompatibilität – alte Screens/Dialoge)
// ============================================================
val Emerald        = Cyber_Core
val EmeraldDeep    = Cyber_Deep
val EmeraldDim     = SuccessDim
val Cyan           = Plat_Core
val CyanDim        = Color(0x2960C5FA)
val EnergyGold     = Gold_Core
val VoltBlue       = Plat_Core
val GridGreen      = Cyber_Deep
val PowerRose      = CriticalRed
val CircuitSlate   = Steel_Core
val DarkBackground = Obsidian
val DarkSurface    = Graphite
val DarkSurfaceVariant = SlateElevated
val OnBackgroundLight = LightText
val OnSurfaceLight    = LightText
val OnBackgroundDark  = TextPrimary
val OnSurfaceDark     = TextPrimary
val TextLight         = Color(0xFFFFFFFF)
val DisabledGray      = Color(0xFF525252)
val WarningYellow     = WarnAmber
val InfoBlue          = Plat_Core
