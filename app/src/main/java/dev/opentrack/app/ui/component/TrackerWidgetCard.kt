package dev.opentrack.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.chart.SignalBarChart
import dev.opentrack.app.ui.chart.SignalCalendarHeatmap
import dev.opentrack.app.ui.chart.SignalDonutChart
import dev.opentrack.app.ui.chart.SignalScatterPlot
import dev.opentrack.app.ui.chart.SignalDistributionBar
import dev.opentrack.app.ui.chart.SignalSparkline
import dev.opentrack.app.ui.model.DashboardWidgetUi
import dev.opentrack.app.ui.model.WidgetChartUi
import dev.opentrack.app.ui.model.WidgetSizeUi

@Composable
fun TrackerWidgetCard(
    widget: DashboardWidgetUi,
    onOpen: () -> Unit,
    onQuickLog: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    val compact = widget.size == WidgetSizeUi.COMPACT
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 184.dp else 214.dp)
            .clickable(role = Role.Button, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(widget.glyph, widget.accent, null, Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    widget.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onMore != null && !compact) {
                    IconButton(onClick = onMore, Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = "Widget options")
                    }
                }
                QuickAddButton(widget.quickActionLabel, widget.accent, onQuickLog)
            }
            Column {
                Text(
                    widget.metric,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    widget.context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                WidgetChart(widget.chart, widget.accent, compact)
            }
        }
    }
}

@Composable
private fun WidgetChart(chart: WidgetChartUi, accent: Color, compact: Boolean) {
    when (chart) {
        WidgetChartUi.None -> Spacer(Modifier.height(1.dp))
        is WidgetChartUi.Sparkline -> SignalSparkline(
            points = chart.points,
            summary = chart.summary,
            color = accent,
            modifier = Modifier.fillMaxWidth().height(if (compact) 40.dp else 52.dp),
        )
        is WidgetChartUi.Area -> SignalSparkline(
            points = chart.points,
            summary = chart.summary,
            color = accent,
            modifier = Modifier.fillMaxWidth().height(if (compact) 40.dp else 52.dp),
            fillArea = true,
        )
        is WidgetChartUi.Scatter -> SignalScatterPlot(
            points = chart.points,
            summary = chart.summary,
            color = accent,
            modifier = Modifier.fillMaxWidth(),
        )
        is WidgetChartUi.Bars -> SignalBarChart(
            bars = chart.bars,
            summary = chart.summary,
            color = accent,
            modifier = Modifier.fillMaxWidth(),
            plotHeight = 44.dp,
            showEdgeLabels = false,
        )
        is WidgetChartUi.Distribution -> SignalDistributionBar(
            parts = chart.parts,
            summary = chart.summary,
            modifier = Modifier.fillMaxWidth(),
            showLegend = false,
        )
        is WidgetChartUi.Donut -> SignalDonutChart(
            parts = chart.parts,
            summary = chart.summary,
            modifier = Modifier.fillMaxWidth(),
        )
        is WidgetChartUi.Calendar -> SignalCalendarHeatmap(
            grid = chart.grid,
            summary = chart.summary,
            color = accent,
            modifier = Modifier.fillMaxWidth(),
            maxCellSize = if (compact) 16.dp else 22.dp,
            gap = 2.dp,
        )
    }
}
