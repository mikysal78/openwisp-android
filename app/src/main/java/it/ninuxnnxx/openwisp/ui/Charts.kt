package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import it.ninuxnnxx.openwisp.data.Chart
import it.ninuxnnxx.openwisp.data.series
import it.ninuxnnxx.openwisp.data.summaryValues

private val palette = listOf(
    Color(0xFF1F77B4), Color(0xFFFF7F0E), Color(0xFF2CA02C), Color(0xFFD62728), Color(0xFF9467BD),
)

private fun parseColor(hex: String?): Color? = runCatching {
    val clean = hex?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    Color(0xFF000000 or clean.toLong(16))
}.getOrNull()

private fun format(value: Double?): String = when {
    value == null -> "-"
    value >= 100 || value == Math.floor(value) -> "%.0f".format(value)
    else -> "%.2f".format(value)
}

/**
 * Grafico di openwisp-monitoring: barre per i grafici "bar" a una traccia
 * (es. ping), linee per tutti gli altri. Le etichette dell'asse x vengono
 * dall'API (ora del server); qui si mostrano solo il primo e l'ultimo punto.
 */
@Composable
fun ChartCard(chart: Chart, x: List<String>) {
    val series = chart.series()
    if (series.isEmpty() || series.all { (_, v) -> v.all { it == null } }) return
    val colors = series.indices.map { parseColor(chart.colors?.getOrNull(it)) ?: palette[it % palette.size] }
    val unit = chart.unit?.trim().orEmpty()
    val all = series.flatMap { it.second }.filterNotNull()
    val max = (all.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
    val min = minOf(0.0, all.minOrNull() ?: 0.0)
    val bars = chart.type == "bar" && series.size == 1
    val summary = chart.summaryValues()
    val grid = MaterialTheme.colorScheme.outlineVariant

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(chart.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "max ${format(max)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 6.dp)) {
                val range = max - min
                fun y(v: Double) = size.height - ((v - min) / range * size.height).toFloat()
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                series.forEachIndexed { index, (_, values) ->
                    val n = values.size
                    if (n == 0) return@forEachIndexed
                    val step = if (n > 1) size.width / (n - 1) else size.width
                    if (bars) {
                        val w = (size.width / n * 0.8f).coerceAtLeast(1f)
                        values.forEachIndexed { i, v ->
                            if (v == null) return@forEachIndexed
                            val left = size.width * i / n
                            drawRect(colors[index], Offset(left, y(v)), Size(w, size.height - y(v)))
                        }
                    } else {
                        val path = Path()
                        var started = false
                        values.forEachIndexed { i, v ->
                            if (v == null) {
                                started = false
                                return@forEachIndexed
                            }
                            val px = step * i
                            if (started) path.lineTo(px, y(v)) else path.moveTo(px, y(v))
                            started = true
                        }
                        drawPath(path, colors[index], style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
            if (x.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    Text(x.first(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text(x.last(), style = MaterialTheme.typography.labelSmall)
                }
            }
            FlowRow(Modifier.padding(top = 4.dp)) {
                series.forEachIndexed { index, (name, values) ->
                    Row(Modifier.padding(end = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(8.dp), shape = CircleShape, color = colors[index]) {}
                        val value = summary[name] ?: values.lastOrNull { it != null }
                        Text(
                            "${name.replace('_', ' ')}: ${format(value)} $unit",
                            Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
