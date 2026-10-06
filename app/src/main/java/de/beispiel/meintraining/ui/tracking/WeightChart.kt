package de.beispiel.meintraining.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChartCursorLine
import de.beispiel.meintraining.ui.theme.ChartGridLine
import de.beispiel.meintraining.ui.theme.ChartLabelBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatShortDate
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toLocalDate
import de.beispiel.meintraining.util.toSignedDecimalString
import java.time.LocalDate
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
 * Tippen oder Ziehen zeigt einen senkrechten Cursor am nächsten Punkt und beschriftet ihn mit
 * Übung, Gewicht und Datum – in Prozent auch mit der Veränderung. Die Anzeige bleibt nach dem
 * Loslassen stehen; ein Tippen abseits aller Punkte blendet sie aus. Der Graph liegt in keinem
 * scrollbaren Bereich, das Ziehen nimmt also niemandem etwas weg – auch nicht der Legende
 * darunter, die für sich scrollt.
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

        // Neue Kurven – anderer Zeitraum, andere Einheit, ein gelöschter Punkt – verwerfen die
        // Auswahl: Die Stelle, auf die sie zeigt, gibt es dann womöglich nicht mehr.
        var selection by remember(series, window) { mutableStateOf<ChartSelection?>(null) }
        val selectedLine = selection?.let { series.getOrNull(it.seriesIndex) }
        val selectedPoint = selection?.let { selectedLine?.points?.getOrNull(it.pointIndex) }
        val cursorLabel = selectedPoint?.let { point ->
            val details = cursorDetails(point, isPercent)
            remember(selectedLine, point, details, measurer) {
                measurer.measure(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) { append(selectedLine?.name.orEmpty()) }
                        append("\n")
                        append(details)
                    },
                    style = axisStyle
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(series, window, scale, labelInset) {
                    val touchRadius = TOUCH_RADIUS.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val area = plotArea(size.width.toFloat(), size.height.toFloat(), labelInset)
                        fun nearestTo(touch: Offset, maxDistance: Float) =
                            nearestPoint(series, touch, maxDistance) { positionOf(it, window, scale, area) }

                        var dragging = false
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragging) {
                                val moved = (change.position - down.position).getDistance()
                                dragging = moved > viewConfiguration.touchSlop
                            }
                            // Beim Ziehen folgt der Cursor dem Finger zum jeweils nächsten Punkt,
                            // egal wie weit der entfernt ist.
                            if (dragging) {
                                selection = nearestTo(change.position, Float.POSITIVE_INFINITY)
                                change.consume()
                            }
                        }
                        // Ein Tippen trifft nur in der Nähe eines Punkts – sonst blendet es aus.
                        if (!dragging) selection = nearestTo(down.position, touchRadius)
                    }
                }
        ) {
            val area = plotArea(size.width, size.height, labelInset)
            if (area.width <= 0f || area.height <= 0f) return@Canvas

            drawHorizontalGrid(gridLabels, scale, area)
            drawTimeAxis(tickLabels, ticks, window, area)

            series.forEachIndexed { index, line ->
                drawSeries(
                    line = line,
                    appearance = appearanceFor(index),
                    window = window,
                    scale = scale,
                    area = area
                )
            }

            val current = selection
            if (current != null && selectedPoint != null && cursorLabel != null) {
                drawCursor(
                    anchor = positionOf(selectedPoint, window, scale, area),
                    appearance = appearanceFor(current.seriesIndex),
                    label = cursorLabel,
                    area = area
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

/**
 * Die Zeichenfläche bei [width] mal [height] Pixeln: links Platz für die Gewichte, unten für
 * die Zeitachse. Zeichnen und Antippen rechnen mit derselben, sonst träfe der Finger daneben.
 */
private fun Density.plotArea(width: Float, height: Float, labelInset: Float): PlotArea {
    val left = labelInset + AXIS_GAP.toPx()
    return PlotArea(left = left, width = width - left, height = height - AXIS_LABEL_HEIGHT.toPx())
}

/**
 * Zweite Zeile der Beschriftung am Cursor: „60 kg · 18. Sept.“, in Prozent
 * „60 kg · +12,5 % · 18. Sept.“. Liegt das Datum nicht im laufenden Jahr, steht das Jahr dabei.
 */
@Composable
private fun cursorDetails(point: ChartPoint, isPercent: Boolean): String {
    val weight = point.weightKg.toDecimalString()
    val date = formatShortDate(point.recordedAt.toLocalDate(), LocalDate.now())
    val percent = point.percent?.let(::roundedPercent)
    return if (isPercent && percent != null) {
        stringResource(
            R.string.tracking_cursor_percent,
            weight,
            // Der Anfang der Kurve ist schlicht „0 %“, ohne Vorzeichen.
            if (percent == 0.0) percent.toDecimalString() else percent.toSignedDecimalString(),
            date
        )
    } else {
        stringResource(R.string.tracking_cursor_kg, weight, date)
    }
}

private fun DrawScope.drawHorizontalGrid(
    labels: List<TextLayoutResult>,
    scale: VerticalScale,
    area: PlotArea
) {
    scale.lines.forEachIndexed { index, value ->
        val y = yFor(value, scale, area.height)
        drawLine(
            color = ChartGridLine,
            start = Offset(area.left, y),
            end = Offset(area.right, y),
            strokeWidth = GRID_STROKE.toPx()
        )
        val label = labels[index]
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = area.left - AXIS_GAP.toPx() - label.size.width,
                y = y - label.size.height / 2f
            )
        )
    }
}

private fun DrawScope.drawTimeAxis(
    labels: List<TextLayoutResult>,
    ticks: List<AxisTick>,
    window: TimeWindow,
    area: PlotArea
) {
    ticks.forEachIndexed { index, tick ->
        val x = area.left + xShare(tick.timeMillis, window) * area.width
        val label = labels[index]
        // Die Beschriftung bleibt innerhalb der Fläche, damit am Rand nichts abgeschnitten wird.
        val left = (x - label.size.width / 2f)
            .coerceIn(area.left, area.right - label.size.width)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(left, area.height + AXIS_GAP.toPx())
        )
    }
}

