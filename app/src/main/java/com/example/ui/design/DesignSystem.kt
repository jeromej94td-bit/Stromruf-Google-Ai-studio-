package com.example.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.Fire_Core
import com.example.ui.theme.Fire_Deep
import com.example.ui.theme.Fire_Ember
import com.example.ui.theme.Fire_Hot
import kotlin.math.sin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AiViolet
import com.example.ui.theme.AiVioletDim
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.CriticalRedDim
import com.example.ui.theme.EdgeHighlight
import com.example.ui.theme.GraphiteLight
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateHigh
import com.example.ui.theme.SuccessDim
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarnAmber
import com.example.ui.theme.WarnAmberDim
import androidx.compose.ui.graphics.graphicsLayer

// ============================================================
// STROMRUF DESIGN SYSTEM 2.0 – METALLIC & AURA
// Jede Komponente folgt automatisch dem gewählten Theme.
// Signatur-kompatibel zur Vorversion.
// ============================================================

object Dim {
    val screenPad = 16.dp
    val cardRadius = 20.dp
    val controlRadius = 14.dp
    val gap = 12.dp
    val gapL = 20.dp
}

// ---------- Metallischer Verlauf (hell → kern → tief) ----------
@Composable
fun metallicBrush(): Brush {
    val c = LocalThemeConfig.current
    return Brush.verticalGradient(
        0f to c.gradLight.copy(alpha = 0.95f),
        0.35f to c.primaryColor,
        1f to c.gradDeep
    )
}

// ---------- Pulsierende Aura ----------
/**
 * Zeichnet eine sanft pulsierende Aura hinter dem Element.
 * Für aktive Navigations-Items, Chips, Segmente und Fokus-Buttons.
 */
fun Modifier.pulsingAura(
    color: Color,
    enabled: Boolean = true,
    maxRadiusFactor: Float = 0.85f
): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "aura")
    val pulse by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1350, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "auraPulse"
    )
    drawBehind {
        // Kräftigere, weiter atmende Aura mit doppeltem Glow-Layer.
        val r = size.maxDimension * (maxRadiusFactor + 0.5f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.42f + 0.28f * pulse),
                    color.copy(alpha = 0.16f + 0.10f * pulse),
                    color.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = center, radius = r
            ),
            radius = r, center = center
        )
        // Innerer, hellerer Kern für mehr Präsenz.
        val rInner = size.maxDimension * (maxRadiusFactor * 0.55f + 0.18f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.30f * pulse),
                    Color.Transparent
                ),
                center = center, radius = rInner
            ),
            radius = rInner, center = center
        )
    }
}

/** Sanftes Atmen (Scale) – für den dominanten Fokus-Anruf-Button. Kräftiger als zuvor. */
fun Modifier.breathing(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(
        initialValue = 0.985f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "breathScale"
    )
    scale(s)
}

// ---------- Feurige Hotbox-Aura ----------
/**
 * Meisterhafte, lebendige Feuer-Aura für die Hotbox.
 * Mehrere überlagerte Glut-Layer flackern organisch (mehrere Sinus-Phasen),
 * verschmolzen mit dem aktuellen Theme-Akzent – „in den entsprechenden Farben".
 *
 * @param accent Theme-Akzentfarbe, mit der die Glut gemischt wird.
 * @param intensity 0f..1f – Grundstärke des Feuers.
 */
