package de.beispiel.meintraining.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChartGridLine
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.toDecimalString
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Verlaufsgraph der Gewichte. Waagerechte Hilfslinien erleichtern das Ablesen, jede
 * Gewichtsänderung bekommt einen Punkt, dazwischen laufen gerade Strecken. Vor dem ersten
 * und nach dem letzten Punkt einer Übung wird nichts gezeichnet.
 */
@Composable
fun WeightChart(
    series: List<ChartSeries>,
    window: TimeWindow,
    ticks: List<AxisTick>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = AppTextStyles.ColumnLabel.copy(color = TextSecondary)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ChartHeight),
        contentAlignment = Alignment.Center
    ) {
        if (series.isEmpty()) {
            Text(
                text = stringResource(R.string.tracking_empty),
                style = AppTextStyles.Body,
                color = TextSecondary
            )
            return@Box
        }

        val scale = remember(series) { verticalScaleFor(series) }

        // Die Beschriftungen hängen nur an Skala und Zeitachse – gemessen wird deshalb einmal
        // und nicht in jedem Zeichendurchgang neu.
        val gridLabels = remember(scale, axisStyle, measurer) {
            scale.lines.map { measurer.measure(it.toDecimalString(), axisStyle) }
        }
        val tickLabels = remember(ticks, axisStyle, measurer) {
            ticks.map { measurer.measure(it.label, axisStyle) }
        }
        val labelInset = remember(gridLabels) {
            gridLabels.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftInset = labelInset + AXIS_GAP.toPx()
            val bottomInset = AXIS_LABEL_HEIGHT.toPx()
            val plotWidth = size.width - leftInset
            val plotHeight = size.height - bottomInset
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            drawHorizontalGrid(gridLabels, scale, leftInset, plotWidth, plotHeight)
            drawTimeAxis(tickLabels, ticks, window, leftInset, plotWidth, plotHeight)

            series.forEachIndexed { index, line ->
                drawSeries(
                    line = line,
                    appearance = appearanceFor(index),
                    window = window,
                    scale = scale,
                    leftInset = leftInset,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight
                )
            }
        }
    }
}

/** Wertebereich der Y-Achse samt der Höhe der Hilfslinien. */
private data class VerticalScale(val min: Double, val max: Double, val lines: List<Double>)

/**
 * Legt die Y-Achse auf runde Stufen (…, 2,5, 5, 10 …) statt auf die rohen Messwerte – nur so
 * lassen sich Zwischenwerte an den Hilfslinien überhaupt ablesen.
 */
private fun verticalScaleFor(series: List<ChartSeries>): VerticalScale {
    val weights = series.flatMap { line -> line.points.map { it.weightKg } }
    val rawMin = weights.minOrNull() ?: 0.0
    val rawMax = weights.maxOrNull() ?: 0.0

    // Bei nur einem Wert braucht die Achse trotzdem Höhe, sonst liegt die Linie auf dem Rand.
    val center = (rawMin + rawMax) / 2
    val span = maxOf(rawMax - rawMin, MIN_SPAN_KG)
    val step = niceStep(span / (GRID_LINES - 1))

    var min = floor((center - span / 2) / step) * step
    var max = ceil((center + span / 2) / step) * step
    // Läge ein Messpunkt genau auf der Kante, wäre er halb abgeschnitten.
    if (rawMin - min < step * EDGE_TOLERANCE) min -= step
    if (max - rawMax < step * EDGE_TOLERANCE) max += step

    val lineCount = ((max - min) / step).roundToInt() + 1
    return VerticalScale(min, max, List(lineCount) { min + step * it })
}

private fun niceStep(raw: Double): Double =
    NICE_STEPS.firstOrNull { it >= raw } ?: NICE_STEPS.last()

private fun DrawScope.drawHorizontalGrid(
    labels: List<TextLayoutResult>,
    scale: VerticalScale,
    leftInset: Float,
    plotWidth: Float,
    plotHeight: Float
) {
    scale.lines.forEachIndexed { index, value ->
        val y = yFor(value, scale, plotHeight)
        drawLine(
            color = ChartGridLine,
            start = Offset(leftInset, y),
            end = Offset(leftInset + plotWidth, y),
            strokeWidth = GRID_STROKE.toPx()
        )
        val label = labels[index]
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = leftInset - AXIS_GAP.toPx() - label.size.width,
                y = y - label.size.height / 2f
            )
        )
    }
}

private fun DrawScope.drawTimeAxis(
    labels: List<TextLayoutResult>,
    ticks: List<AxisTick>,
    window: TimeWindow,
    leftInset: Float,
    plotWidth: Float,
    plotHeight: Float
) {
    ticks.forEachIndexed { index, tick ->
        val x = leftInset + xShare(tick.timeMillis, window) * plotWidth
        val label = labels[index]
        // Die Beschriftung bleibt innerhalb der Fläche, damit am Rand nichts abgeschnitten wird.
        val left = (x - label.size.width / 2f)
            .coerceIn(leftInset, leftInset + plotWidth - label.size.width)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(left, plotHeight + AXIS_GAP.toPx())
        )
    }
}

private fun DrawScope.drawSeries(
    line: ChartSeries,
    appearance: SeriesAppearance,
    window: TimeWindow,
    scale: VerticalScale,
    leftInset: Float,
    plotWidth: Float,
    plotHeight: Float
) {
    val positions = line.points.map { point ->
        Offset(
            x = leftInset + xShare(point.timeMillis, window) * plotWidth,
            y = yFor(point.weightKg, scale, plotHeight)
        )
    }
    if (positions.size >= 2) {
        val path = Path().apply {
            moveTo(positions.first().x, positions.first().y)
            positions.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = appearance.color,
            style = Stroke(
                width = SERIES_STROKE.toPx(),
                pathEffect = appearance.style.pathEffect
            )
        )
    }
    // Jeder Punkt ist eine eingetragene Änderung und wird auch als solcher gezeigt.
    positions.forEach { position ->
        drawCircle(
            color = appearance.color,
            radius = POINT_RADIUS.toPx(),
            center = position
        )
    }
}

private fun xShare(timeMillis: Long, window: TimeWindow): Float {
    val span = (window.endMillis - window.startMillis).toFloat()
    if (span <= 0f) return 0f
    return ((timeMillis - window.startMillis) / span).coerceIn(0f, 1f)
}

private fun yFor(value: Double, scale: VerticalScale, plotHeight: Float): Float {
    val span = scale.max - scale.min
    if (abs(span) < Double.MIN_VALUE) return plotHeight / 2f
    return (plotHeight * (1.0 - (value - scale.min) / span)).toFloat()
}

private const val GRID_LINES = 5
private const val MIN_SPAN_KG = 2.5
private const val EDGE_TOLERANCE = 0.15
private val NICE_STEPS = listOf(0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
private val GRID_STROKE = 1.dp
private val SERIES_STROKE = 2.dp
private val POINT_RADIUS = 3.dp
private val AXIS_GAP = 6.dp
private val AXIS_LABEL_HEIGHT = 22.dp
