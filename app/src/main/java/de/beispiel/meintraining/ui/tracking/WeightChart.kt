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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Verlaufsgraph der Gewichte. Waagerechte Hilfslinien erleichtern das Ablesen, jede
 * Gewichtsänderung bekommt einen Punkt, dazwischen laufen gerade Strecken.
 *
 * Ein Gewicht ist ein Zustand, der bis zur nächsten Änderung gilt: Ein Stand von vor dem
 * Zeitraum beginnt die Linie am linken Rand, der letzte Stand läuft bis heute weiter (siehe
 * [buildSeries]). Diese übernommenen Stücke sind dünner und blasser und tragen keinen Punkt –
 * so bleiben echte Änderungen als Punkte erkennbar. Gestrichelt werden sie bewusst nicht:
 * Strichmuster unterscheiden schon die Übungen voneinander (siehe [SeriesStyle]).
 *
 * In der %-Ansicht ([isPercent]) stehen die Punkte nach ihrer Veränderung in der Höhe (siehe
 * [toPercentSeries]) und die Achse ist in Prozent beschriftet.
 *
 * [emptyText] steht da, wenn es nichts zu zeichnen gibt – warum, weiß nur der Aufrufer.
 */
@Composable
fun WeightChart(
    series: List<ChartSeries>,
    window: TimeWindow,
    ticks: List<AxisTick>,
    emptyText: String,
    modifier: Modifier = Modifier,
    isPercent: Boolean = false
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = AppTextStyles.ColumnLabel.copy(color = TextSecondary)
    val resources = LocalResources.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ChartHeight),
        contentAlignment = Alignment.Center
    ) {
        // Eine Kurve ohne Punkte ist eine in Prozent ohne Bezugswert: Sie steht nur in der Legende.
        if (series.none { it.points.isNotEmpty() }) {
            Text(
                text = emptyText,
                style = AppTextStyles.Body,
                color = TextSecondary
            )
            return@Box
        }

        val scale = remember(series) { verticalScaleFor(series) }

        // Die Beschriftungen hängen nur an Skala und Zeitachse – gemessen wird deshalb einmal
        // und nicht in jedem Zeichendurchgang neu.
        val gridLabels = remember(scale, axisStyle, measurer, isPercent) {
            scale.lines.map { value ->
                val label = if (isPercent) {
                    resources.getString(R.string.tracking_axis_percent, value.toAxisNumber())
                } else {
                    value.toDecimalString()
                }
                measurer.measure(label, axisStyle)
            }
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
internal data class VerticalScale(val min: Double, val max: Double, val lines: List<Double>)

/**
 * Legt die Y-Achse auf runde Stufen (…, 2,5, 5, 10 …) statt auf die rohen Messwerte – nur so
 * lassen sich Zwischenwerte an den Hilfslinien überhaupt ablesen.
 */
internal fun verticalScaleFor(series: List<ChartSeries>): VerticalScale {
    val values = series.flatMap { line -> line.points.map { it.plotted } }
    val rawMin = values.minOrNull() ?: 0.0
    val rawMax = values.maxOrNull() ?: 0.0

    // Bei nur einem Wert braucht die Achse trotzdem Höhe, sonst liegt die Linie auf dem Rand.
    val center = rawMin + (rawMax - rawMin) / 2
    val span = maxOf(rawMax - rawMin, MIN_SPAN)
    val step = niceStep(span / (GRID_LINES - 1))

    var min = floor((center - span / 2) / step) * step
    var max = ceil((center + span / 2) / step) * step
    // Läge ein Messpunkt genau auf der Kante, wäre er halb abgeschnitten.
    if (rawMin - min < step * EDGE_TOLERANCE) min -= step
    if (max - rawMax < step * EDGE_TOLERANCE) max += step
    // Gewichte gibt es erst ab null: Eine Hilfslinie bei „-2,5“ beschriftete etwas, das nicht
    // vorkommen kann. Ein Punkt bei 0 liegt dann auf der untersten Linie – unten ist unter dem
    // Graphen noch die Zeitachse, abgeschnitten wird dort nichts. In Prozent gilt dasselbe,
    // solange keine Kurve unter ihren Anfang fällt: Dann ist 0 % der Boden.
    if (rawMin >= 0.0) min = maxOf(min, 0.0)

    // Mit Stufen nach Größenordnung sind es nie mehr als eine Handvoll Linien. Die Obergrenze
    // fängt nur ab, was sich nicht mehr rechnen lässt – Gewichte nahe am Rand dessen, was eine
    // Kommazahl fassen kann.
    val lineCount = ((max - min) / step).roundToInt().coerceIn(0, MAX_GRID_LINES) + 1
    return VerticalScale(min, max, List(lineCount) { min + step * it })
}

/**
 * Die kleinste runde Stufe, die mindestens [raw] groß ist.
 *
 * Über [NICE_STEPS] hinaus geht es nach Größenordnung weiter: 1, 2, 2,5 oder 5 mal einer
 * Zehnerpotenz. Die Liste endete früher bei 100 und blieb dort stehen. Ein vertipptes Gewicht –
 * „60000“ statt „60“ – zog damit Hunderte Hilfslinien samt Beschriftung nach sich, ein noch
 * größeres Millionen, und das Tracking ging nicht mehr auf. Mit ihm auch nicht die Punktliste,
 * in der sich der falsche Eintrag löschen ließe.
 */
private fun niceStep(raw: Double): Double {
    NICE_STEPS.firstOrNull { it >= raw }?.let { return it }
    val magnitude = 10.0.pow(floor(log10(raw)))
    return LARGE_STEP_FACTORS.firstOrNull { it * magnitude >= raw }?.times(magnitude)
        ?: (LARGE_STEP_FACTORS.first() * 10 * magnitude)
}

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
    fun positionOf(point: ChartPoint) = Offset(
        x = leftInset + xShare(point.timeMillis, window) * plotWidth,
        y = yFor(point.plotted, scale, plotHeight)
    )

    line.pieces.forEach { piece ->
        val positions = piece.points.map(::positionOf)
        val path = Path().apply {
            moveTo(positions.first().x, positions.first().y)
            positions.drop(1).forEach { lineTo(it.x, it.y) }
        }
        // Übernommene Stücke behalten das Strichmuster ihrer Übung – sonst wären sie keiner
        // mehr zuzuordnen – und unterscheiden sich nur in Stärke und Deckkraft.
        drawPath(
            path = path,
            color = if (piece.isCarried) appearance.color.copy(alpha = CARRIED_ALPHA) else appearance.color,
            style = Stroke(
                width = (if (piece.isCarried) CARRIED_STROKE else SERIES_STROKE).toPx(),
                pathEffect = appearance.style.pathEffect
            )
        )
    }
    // Jeder echte Punkt ist eine eingetragene Änderung und wird auch als solcher gezeigt.
    line.points.filterNot { it.isCarried }.forEach { point ->
        drawCircle(
            color = appearance.color,
            radius = POINT_RADIUS.toPx(),
            center = positionOf(point)
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
/** Kleinste Spanne der Y-Achse – in kg wie in Prozent. */
private const val MIN_SPAN = 2.5
private const val EDGE_TOLERANCE = 0.15
private val NICE_STEPS = listOf(0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
private val LARGE_STEP_FACTORS = listOf(1.0, 2.0, 2.5, 5.0)
private const val MAX_GRID_LINES = 12
private val GRID_STROKE = 1.dp
private val SERIES_STROKE = 2.dp
private val CARRIED_STROKE = 1.dp
private const val CARRIED_ALPHA = 0.6f
private val POINT_RADIUS = 3.dp
private val AXIS_GAP = 6.dp
private val AXIS_LABEL_HEIGHT = 22.dp