fun Modifier.fireAura(
    accent: Color,
    enabled: Boolean = true,
    intensity: Float = 1f
): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "fire")
    // Grundpuls (Ein-/Ausatmen der Glut)
    val breath by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fireBreath"
    )
    // Schnelles Flackern (unregelmäßig durch Überlagerung)
    val flickerA by t.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(520, easing = androidx.compose.animation.core.LinearEasing)),
        label = "flickA"
    )
    val flickerB by t.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(830, easing = androidx.compose.animation.core.LinearEasing)),
        label = "flickB"
    )

    // Glut-Töne mit Theme-Akzent verschmelzen (30 % Akzent für „entsprechende Farben")
    val hot = lerp(Fire_Hot, accent, 0.18f)
    val core = lerp(Fire_Core, accent, 0.30f)
    val ember = lerp(Fire_Ember, accent, 0.30f)
    val deep = lerp(Fire_Deep, accent, 0.22f)

    drawBehind {
        val flicker = (sin(flickerA) * 0.5f + sin(flickerB * 1.3f) * 0.5f) // -1..1
        val f = (0.5f + 0.5f * flicker)                                    // 0..1
        val amp = intensity * (0.55f + 0.45f * breath)                     // Gesamtamplitude

        val cx = center.x
        val cy = center.y
        // Hitze steigt: Zentrum leicht nach oben versetzt + Flacker-Wobble
        val riseY = cy - size.height * (0.06f + 0.05f * f)
        val warmCenter = Offset(cx + size.width * 0.03f * (flicker), riseY)

        // 1) Weiter, weicher Außen-Schein (Wärmestrahlung)
        val rOuter = size.maxDimension * (0.95f + 0.45f * breath + 0.10f * f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    core.copy(alpha = 0.30f * amp),
                    ember.copy(alpha = 0.14f * amp),
                    deep.copy(alpha = 0.05f * amp),
                    Color.Transparent
                ),
                center = warmCenter, radius = rOuter
            ),
            radius = rOuter, center = warmCenter
        )

        // 2) Mittlerer Glut-Ring
        val rMid = size.maxDimension * (0.70f + 0.22f * f + 0.12f * breath)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    core.copy(alpha = 0.42f * amp),
                    ember.copy(alpha = 0.22f * amp),
                    Color.Transparent
                ),
                center = warmCenter, radius = rMid
            ),
            radius = rMid, center = warmCenter
        )

        // 3) Heißer, heller Kern (das eigentliche Feuer)
        val rCore = size.maxDimension * (0.44f + 0.14f * f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    hot.copy(alpha = 0.55f * amp),
                    core.copy(alpha = 0.30f * amp),
                    Color.Transparent
                ),
                center = warmCenter, radius = rCore
            ),
            radius = rCore, center = warmCenter
        )
    }
}

/** Feuriger Verlaufs-Brush für Hotbox-Flächen (Glut, mit Theme-Akzent gemischt). */
fun fireBrush(accent: Color): Brush = Brush.verticalGradient(
    0f to lerp(Fire_Hot, accent, 0.15f),
    0.45f to lerp(Fire_Core, accent, 0.28f),
    1f to lerp(Fire_Deep, accent, 0.20f)
)

// ============================================================
// EPISCHE FEUER-AURA – „gottgleiche" Hotbox-Inszenierung
// ============================================================
/**
 * Die große Bühne für die zentrale Hotbox-Funktion.
 * Ein mehrschichtiges, lebendiges Feuer, das weit über das Element hinausgeht:
 *
 *   1. Wärme-Korona  – drei weite, atmende Glow-Schichten (Strahlungshitze)
 *   2. Flammenzungen – neun tanzende Zungen über der Oberkante, jede mit
 *                      eigener Phase, Höhe und Breite (organisches Züngeln)
 *   3. Funkenflug    – sechzehn glühende Partikel, die aus dem Element
 *                      aufsteigen, seitlich tänzeln und verglühen
 *   4. Glutbett      – ein tiefes Glimmen unter dem Element, als läge es
 *                      auf heißen Kohlen
 *
 * Alle Farben werden mit dem aktuellen Theme-Akzent verschmolzen, sodass die
 * Aura in jedem der fünf Themes „in den entsprechenden Farben" brennt.
 */
