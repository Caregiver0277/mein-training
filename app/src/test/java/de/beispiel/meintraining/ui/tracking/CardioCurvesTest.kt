package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.IntensityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_DAY = 24L * 60 * 60 * 1000

private fun unitLabel(unit: IntensityUnit) = when (unit) {
    IntensityUnit.KMH -> "km/h"
    IntensityUnit.LEVEL -> "Stufe"
}

private fun einheit(
    id: Long,
    name: String,
    tag: Long,
    variation: String? = null,
    dauer: Double? = null,
    tempo: Double? = null,
    einheit: IntensityUnit? = null,
    steigung: Double? = null
) = CardioLog(
    id = id,
    exerciseName = name,
    variation = variation,
    dayId = 1,
    performedAt = tag * ONE_DAY,
    durationMin = dauer,
    intensity = tempo,
    intensityUnit = einheit,
    inclinePercent = steigung
)

class CardioCurvesTest {

    @Test
    fun jedeUebungSamtVariationIstEineKurveAusIhrenEinheiten() {
        val logs = listOf(
            einheit(1, "Rad", 1, variation = "locker", dauer = 30.0),
            einheit(2, "Laufband", 2, dauer = 20.0),
            einheit(3, "Rad", 3, variation = "locker", dauer = 32.0),
            einheit(4, "Rad", 4, dauer = 15.0),
            // Ohne Dauer gehört diese Einheit nicht in die Kurve der Dauer.
            einheit(5, "Laufband", 5, steigung = 8.0)
        )
        val kurven = cardioCurves(logs, CardioValue.DURATION, ::unitLabel)
        assertEquals(listOf("Laufband", "Rad", "Rad (locker)"), kurven.map { it.name })
        assertEquals(listOf(30.0, 32.0), kurven[2].points.map { it.amount })
        assertEquals("Rad", kurven[2].exerciseName)
    }

    @Test
    fun kmhUndStufeNieInEinerKurve() {
        val logs = listOf(
            einheit(1, "Crosstrainer", 1, tempo = 6.0, einheit = IntensityUnit.KMH),
            einheit(2, "Crosstrainer", 2, tempo = 8.0, einheit = IntensityUnit.LEVEL),
            einheit(3, "Crosstrainer", 3, tempo = 9.0, einheit = IntensityUnit.LEVEL),
            einheit(4, "Rad", 4, tempo = 5.0, einheit = IntensityUnit.LEVEL)
        )
        val kurven = cardioCurves(logs, CardioValue.INTENSITY, ::unitLabel)
        assertEquals(listOf("Crosstrainer (km/h)", "Crosstrainer (Stufe)", "Rad"), kurven.map { it.name })
        assertEquals(listOf(8.0, 9.0), kurven[1].points.map { it.amount })
        assertEquals(IntensityUnit.LEVEL, kurven[2].unit)
    }

    @Test
    fun imFensterNurEchtePunkteUndNurSichtbareKurven() {
        val logs = listOf(
            einheit(1, "Rad", 1, dauer = 30.0),
            einheit(2, "Rad", 10, dauer = 32.0),
            einheit(3, "Rad", 20, dauer = 35.0),
            einheit(4, "Laufband", 2, dauer = 20.0)
        )
        val kurven = cardioCurves(logs, CardioValue.DURATION, ::unitLabel)
        val fenster = TimeWindow(5 * ONE_DAY, 30 * ONE_DAY)

        val linien = cardioSeries(kurven, setOf("Rad", "Laufband"), fenster)
        // Das Laufband hat im Fenster keine Einheit und fällt weg; kein Stand läuft weiter.
        assertEquals(listOf("Rad"), linien.map { it.name })
        assertEquals(listOf(32.0, 35.0), linien[0].points.map { it.weightKg })
        assertTrue(linien[0].points.none { it.isCarried })

        assertTrue(cardioSeries(kurven, setOf("Laufband"), fenster).isEmpty())
    }

    @Test
    fun inProzentZaehltEineSenkungNurFuerDenWertDesPfeils() {
        val logs = listOf(
            einheit(1, "Laufband", 1, dauer = 30.0, steigung = 4.0),
            einheit(2, "Laufband", 2, dauer = 27.0, steigung = 5.0)
        )
        val laufband = ExerciseDefinition(
            name = "Laufband",
            kind = ExerciseKind.CARDIO,
            cardio = CardioTargets(durationMin = 27.0, arrowValue = CardioValue.DURATION, arrowDown = true)
        )
        val fenster = TimeWindow(0, 10 * ONE_DAY)

        val dauer = cardioCurves(logs, CardioValue.DURATION, ::unitLabel)
        val sinkend = decreasingCardioNames(dauer, listOf(laufband), CardioValue.DURATION)
        assertEquals(setOf("Laufband"), sinkend)
        val prozent = toPercentSeries(cardioSeries(dauer, setOf("Laufband"), fenster), sinkend)
        // 30 auf 27 Minuten: 10 % schneller – Fortschritt.
        assertEquals(10.0, prozent[0].points.last().percent!!, 1e-9)

        // Die Steigung steuert der Pfeil nicht: Dort bleibt mehr eben mehr.
        val steigung = cardioCurves(logs, CardioValue.INCLINE, ::unitLabel)
        assertTrue(decreasingCardioNames(steigung, listOf(laufband), CardioValue.INCLINE).isEmpty())
    }
}
