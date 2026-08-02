package de.beispiel.meintraining.ui.tracking

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import de.beispiel.meintraining.ui.theme.SeriesColors

/**
 * Linienarten in absteigender „Normalität“. [SOLID] ist die am besten lesbare und geht
 * deshalb an die erste angezeigte Übung.
 */
enum class SeriesStyle(private val intervals: FloatArray?) {
    SOLID(null),
    DASHED(floatArrayOf(20f, 12f)),
    DOTTED(floatArrayOf(4f, 9f)),
    DASH_DOT(floatArrayOf(22f, 9f, 4f, 9f)),
    LONG_DASH(floatArrayOf(38f, 14f));

    fun pathEffect(): PathEffect? = intervals?.let { PathEffect.dashPathEffect(it) }
}

/** Farbe und Linienart einer Kurve. */
data class SeriesAppearance(val color: Color, val style: SeriesStyle)

/**
 * Bestimmt das Aussehen der [index]-ten *angezeigten* Kurve.
 *
 * Farbe und Linienart laufen gemeinsam weiter, damit sich benachbarte Kurven in beidem
 * unterscheiden – das trennt sie deutlich besser, als nur eine der beiden Eigenschaften zu
 * variieren. Weil der Index sich auf die gerade sichtbare Auswahl bezieht, rutschen die
 * verbleibenden Kurven nach dem Abwählen anderer automatisch wieder auf die vorderen,
 * ruhigeren Darstellungen.
 */
fun appearanceFor(index: Int): SeriesAppearance = SeriesAppearance(
    color = SeriesColors[index % SeriesColors.size],
    style = SeriesStyle.entries[index % SeriesStyle.entries.size]
)