fun Modifier.epicFireAura(
    accent: Color,
    enabled: Boolean = true,
    intensity: Float = 1f
): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "epicFire")

    // Aufstiegs-Phase für Funken (langsame Endlosschleife)
    val rise by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "fireRise"
    )
    // Zwei überlagerte Flacker-Phasen (unregelmäßiges Züngeln)
    val flickA by t.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(470, easing = androidx.compose.animation.core.LinearEasing)),
        label = "fireFlickA"
    )
    val flickB by t.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(760, easing = androidx.compose.animation.core.LinearEasing)),
        label = "fireFlickB"
    )
    // Tiefes Atmen der gesamten Aura
    val breath by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fireBreath"
    )

    // Glut-Palette, mit Theme-Akzent verschmolzen → passt sich den Einstellungen an
    val hot = lerp(Fire_Hot, accent, 0.22f)
    val core = lerp(Fire_Core, accent, 0.34f)
    val ember = lerp(Fire_Ember, accent, 0.34f)
    val deep = lerp(Fire_Deep, accent, 0.26f)

    // Deterministische Funken-Bahnen (Goldener Schnitt für gleichmäßige Streuung)
    val sparks = remember {
        List(16) { i ->
            val x = ((i * 0.618034f) + 0.13f) % 1f          // Start-X 0..1
            val speed = 0.55f + ((i * 37) % 13) / 13f * 0.95f // Aufstiegs-Tempo
            val scale = 0.55f + ((i * 53) % 7) / 7f * 0.9f    // Größe
            Triple(x, speed, scale)
        }
    }

    drawBehind {
        val flicker = sin(flickA) * 0.5f + sin(flickB * 1.37f) * 0.5f  // -1..1
        val f = 0.5f + 0.5f * flicker                                   // 0..1
        val amp = intensity * (0.6f + 0.4f * breath)

        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // ---------- 1) WÄRME-KORONA (drei Schichten, weit über das Feld hinaus) ----------
        val coronaCenter = Offset(cx + w * 0.015f * flicker, cy - h * 0.30f)
        val rFar = size.maxDimension * (1.05f + 0.35f * breath)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    deep.copy(alpha = 0.16f * amp),
                    deep.copy(alpha = 0.06f * amp),
                    Color.Transparent
                ),
                center = coronaCenter, radius = rFar
            ),
            radius = rFar, center = coronaCenter
        )
        val rMid = size.maxDimension * (0.78f + 0.20f * f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    core.copy(alpha = 0.30f * amp),
                    ember.copy(alpha = 0.13f * amp),
                    Color.Transparent
                ),
                center = coronaCenter, radius = rMid
            ),
            radius = rMid, center = coronaCenter
        )
        val rNear = size.maxDimension * (0.52f + 0.12f * f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    hot.copy(alpha = 0.34f * amp),
                    core.copy(alpha = 0.16f * amp),
                    Color.Transparent
                ),
                center = coronaCenter, radius = rNear
            ),
            radius = rNear, center = coronaCenter
        )

        // ---------- 2) FLAMMENZUNGEN über der Oberkante ----------
        // Neun Zungen; jede tanzt mit eigener Phase. Aufgebaut aus einer
        // Kette schrumpfender Glut-Kreise (breite Basis → heiße Spitze).
        val tongueCount = 9
        for (i in 0 until tongueCount) {
            val tx = w * (0.08f + 0.84f * i / (tongueCount - 1f))
            val wob = sin(flickA * 1.15f + i * 1.9f) * 0.5f +
                      sin(flickB * 0.9f + i * 0.7f) * 0.5f      // -1..1
            val tW = 0.5f + 0.5f * wob                            // 0..1
            val tongueH = h * (0.45f + 0.95f * tW) * amp          // Zungenhöhe
            val baseR = w * 0.052f * (0.85f + 0.45f * tW)

            val steps = 4
            for (s in 0..steps) {
                val prog = s / steps.toFloat()                    // 0 Basis .. 1 Spitze
                val segY = -tongueH * prog * 0.92f
                val segX = tx + sin(flickA * 1.6f + i * 2.3f + prog * 3.4f) * w * 0.018f * prog
                val segR = baseR * (1f - 0.72f * prog)
                val segColor = when {
                    prog < 0.35f -> hot
                    prog < 0.7f -> core
                    else -> ember
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            segColor.copy(alpha = (0.55f - 0.38f * prog) * amp),
                            Color.Transparent
                        ),
                        center = Offset(segX, segY), radius = segR * 2.1f
                    ),
                    radius = segR * 2.1f, center = Offset(segX, segY)
                )
            }
        }

        // ---------- 3) FUNKENFLUG (aufsteigende, verglühende Partikel) ----------
        sparks.forEach { (sx, speed, scale) ->
            val p = ((rise * speed + sx) % 1f)                    // 0 unten .. 1 oben
            val ey = h * 0.85f - p * h * 2.4f                     // steigt weit über das Feld
            val ex = w * sx + sin(p * 11f + sx * 25f) * w * 0.06f // seitliches Tänzeln
            val fade = (1f - p) * (1f - p)                        // quadratisches Verglühen
            val a = fade * 0.9f * amp
            if (a > 0.02f) {
                val rSpark = (1.6f + 2.6f * scale) * (1f - 0.45f * p) * density
                // Glow um den Funken
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ember.copy(alpha = a * 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(ex, ey), radius = rSpark * 3.4f
                    ),
                    radius = rSpark * 3.4f, center = Offset(ex, ey)
                )
                // Heißer Funkenkern
                drawCircle(
                    color = lerp(hot, Color.White, 0.25f * fade).copy(alpha = a),
                    radius = rSpark, center = Offset(ex, ey)
                )
            }
        }

        // ---------- 4) GLUTBETT unter dem Element ----------
        val bedCenter = Offset(cx, h * (1.05f + 0.03f * f))
        val bedR = w * (0.62f + 0.10f * breath)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    deep.copy(alpha = 0.42f * amp),
                    core.copy(alpha = 0.16f * amp),
                    Color.Transparent
                ),
                center = bedCenter, radius = bedR
            ),
            radius = bedR, center = bedCenter
        )
    }
}

