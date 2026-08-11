package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.database.CallLogEntity
import com.example.ui.design.AppCard
import com.example.ui.design.BadgeTone
import com.example.ui.design.ChipRow
import com.example.ui.design.Dim
import com.example.ui.design.EmptyState
import com.example.ui.design.IconDisc
import com.example.ui.design.SectionHeader
import com.example.ui.design.StatusBadge
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.SlateHigh
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarnAmber
import com.example.viewmodel.StromrufViewModel
import java.util.Calendar
import java.util.Locale

// ============================================================
// STATISTIK 3.0 – Sales-Intelligence-Cockpit
//  · KPIs mit Trend-Vergleich zur Vorperiode
//  · Anrufvolumen (gestapelte Balken, animiert)
//  · Erreichbarkeits-Trendlinie
//  · Beste-Anrufzeiten-Heatmap (Wochentag × Stunde)
//  · Conversion-Funnel bis zum Abschluss
//  · Automatische Insights aus den eigenen Daten
// ============================================================

private enum class StatRange(val label: String) {
    TAG("Tag"), WOCHE("Woche"), MONAT("Monat"), QUARTAL("Quartal"), JAHR("Jahr")
}

private data class Bucket(val label: String, val start: Long, val end: Long)

private fun startOfDay(cal: Calendar = Calendar.getInstance()): Calendar =
    (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

private fun buildBuckets(range: StatRange): List<Bucket> {
    val now = Calendar.getInstance()
    val day0 = startOfDay(now)
    return when (range) {
        StatRange.TAG -> (7..20).map { h ->
            val s = (day0.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h) }
            Bucket("$h", s.timeInMillis, s.timeInMillis + 3600_000L)
        }
        StatRange.WOCHE -> {
            val monday = (day0.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                if (timeInMillis > now.timeInMillis) add(Calendar.DAY_OF_YEAR, -7)
            }
            val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            (0..6).map { d ->
                val s = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, d) }
                Bucket(names[d], s.timeInMillis, s.timeInMillis + 86_400_000L)
            }
        }
        StatRange.MONAT -> {
            val first = (day0.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val weeks = (daysInMonth + 6) / 7
            (0 until weeks).map { w ->
                val s = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, w * 7) }
                val e = (first.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, minOf((w + 1) * 7, daysInMonth))
                }
                Bucket("W${w + 1}", s.timeInMillis, e.timeInMillis)
            }
        }
        StatRange.QUARTAL -> {
            val qStartMonth = (now.get(Calendar.MONTH) / 3) * 3
            (0..2).map { m ->
                val s = (day0.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, qStartMonth + m)
                }
                val e = (s.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                val label = s.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.GERMAN) ?: "${m + 1}"
                Bucket(label, s.timeInMillis, e.timeInMillis)
            }
        }
        StatRange.JAHR -> {
            val jan = (day0.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, Calendar.JANUARY)
            }
            listOf("J","F","M","A","M","J","J","A","S","O","N","D").mapIndexed { m, lbl ->
                val s = (jan.clone() as Calendar).apply { add(Calendar.MONTH, m) }
                val e = (s.clone() as Calendar).apply { add(Calendar.MONTH, m + 1) }
                Bucket(lbl, s.timeInMillis, e.timeInMillis)
            }
        }
    }
}

private fun isReached(outcome: String): Boolean =
    (outcome.contains("Erreicht", true) && !outcome.contains("Nicht", true)) ||
        outcome.contains("erreicht_", true) ||
        outcome.contains("Angebot", true) || outcome.contains("Abschluss", true)

/** Prozentuale Veränderung gegenüber der Vorperiode; null wenn nicht berechenbar. */
private fun deltaPercent(current: Double, previous: Double): Int? {
    if (previous <= 0.0) return null
    return (((current - previous) / previous) * 100).toInt()
}

