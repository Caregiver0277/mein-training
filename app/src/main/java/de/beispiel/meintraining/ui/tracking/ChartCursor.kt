package de.beispiel.meintraining.ui.tracking

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.round

/**
 * Der angetippte Punkt im Graphen: welche Kurve und welche Stelle darin.
 *
 * Über die Stellen statt über den Punkt selbst gemerkt, damit sich Kurve und Farbe dazu ohne
 * Suche finden lassen.
 */
data class ChartSelection(val seriesIndex: Int, val pointIndex: Int)

/** Die Zeichenfläche des Graphen ohne die Achsenbeschriftungen, in Pixeln. */
internal data class PlotArea(val left: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
}

/** Wo [point] in [area] liegt. */
internal fun positionOf(
    point: ChartPoint,
    window: TimeWindow,
    scale: VerticalScale,
    area: PlotArea
): Offset = Offset(
    x = area.left + xShare(point.timeMillis, window) * area.width,
    y = yFor(point.plotted, scale, area.height)
)

/**
 * Der Punkt, der [touch] am nächsten liegt – über alle Kurven, nach Abstand auf dem Bildschirm.
 *
 * Gemessen wird in beiden Richtungen: Liegen zwei Kurven übereinander, entscheidet die Höhe des
 * Fingers, welche gemeint ist. Übernommene Stände zählen mit – am linken Rand ist das der Wert,
 * von dem die Kurve ausgeht, und in Prozent ihr Bezug.
 *
 * Liegt keiner näher als [maxDistance], ist nichts getroffen (`null`): So blendet ein Tippen
 * daneben die Anzeige wieder aus.
 */
internal fun nearestPoint(
    series: List<ChartSeries>,
    touch: Offset,
    maxDistance: Float = Float.POSITIVE_INFINITY,
    positionOf: (ChartPoint) -> Offset
): ChartSelection? {
    var best: ChartSelection? = null
    var bestDistance = maxDistance
    series.forEachIndexed { seriesIndex, line ->
        line.points.forEachIndexed { pointIndex, point ->
            val distance = (positionOf(point) - touch).getDistance()
            if (distance <= bestDistance) {
                // Bei Gleichstand gewinnt der spätere Punkt: Am rechten Ende liegen echter und
                // übernommener Stand oft aufeinander, und dann ist der jüngere gemeint.
                best = ChartSelection(seriesIndex, pointIndex)
                bestDistance = distance
            }
        }
    }
    return best
}

/**
 * Linke obere Ecke der Beschriftung am Punkt [anchor]: mittig darüber, und wo dort kein Platz
 * ist, darunter. Sie bleibt immer ganz in [area] – am Rand rutscht sie zur Seite statt
 * abgeschnitten zu werden.
 */
internal fun labelTopLeft(
    anchor: Offset,
    labelWidth: Float,
    labelHeight: Float,
    area: PlotArea,
    gap: Float
): Offset {
    val left = (anchor.x - labelWidth / 2f)
        .coerceIn(area.left, maxOf(area.left, area.right - labelWidth))
    val above = anchor.y - gap - labelHeight
    val top = if (above >= 0f) above else anchor.y + gap
    return Offset(left, top.coerceIn(0f, maxOf(0f, area.height - labelHeight)))
}

/** Prozentwert für die Beschriftung, auf eine Nachkommastelle: `12.345 → 12.3`. */
internal fun roundedPercent(percent: Double): Double {
    val rounded = round(percent * PERCENT_DECIMALS) / PERCENT_DECIMALS
    // Aus -0,04 würde sonst „−0“.
    return if (abs(rounded) < HALF_STEP) 0.0 else rounded
}

/** Anteil der Breite, an dem [timeMillis] im Fenster liegt. */
internal fun xShare(timeMillis: Long, window: TimeWindow): Float {
    val span = (window.endMillis - window.startMillis).toFloat()
    if (span <= 0f) return 0f
    return ((timeMillis - window.startMillis) / span).coerceIn(0f, 1f)
}

/** Höhe eines Werts in der Fläche; oben ist das Maximum der Skala. */
internal fun yFor(value: Double, scale: VerticalScale, plotHeight: Float): Float {
    val span = scale.max - scale.min
    if (abs(span) < Double.MIN_VALUE) return plotHeight / 2f
    return (plotHeight * (1.0 - (value - scale.min) / span)).toFloat()
}

private const val PERCENT_DECIMALS = 10.0
private const val HALF_STEP = 0.05