// ---------- Karte mit Metall-Layer ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cfg = LocalThemeConfig.current
    val shape = RoundedCornerShape(Dim.cardRadius)
    val base = Modifier
        .shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.55f),
            spotColor = (accent ?: cfg.primaryColor).copy(alpha = 0.35f)
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to GraphiteLight.copy(alpha = 0.95f),
                0.12f to cfg.cardBackground,
                0.85f to cfg.cardBackground,
                1f to Color.Black.copy(alpha = 0.25f).compositeOver(cfg.cardBackground)
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                0f to (accent ?: cfg.primaryColor).copy(alpha = if (accent != null) 0.55f else 0.20f),
                0.5f to cfg.cardBorder,
                1f to (accent ?: Color.White).copy(alpha = if (accent != null) 0.18f else 0.04f)
            ),
            shape
        )
    val clickMod = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick
        )
    } else Modifier
    Box(modifier = modifier.then(clickMod).then(base)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(EdgeHighlight))
        Box(Modifier.padding(top = 1.dp)) { content() }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val cfgHeader = LocalThemeConfig.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(12.dp).background(
                Brush.verticalGradient(
                    listOf(cfgHeader.primaryColor, cfgHeader.gradDeep)
                ),
                RoundedCornerShape(2.dp)
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = TextMuted, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge,
                color = LocalThemeConfig.current.secondaryColor,
                modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

enum class BadgeTone { Success, Warn, Critical, Info, Ai, Neutral }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val cfg = LocalThemeConfig.current
    val (bg, fg) = when (tone) {
        BadgeTone.Success -> SuccessDim to SuccessGreen
        BadgeTone.Warn -> WarnAmberDim to WarnAmber
        BadgeTone.Critical -> CriticalRedDim to CriticalRed
        BadgeTone.Info -> cfg.primaryColor.copy(alpha = 0.16f) to cfg.primaryColor
        BadgeTone.Ai -> AiVioletDim to AiViolet
        BadgeTone.Neutral -> SlateHigh to TextSecondary
    }
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun KpiTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral
) {
    val cfg = LocalThemeConfig.current
    val accent = when (tone) {
        BadgeTone.Success -> SuccessGreen
        BadgeTone.Warn -> WarnAmber
        BadgeTone.Critical -> CriticalRed
        BadgeTone.Info -> cfg.primaryColor
        BadgeTone.Ai -> AiViolet
        BadgeTone.Neutral -> TextPrimary
    }
    AppCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.width(22.dp).height(3.dp).background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.15f))
                    ),
                    RoundedCornerShape(2.dp)
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
fun IconDisc(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 38) {
    Box(
        modifier.size(size.dp).clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.22f),
                    1f to tint.copy(alpha = 0.10f)
                )
            )
            .border(1.dp, tint.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun PersonRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = LocalThemeConfig.current.secondaryColor,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                IconDisc(leadingIcon, leadingTint)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (badge != null) {
                    Spacer(Modifier.height(6.dp))
                    badge()
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label="aura")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    return this.graphicsLayer(
        scaleX = scale,
        scaleY = scale
    ).drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            size = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
    }
}