@Composable
fun StatistikenScreen(viewModel: StromrufViewModel) {
    val callLogs by viewModel.callLogs.collectAsState()
    val annahmen by viewModel.annahmen.collectAsState()
    val cfg = LocalThemeConfig.current

    var range by remember { mutableStateOf(StatRange.WOCHE) }
    val buckets = remember(range) { buildBuckets(range) }
    val rangeStart = buckets.first().start
    val rangeEnd = buckets.last().end
    val rangeLen = rangeEnd - rangeStart

    val inRange = remember(callLogs, range) {
        callLogs.filter { it.timestamp in rangeStart until rangeEnd }
    }
    val inPrevRange = remember(callLogs, range) {
        callLogs.filter { it.timestamp in (rangeStart - rangeLen) until rangeStart }
    }

    val reachedPerBucket = remember(inRange, buckets) {
        buckets.map { b -> inRange.count { it.timestamp in b.start until b.end && isReached(it.outcome) } }
    }
    val missedPerBucket = remember(inRange, buckets) {
        buckets.map { b -> inRange.count { it.timestamp in b.start until b.end && !isReached(it.outcome) } }
    }

    // --- KPIs aktuelle Periode ---
    val totalCalls = inRange.size
    val totalReached = reachedPerBucket.sum()
    val quote = if (totalCalls > 0) totalReached * 100 / totalCalls else 0
    val avgDurMin = if (totalReached > 0)
        inRange.filter { isReached(it.outcome) }.sumOf { it.durationSeconds } / totalReached / 60.0 else 0.0
    val abschluesse = annahmen.count { it.timestamp in rangeStart until rangeEnd }
    val volumen = annahmen.filter { it.timestamp in rangeStart until rangeEnd }
        .sumOf { it.weightedVolume }

    // --- KPIs Vorperiode (für Deltas) ---
    val prevCalls = inPrevRange.size
    val prevReached = inPrevRange.count { isReached(it.outcome) }
    val prevQuote = if (prevCalls > 0) prevReached * 100.0 / prevCalls else 0.0
    val prevAbschluesse = annahmen.count { it.timestamp in (rangeStart - rangeLen) until rangeStart }

    val callsDelta = deltaPercent(totalCalls.toDouble(), prevCalls.toDouble())
    val quoteDelta = deltaPercent(quote.toDouble(), prevQuote)
    val abschlussDelta = deltaPercent(abschluesse.toDouble(), prevAbschluesse.toDouble())

    // --- Insights über ALLE Daten ---
    val heat = remember(callLogs) { buildHeatmap(callLogs) }
    val insights = remember(callLogs, inRange, inPrevRange) {
        buildInsights(callLogs, quote, prevQuote.toInt(), prevCalls > 0)
    }

    // Einblendanimation bei Zeitraumwechsel
    val progress = remember(range) { Animatable(0f) }
    LaunchedEffect(range) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dim.screenPad, end = Dim.screenPad, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Dim.gap)
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
                Text("Statistik", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                Text("Dein Sales-Cockpit – live aus deinen Anrufen",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        item {
            ChipRow(
                options = StatRange.entries.map { it.label },
                selected = range.label,
                onSelect = { sel -> range = StatRange.entries.first { it.label == sel } }
            )
        }

        // ---- KPI-Kacheln mit Trend zur Vorperiode ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                TrendKpi("$totalCalls", "Anrufe", callsDelta, cfg.primaryColor, Modifier.weight(1f))
                TrendKpi("$quote %", "Erreicht", quoteDelta, SuccessGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                TrendKpi(
                    String.format(Locale.GERMAN, "%.1f", avgDurMin), "Ø Min. (erreicht)",
                    null, WarnAmber, Modifier.weight(1f)
                )
                TrendKpi("$abschluesse", "Abschlüsse", abschlussDelta, cfg.tertiaryColor, Modifier.weight(1f))
            }
        }

        if (volumen > 0) {
            item {
                AppCard(accent = cfg.primaryColor) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconDisc(Icons.Default.Bolt, cfg.primaryColor, size = 38)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                String.format(Locale.GERMAN, "%,d kWh", volumen),
                                style = MaterialTheme.typography.headlineMedium, color = cfg.primaryColor
                            )
                            Text("Gewichtetes Abschlussvolumen (Verbrauch × Laufzeit)",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // ---- Anrufvolumen ----
        item { SectionHeader("ANRUFVOLUMEN · ${range.label.uppercase()}") }
        item {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    if (totalCalls == 0) {
                        EmptyState(
                            Icons.Default.Insights, "Keine Anrufe im Zeitraum",
                            "Sobald du telefonierst, entsteht hier dein Verlauf."
                        )
                    } else {
                        VolumeChart(
                            buckets = buckets,
                            reached = reachedPerBucket,
                            missed = missedPerBucket,
                            progress = progress.value,
                            gradTop = cfg.gradLight,
                            gradBottom = cfg.gradDeep
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(cfg.gradLight); Spacer(Modifier.width(6.dp))
                            Text("Erreicht", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(Modifier.width(16.dp))
                            LegendDot(SlateHigh); Spacer(Modifier.width(6.dp))
                            Text("Nicht erreicht", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // ---- Erreichbarkeits-Trend ----
        if (totalCalls > 0) {
            item { SectionHeader("ERREICHBARKEITS-TREND") }
            item {
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        QuoteTrendChart(
                            buckets = buckets,
                            reached = reachedPerBucket,
                            missed = missedPerBucket,
                            progress = progress.value,
                            accent = SuccessGreen
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Anteil erreichter Anrufe je Abschnitt – Ziel-Linie bei 50 %",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted
                        )
                    }
                }
            }
        }

        // ---- Beste Anrufzeiten (Heatmap über alle Daten) ----
        if (heat.totalCalls >= 10) {
            item { SectionHeader("BESTE ANRUFZEITEN · ALLE DATEN") }
            item {
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        HeatmapChart(heat = heat, accent = cfg.primaryColor)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Je intensiver die Zelle, desto höher deine Erreichbarkeitsquote " +
                                "zu dieser Zeit (Wochentag × Stunde).",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted
                        )
                    }
                }
            }
        }

        // ---- Conversion-Funnel ----
        if (totalCalls > 0) {
            item { SectionHeader("CONVERSION-FUNNEL") }
            item {
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FunnelBar("Anrufe", totalCalls, totalCalls, cfg.primaryColor, progress.value)
                        FunnelBar("Erreicht", totalReached, totalCalls, SuccessGreen, progress.value)
                        FunnelBar("Abschlüsse", abschluesse, totalCalls, cfg.tertiaryColor, progress.value)
                        if (totalReached > 0) {
                            Text(
                                "Abschlussquote aus erreichten Gesprächen: " +
                                    "${abschluesse * 100 / totalReached} %",
                                style = MaterialTheme.typography.labelMedium, color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // ---- Insights ----
        if (insights.isNotEmpty()) {
            item { SectionHeader("INSIGHTS") }
            items(insights.size) { i ->
                val insight = insights[i]
                AppCard(accent = insight.color) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconDisc(insight.icon, insight.color, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(insight.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(insight.text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// KPI-Kachel mit Delta-Badge
// ============================================================
@Composable
private fun TrendKpi(
    value: String,
    label: String,
    delta: Int?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, accent = accent) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (delta != null) {
                    val up = delta > 0
                    val flat = delta == 0
                    val tone = when {
                        flat -> BadgeTone.Neutral
                        up -> BadgeTone.Success
                        else -> BadgeTone.Critical
                    }
                    StatusBadge(
                        text = (if (up) "+" else "") + "$delta %",
                        tone = tone
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
            if (delta != null) {
                Text("vs. Vorperiode", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

// ============================================================
// Gestapelte Balken (Anrufvolumen)
// ============================================================
@Composable
private fun VolumeChart(
    buckets: List<Bucket>,
    reached: List<Int>,
    missed: List<Int>,
    progress: Float,
    gradTop: Color,
    gradBottom: Color
) {
    val labelColor = TextMuted.toArgb()
    val missColor = SlateHigh
    val maxVal = buckets.indices.maxOf { reached[it] + missed[it] }.coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val labelH = 34f
        val chartH = size.height - labelH
        val n = buckets.size
        val slot = size.width / n
        val barW = (slot * 0.52f).coerceAtMost(46f)
        val corner = CornerRadius(barW / 3f, barW / 3f)

        buckets.forEachIndexed { i, b ->
            val r = reached[i]
            val m = missed[i]
            val totalH = chartH * (r + m).toFloat() / maxVal * progress
            val reachedH = chartH * r.toFloat() / maxVal * progress
            val x = slot * i + (slot - barW) / 2f

            if (m > 0) {
                drawRoundRect(
                    color = missColor,
                    topLeft = Offset(x, chartH - totalH),
                    size = Size(barW, totalH - reachedH + if (r > 0) corner.y else 0f),
                    cornerRadius = corner
                )
            }
            if (r > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to gradTop, 1f to gradBottom,
                        startY = chartH - reachedH, endY = chartH
                    ),
                    topLeft = Offset(x, chartH - reachedH),
                    size = Size(barW, reachedH),
                    cornerRadius = corner
                )
            }
            if (r + m > 0 && progress > 0.9f) {
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(
                        "${r + m}", x + barW / 2f,
                        (chartH - totalH - 8f).coerceAtLeast(24f),
                        android.graphics.Paint().apply {
                            color = labelColor; textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    b.label, x + barW / 2f, size.height - 6f,
                    android.graphics.Paint().apply {
                        color = labelColor; textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}

// ============================================================
// Erreichbarkeits-Trend (Fläche + Linie + 50%-Ziellinie)
// ============================================================
@Composable
private fun QuoteTrendChart(
    buckets: List<Bucket>,
    reached: List<Int>,
    missed: List<Int>,
    progress: Float,
    accent: Color
) {
    val labelColor = TextMuted.toArgb()

    // Quote je Bucket; Buckets ohne Anrufe werden übersprungen (-1)
    val quotes = buckets.indices.map { i ->
        val total = reached[i] + missed[i]
        if (total == 0) -1f else reached[i].toFloat() / total
    }

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelH = 30f
        val chartH = size.height - labelH
        val n = buckets.size
        val slot = size.width / n

        // Ziel-Linie bei 50 %
        val targetY = chartH * 0.5f
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 2f
        )

        // Punkte sammeln
        val points = mutableListOf<Offset>()
        quotes.forEachIndexed { i, q ->
            if (q >= 0f) {
                val x = slot * i + slot / 2f
                val y = chartH - (chartH * q * progress)
                points.add(Offset(x, y))
            }
        }

        if (points.size >= 2) {
            // Fläche unter der Linie
            val area = Path().apply {
                moveTo(points.first().x, chartH)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, chartH)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.30f),
                    1f to accent.copy(alpha = 0.02f)
                )
            )
            // Linie
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = accent, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        // Punkte
        points.forEach { p ->
            drawCircle(color = accent, radius = 6f, center = p)
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.5f, center = p)
        }

        // Bucket-Labels
        buckets.forEachIndexed { i, b ->
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    b.label, slot * i + slot / 2f, size.height - 4f,
                    android.graphics.Paint().apply {
                        color = labelColor; textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}

// ============================================================
// Heatmap: Wochentag × Stunde → Erreichbarkeitsquote
// ============================================================
private data class HeatData(
    val calls: Array<IntArray>,     // [7 Tage][Stunden 7..20]
    val reached: Array<IntArray>,
    val totalCalls: Int,
    val hours: IntRange = 7..20
)

private fun buildHeatmap(logs: List<CallLogEntity>): HeatData {
    val hours = 7..20
    val nH = hours.last - hours.first + 1
    val calls = Array(7) { IntArray(nH) }
    val reached = Array(7) { IntArray(nH) }
    val cal = Calendar.getInstance()
    logs.forEach { log ->
        cal.timeInMillis = log.timestamp
        val h = cal.get(Calendar.HOUR_OF_DAY)
        if (h in hours) {
            // Calendar: So=1 … Sa=7 → Mo=0 … So=6
            val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            calls[dow][h - hours.first]++
            if (isReached(log.outcome)) reached[dow][h - hours.first]++
        }
    }
    return HeatData(calls, reached, logs.size, hours)
}

@Composable
private fun HeatmapChart(heat: HeatData, accent: Color) {
    val labelColor = TextMuted.toArgb()
    val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val base = SlateHigh.copy(alpha = 0.35f)

    Canvas(Modifier.fillMaxWidth().height(210.dp)) {
        val leftPad = 52f
        val topPad = 26f
        val nH = heat.hours.last - heat.hours.first + 1
        val cellW = (size.width - leftPad) / nH
        val cellH = (size.height - topPad) / 7f
        val gap = 3f

        // Stunden-Labels (jede zweite)
        for (hIdx in 0 until nH step 2) {
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    "${heat.hours.first + hIdx}",
                    leftPad + cellW * hIdx + cellW / 2f, 20f,
                    android.graphics.Paint().apply {
                        color = labelColor; textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }

        for (d in 0 until 7) {
            // Tages-Label
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    dayNames[d], 8f, topPad + cellH * d + cellH / 2f + 8f,
                    android.graphics.Paint().apply {
                        color = labelColor; textSize = 24f
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                    }
                )
            }
            for (hIdx in 0 until nH) {
                val total = heat.calls[d][hIdx]
                val rate = if (total > 0) heat.reached[d][hIdx].toFloat() / total else 0f
                // Intensität: Mischung aus Aktivität und Quote
                val activity = (total.toFloat() / 6f).coerceAtMost(1f)
                val cellColor = if (total == 0) base
                else lerp(accent.copy(alpha = 0.18f), accent, rate)
                    .copy(alpha = 0.25f + 0.75f * maxOf(rate * 0.7f, activity * 0.5f))
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(leftPad + cellW * hIdx + gap / 2f, topPad + cellH * d + gap / 2f),
                    size = Size(cellW - gap, cellH - gap),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
    }
}

// ============================================================
// Funnel-Balken
// ============================================================
@Composable
private fun FunnelBar(label: String, value: Int, maxValue: Int, color: Color, progress: Float) {
    val frac = if (maxValue > 0) value.toFloat() / maxValue else 0f
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = TextSecondary, modifier = Modifier.weight(1f))
            Text(
                "$value · ${(frac * 100).toInt()} %",
                style = MaterialTheme.typography.labelMedium, color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .background(SlateHigh.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((frac * progress).coerceIn(0.02f, 1f))
                    .height(12.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to color.copy(alpha = 0.65f),
                            1f to color
                        ),
                        RoundedCornerShape(6.dp)
                    )
            )
        }
    }
}

// ============================================================
// Automatische Insights
// ============================================================
private data class Insight(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val title: String,
    val text: String
)

private fun buildInsights(
    logs: List<CallLogEntity>,
    quote: Int,
    prevQuote: Int,
    hasPrev: Boolean
): List<Insight> {
    val out = mutableListOf<Insight>()
    if (logs.size < 10) return out

    val cal = Calendar.getInstance()
    val dayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    // Bester Wochentag nach Quote (min. 5 Anrufe)
    val byDay = logs.groupBy {
        cal.timeInMillis = it.timestamp
        (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    val bestDay = byDay
        .filter { it.value.size >= 5 }
        .maxByOrNull { (_, l) -> l.count { isReached(it.outcome) }.toFloat() / l.size }
    if (bestDay != null) {
        val rate = bestDay.value.count { isReached(it.outcome) } * 100 / bestDay.value.size
        out.add(
            Insight(
                Icons.Default.EmojiEvents, SuccessGreen,
                "Stärkster Tag: ${dayNames[bestDay.key]}",
                "$rate % Erreichbarkeit bei ${bestDay.value.size} Anrufen – plane wichtige " +
                    "Gespräche bevorzugt auf diesen Tag."
            )
        )
    }

    // Beste Stunde nach Quote (min. 5 Anrufe)
    val byHour = logs.groupBy {
        cal.timeInMillis = it.timestamp
        cal.get(Calendar.HOUR_OF_DAY)
    }
    val bestHour = byHour
        .filter { it.value.size >= 5 }
        .maxByOrNull { (_, l) -> l.count { isReached(it.outcome) }.toFloat() / l.size }
    if (bestHour != null) {
        val rate = bestHour.value.count { isReached(it.outcome) } * 100 / bestHour.value.size
        out.add(
            Insight(
                Icons.Default.Schedule, WarnAmber,
                "Goldene Stunde: ${bestHour.key}:00 – ${bestHour.key + 1}:00 Uhr",
                "$rate % Erreichbarkeit in diesem Zeitfenster – hier lohnt sich die Hotbox."
            )
        )
    }

    // Trend zur Vorperiode
    if (hasPrev) {
        val diff = quote - prevQuote
        when {
            diff >= 5 -> out.add(
                Insight(
                    Icons.AutoMirrored.Filled.TrendingUp, SuccessGreen,
                    "Aufwärtstrend: +$diff Prozentpunkte",
                    "Deine Erreichbarkeitsquote liegt deutlich über der Vorperiode. Weiter so!"
                )
            )
            diff <= -5 -> out.add(
                Insight(
                    Icons.AutoMirrored.Filled.TrendingDown, CriticalRed,
                    "Abwärtstrend: $diff Prozentpunkte",
                    "Die Quote ist gegenüber der Vorperiode gefallen – prüfe Anrufzeiten und Listen-Qualität."
                )
            )
            else -> out.add(
                Insight(
                    Icons.AutoMirrored.Filled.TrendingFlat, TextSecondary,
                    "Stabile Quote",
                    "Deine Erreichbarkeit bewegt sich auf dem Niveau der Vorperiode ($quote %)."
                )
            )
        }
    }

    // Aktivitäts-Streak: aufeinanderfolgende Tage mit Anrufen
    val daysWithCalls = logs.map {
        cal.timeInMillis = it.timestamp
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }.toSortedSet()
    if (daysWithCalls.isNotEmpty()) {
        val today = Calendar.getInstance()
        var streak = 0
        val probe = today.clone() as Calendar
        while (true) {
            val key = probe.get(Calendar.YEAR) * 1000 + probe.get(Calendar.DAY_OF_YEAR)
            if (key in daysWithCalls) {
                streak++
                probe.add(Calendar.DAY_OF_YEAR, -1)
            } else if (streak == 0 && probe == today) {
                break
            } else {
                break
            }
        }
        if (streak >= 2) {
            out.add(
                Insight(
                    Icons.Default.LocalFireDepartment, CriticalRed,
                    "$streak Tage in Serie aktiv",
                    "Du telefonierst seit $streak Tagen ohne Unterbrechung – Konstanz gewinnt."
                )
            )
        }
    }

    return out
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}
