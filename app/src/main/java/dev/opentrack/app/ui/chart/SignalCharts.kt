package dev.opentrack.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.opentrack.app.ui.model.CalendarDayUi
import dev.opentrack.app.ui.model.ChartBarUi
import dev.opentrack.app.ui.model.ChartPointUi
import dev.opentrack.app.ui.model.DistributionPartUi
import kotlin.math.abs
import kotlin.math.max

@Composable
fun SignalSparkline(
    points: List<ChartPointUi>,
    summary: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(points) { points.sortedBy { it.x } }
    Canvas(
        modifier = modifier
            .height(48.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        if (sorted.isEmpty()) {
            drawLine(
                color = color.copy(alpha = 0.22f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val coordinates = sorted.toOffsets(size.width, size.height, 3.dp.toPx())
        if (coordinates.size == 1) {
            drawCircle(color = color, radius = 4.dp.toPx(), center = coordinates.first())
            return@Canvas
        }
        val line = pathThrough(coordinates)
        val fill = Path().apply {
            addPath(line)
            lineTo(coordinates.last().x, size.height)
            lineTo(coordinates.first().x, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.24f), Color.Transparent),
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = color, radius = 3.dp.toPx(), center = coordinates.last())
    }
}

@Composable
fun SignalLineChart(
    points: List<ChartPointUi>,
    summary: String,
    color: Color,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(points) { points.sortedBy { it.x } }
    var selectedIndex by remember(points) { mutableIntStateOf(if (points.isEmpty()) -1 else points.lastIndex) }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        if (selectedIndex in sorted.indices) {
            val selected = sorted[selectedIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(sorted) {
                    detectTapGestures { tap ->
                        if (sorted.isNotEmpty() && size.width > 0) {
                            val ratio = (tap.x / size.width).coerceIn(0f, 1f)
                            val minX = sorted.first().x
                            val maxX = sorted.last().x
                            val tappedX = minX + ((maxX - minX) * ratio)
                            selectedIndex = sorted.indices.minByOrNull { abs(sorted[it].x - tappedX) } ?: -1
                        }
                    }
                },
        ) {
            val gridColor = Color.Gray.copy(alpha = 0.16f)
            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (sorted.isEmpty()) return@Canvas
            val coordinates = sorted.toOffsets(size.width, size.height, 8.dp.toPx())
            if (coordinates.size > 1) {
                val line = pathThrough(coordinates)
                val fill = Path().apply {
                    addPath(line)
                    lineTo(coordinates.last().x, size.height)
                    lineTo(coordinates.first().x, size.height)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)),
                )
                drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            coordinates.forEachIndexed { index, offset ->
                if (index == selectedIndex || coordinates.size == 1) {
                    drawCircle(MaterialThemeColorFallback, 5.dp.toPx(), offset)
                    drawCircle(color, 3.dp.toPx(), offset)
                    drawLine(
                        color.copy(alpha = 0.35f),
                        Offset(offset.x, 0f),
                        Offset(offset.x, size.height),
                        1.dp.toPx(),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SignalBarChart(
    bars: List<ChartBarUi>,
    summary: String,
    color: Color,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 160.dp,
    showEdgeLabels: Boolean = true,
) {
    val visibleBars = remember(bars) { bars.take(12) }
    val maximum = max(visibleBars.maxOfOrNull { abs(it.value) } ?: 0f, 1f)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        Canvas(Modifier.fillMaxWidth().height(plotHeight)) {
            if (visibleBars.isEmpty()) return@Canvas
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (visibleBars.size - 1)) / visibleBars.size).coerceAtLeast(2.dp.toPx())
            val zeroY = if (visibleBars.any { it.value < 0f }) size.height / 2f else size.height
            drawLine(
                Color.Gray.copy(alpha = 0.2f),
                Offset(0f, zeroY),
                Offset(size.width, zeroY),
                1.dp.toPx(),
            )
            visibleBars.forEachIndexed { index, bar ->
                val available = if (bar.value < 0) size.height - zeroY else zeroY
                val height = (abs(bar.value) / maximum) * (available - 4.dp.toPx())
                val left = index * (barWidth + gap)
                val top = if (bar.value >= 0) zeroY - height else zeroY
                drawRoundRect(
                    color = bar.color ?: color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, max(height, 1.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
            }
        }
        if (showEdgeLabels && visibleBars.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(visibleBars.first().label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (visibleBars.size > 1) {
                    Text(visibleBars.last().label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SignalDistributionBar(
    parts: List<DistributionPartUi>,
    summary: String,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
) {
    val usable = remember(parts) { parts.filter { it.value > 0f } }
    val total = usable.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50)),
        ) {
            if (usable.isEmpty()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                usable.forEach { part ->
                    Box(
                        Modifier
                            .weight(part.value / total)
                            .fillMaxSize()
                            .background(part.color),
                    )
                }
            }
        }
        if (showLegend) {
            usable.chunked(2).forEach { rowParts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowParts.forEach { part ->
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(part.color))
                            Text(
                                text = "${part.label} ${(part.value / total * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (rowParts.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SignalCalendarHeatmap(
    days: List<CalendarDayUi>,
    summary: String,
    color: Color,
    modifier: Modifier = Modifier,
    cellSize: Dp = 15.dp,
    gap: Dp = 5.dp,
) {
    val weeks = remember(days) { days.chunked(7) }
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (day.intensity <= 0f) MaterialTheme.colorScheme.surfaceVariant
                                else color.copy(alpha = 0.18f + 0.82f * day.intensity.coerceIn(0f, 1f)),
                            ),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.size(cellSize)) }
            }
        }
    }
}

private fun List<ChartPointUi>.toOffsets(width: Float, height: Float, inset: Float): List<Offset> {
    if (isEmpty()) return emptyList()
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    val xSpan = (maxX - minX).takeIf { abs(it) > 0.0001f } ?: 1f
    val ySpan = (maxY - minY).takeIf { abs(it) > 0.0001f } ?: 1f
    return map { point ->
        Offset(
            x = if (size == 1) width / 2f else ((point.x - minX) / xSpan) * width,
            y = inset + (1f - ((point.y - minY) / ySpan)) * (height - inset * 2),
        )
    }
}

private fun pathThrough(points: List<Offset>) = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
}

private val MaterialThemeColorFallback = Color(0xFFFDFCF8)