// ---------- Chips & Segmente mit Puls auf Auswahl ----------
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalThemeConfig.current.primaryColor
) {
    val cfg = LocalThemeConfig.current
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.20f) else SlateHigh.copy(alpha = 0.55f),
        tween(200), label = "chipBg"
    )
    val border by animateColorAsState(
        if (selected) accent.copy(alpha = 0.7f) else BorderSubtle,
        tween(200), label = "chipBorder"
    )
    Box(
        modifier
            .pulsingAura(cfg.auraColor, enabled = selected, maxRadiusFactor = 0.55f)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary, maxLines = 1)
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalThemeConfig.current.primaryColor
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { opt ->
            SelectChip(opt, opt == selected, { onSelect(opt) }, accent = accent)
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cfg = LocalThemeConfig.current
    Row(
        modifier
            .clip(RoundedCornerShape(Dim.controlRadius))
            .background(SlateHigh.copy(alpha = 0.45f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(Dim.controlRadius))
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val sel = i == selectedIndex
            val bg by animateColorAsState(
                if (sel) cfg.primaryColor.copy(alpha = 0.18f) else Color.Transparent,
                tween(220), label = "seg"
            )
            Box(
                Modifier.weight(1f)
                    .pulsingAura(cfg.auraColor, enabled = sel, maxRadiusFactor = 0.45f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .then(
                        if (sel) Modifier.border(
                            1.dp, cfg.primaryColor.copy(alpha = 0.55f), RoundedCornerShape(9.dp)
                        ) else Modifier
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(opt, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) cfg.primaryColor else TextSecondary, maxLines = 1)
            }
        }
    }
}

// ---------- Buttons ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tone: Color = LocalThemeConfig.current.primaryColor,
    pulsing: Boolean = false,
    fire: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val cfg = LocalThemeConfig.current
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(150), label = "btn")
    val isThemeTone = tone == cfg.primaryColor
    val brush = when {
        fire -> fireBrush(cfg.primaryColor)
        isThemeTone -> Brush.verticalGradient(
            0f to cfg.gradLight.copy(alpha = 0.95f * alpha),
            0.35f to cfg.primaryColor.copy(alpha = alpha),
            1f to cfg.gradDeep.copy(alpha = alpha)
        )
        else -> Brush.verticalGradient(
            0f to tone.copy(alpha = 0.95f * alpha),
            1f to tone.copy(alpha = 0.75f * alpha)
        )
    }

    val isIndustrialSteel = cfg.name == "Industrial Steel" && text.contains("Hotbox", ignoreCase = true)
    var showLightning by remember { mutableStateOf(false) }
    if (isIndustrialSteel) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(2200)
                showLightning = true
                delay(120)
                showLightning = false
                delay(120)
                showLightning = true
                delay(120)
                showLightning = false
                delay(120)
                showLightning = true
                delay(500)
                showLightning = false
            }
        }
    }

    Row(
        modifier
            .then(
                if (fire) Modifier.epicFireAura(cfg.primaryColor, enabled = enabled)
                else Modifier.pulsingAura(cfg.auraColor, enabled = pulsing && enabled)
            )
            .breathing(enabled = (pulsing || fire) && enabled)
            .clip(RoundedCornerShape(Dim.controlRadius))
            .background(brush)
            .border(
                1.dp,
                if (fire) lerp(Fire_Hot, Color.White, 0.35f).copy(alpha = 0.55f * alpha)
                else Color.White.copy(alpha = 0.22f * alpha),
                RoundedCornerShape(Dim.controlRadius)
            )
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val fg = when {
            fire -> Color(0xFF2A0A00)
            isThemeTone -> cfg.onAccent
            else -> Color(0xFF10151B)
        }
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        if (isIndustrialSteel) {
            AnimatedVisibility(
                visible = showLightning,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text("⚡ ", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFD700))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (isIndustrialSteel) {
            AnimatedVisibility(
                visible = showLightning,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text(" ⚡", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFD700))
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = TextPrimary
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Dim.controlRadius))
            .background(
                Brush.verticalGradient(
                    0f to SlateHigh.copy(alpha = 0.75f),
                    1f to SlateElevated.copy(alpha = 0.75f)
                )
            )
            .border(1.dp, BorderSubtle, RoundedCornerShape(Dim.controlRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

@Composable
fun AiHintCard(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    AppCard(modifier = modifier.fillMaxWidth(), accent = AiViolet) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconDisc(icon, AiViolet, size = 34)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("KI-Empfehlung", style = MaterialTheme.typography.labelMedium, color = AiViolet)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(10.dp))
                SecondaryButton(actionLabel, onAction, tint = AiViolet)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconDisc(icon, TextMuted, size = 56)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextSecondary, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun TimelineEntry(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    meta: String,
    isLast: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconDisc(icon, tint, size = 32)
            if (!isLast) {
                Box(
                    Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp)
                        .background(BorderSubtle, RoundedCornerShape(1.dp))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

// ---------- Navigation mit pulsierender Aura ----------
data class NavArea(val key: String, val label: String, val icon: ImageVector)

@Composable
fun StromrufNavBar(
    areas: List<NavArea>,
    activeKey: String,
    onSelect: (String) -> Unit,
    badgeCounts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val cfg = LocalThemeConfig.current
    Surface(modifier = modifier.fillMaxWidth(), color = cfg.cardBackground, tonalElevation = 0.dp) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, cfg.primaryColor.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
            )
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                areas.forEach { area ->
                    val selected = area.key == activeKey
                    val tint by animateColorAsState(
                        if (selected) cfg.primaryColor else TextMuted, tween(200), label = "nav"
                    )
                    val iconScale by animateFloatAsState(
                        if (selected) 1.12f else 1f,
                        spring(dampingRatio = 0.55f, stiffness = 380f), label = "navScale"
                    )
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(area.key) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val count = badgeCounts[area.key] ?: 0
                        Box(
                            Modifier.pulsingAura(cfg.auraColor, enabled = selected, maxRadiusFactor = 0.8f),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(badge = {
                                if (count > 0) {
                                    Badge(containerColor = CriticalRed) {
                                        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }) {
                                Icon(area.icon, area.label, tint = tint,
                                    modifier = Modifier.size(23.dp).scale(iconScale))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(area.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier.width(16.dp).height(2.dp).background(
                                if (selected) Brush.horizontalGradient(
                                    listOf(cfg.gradLight, cfg.primaryColor, cfg.gradDeep)
                                ) else Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                ),
                                RoundedCornerShape(1.dp)
                            )
                        )
                    }
                }
            }
        }
    }
}