private fun DrawScope.drawSeries(
    line: ChartSeries,
    appearance: SeriesAppearance,
    window: TimeWindow,
    scale: VerticalScale,
    area: PlotArea
) {
    val at = { point: ChartPoint -> positionOf(point, window, scale, area) }

    line.pieces.forEach { piece ->
        val positions = piece.points.map(at)
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
            center = at(point)
        )
    }
}

/**
 * Der Cursor: eine senkrechte Linie über die ganze Höhe, ein Ring um den gewählten Punkt in der
 * Farbe seiner Kurve und darüber die Beschriftung auf eigenem Grund.
 */
private fun DrawScope.drawCursor(
    anchor: Offset,
    appearance: SeriesAppearance,
    label: TextLayoutResult,
    area: PlotArea
) {
    drawLine(
        color = ChartCursorLine,
        start = Offset(anchor.x, 0f),
        end = Offset(anchor.x, area.height),
        strokeWidth = GRID_STROKE.toPx()
    )
    drawCircle(color = appearance.color, radius = POINT_RADIUS.toPx(), center = anchor)
    drawCircle(
        color = appearance.color,
        radius = HIGHLIGHT_RADIUS.toPx(),
        center = anchor,
        style = Stroke(width = SERIES_STROKE.toPx())
    )

    val padding = LABEL_PADDING.toPx()
    val boxWidth = label.size.width + 2 * padding
    val boxHeight = label.size.height + 2 * padding
    val topLeft = labelTopLeft(anchor, boxWidth, boxHeight, area, gap = HIGHLIGHT_RADIUS.toPx() + padding)
    drawRoundRect(
        color = ChartLabelBackground,
        topLeft = topLeft,
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(LABEL_CORNER.toPx())
    )
    drawText(textLayoutResult = label, topLeft = topLeft + Offset(padding, padding))
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
private val HIGHLIGHT_RADIUS = 7.dp
private val LABEL_PADDING = 6.dp
private val LABEL_CORNER = 6.dp

/** Wie nah ein Tippen an einem Punkt liegen muss, um ihn zu treffen – etwa eine Fingerkuppe. */
private val TOUCH_RADIUS = 32.dp
private val AXIS_GAP = 6.dp
private val AXIS_LABEL_HEIGHT = 22.dp
