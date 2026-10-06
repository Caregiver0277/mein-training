package de.beispiel.meintraining.ui.tracking

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartCursorTest {

    /** Punkte liegen hier direkt auf ihren Koordinaten: Zeit = x, Wert = y. */
    private val atItself: (ChartPoint) -> Offset = { Offset(it.timeMillis.toFloat(), it.plotted.toFloat()) }

    private val series = listOf(
        ChartSeries("A", listOf(ChartPoint(0, 100.0), ChartPoint(100, 100.0))),
        ChartSeries("B", listOf(ChartPoint(0, 20.0), ChartPoint(100, 30.0)))
    )

    @Test
    fun derFingerTrifftDenNaechstenPunktUeberAlleKurven() {
        assertEquals(ChartSelection(1, 1), nearestPoint(series, Offset(95f, 35f), positionOf = atItself))
        assertEquals(ChartSelection(0, 1), nearestPoint(series, Offset(95f, 90f), positionOf = atItself))
    }

    @Test
    fun tippenDanebenTrifftNichts() {
        assertNull(nearestPoint(series, Offset(50f, 60f), maxDistance = 10f, positionOf = atItself))
    }

    @Test
    fun beiGleichstandGewinntDerSpaetere() {
        // Echter und übernommener Stand am selben Fleck: gemeint ist der jüngere.
        val line = listOf(ChartSeries("A", listOf(ChartPoint(10, 5.0), ChartPoint(10, 5.0, isCarried = true))))
        assertEquals(ChartSelection(0, 1), nearestPoint(line, Offset(10f, 5f), positionOf = atItself))
    }

    @Test
    fun ohneKurvenGibtEsNichtsZuTreffen() {
        assertNull(nearestPoint(emptyList(), Offset.Zero, positionOf = atItself))
    }

    private val area = PlotArea(left = 40f, width = 300f, height = 200f)

    @Test
    fun beschriftungStehtMittigUeberDemPunkt() {
        val topLeft = labelTopLeft(Offset(190f, 120f), 100f, 40f, area, gap = 10f)
        assertEquals(Offset(140f, 70f), topLeft)
    }

    @Test
    fun amRandRutschtSieZurSeite() {
        val left = labelTopLeft(Offset(45f, 120f), 100f, 40f, area, gap = 10f)
        assertEquals(40f, left.x)
        val right = labelTopLeft(Offset(335f, 120f), 100f, 40f, area, gap = 10f)
        assertEquals(240f, right.x)
    }

    @Test
    fun obenOhnePlatzSiehtSieDarunter() {
        val topLeft = labelTopLeft(Offset(190f, 20f), 100f, 40f, area, gap = 10f)
        assertEquals(30f, topLeft.y)
    }

    @Test
    fun sieBleibtInnerhalbDerFlaeche() {
        // Größer als die Fläche selbst: dann wenigstens bündig oben links, nie davor.
        val topLeft = labelTopLeft(Offset(190f, 20f), 400f, 300f, area, gap = 10f)
        assertEquals(Offset(40f, 0f), topLeft)
        val low = labelTopLeft(Offset(190f, 195f), 100f, 40f, area, gap = 10f)
        assertTrue(low.y + 40f <= area.height)
    }

    @Test
    fun prozentAufEineStelle() {
        assertEquals(12.3, roundedPercent(12.345), 0.0)
        assertEquals(-2.5, roundedPercent(-2.5), 0.0)
        // Kein „−0“ für eine winzige Senkung.
        assertEquals(0.0, roundedPercent(-0.04), 0.0)
        assertTrue(1.0 / roundedPercent(-0.04) > 0)
    }
}
