package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.data.model.WeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC
private val HEUTE: LocalDate = LocalDate.of(2026, 10, 7)

class ExerciseProgressTest {

    // --- Kraft ---------------------------------------------------------------

    @Test
    fun ohneEintragKeinFortschritt() {
        assertNull(strengthProgress("Bankdrücken", emptyList(), isDecreasing = false))
    }

    @Test
    fun einEintragIstStartGleichAktuell() {
        val progress = strengthProgress("Bankdrücken", listOf(weight(60.0, 30)), isDecreasing = false)!!
        assertEquals(60.0, progress.fromKg, 0.0)
        assertEquals(60.0, progress.toKg, 0.0)
        assertEquals(0, progress.increases)
        assertEquals(0.0, progress.gainKg, 0.0)
        assertNull(progress.lastIncreaseAt)
        assertNull(progress.tempo(ZONE))
        assertNull(progress.daysSinceLastIncrease(HEUTE, ZONE))
    }

    @Test
    fun steigerungenZaehlenNurInRichtungFortschritt() {
        val logs = listOf(
            weight(60.0, 44),
            weight(62.5, 33),
            // Eine Korrektur nach unten ist keine Steigerung …
            weight(60.0, 30),
            weight(62.5, 22),
            weight(65.0, 11)
        )
        val progress = strengthProgress("Bankdrücken", logs, isDecreasing = false)!!
        assertEquals(3, progress.increases)
        assertEquals(7.5, progress.increasedKg, 1e-9)
        assertEquals(5.0, progress.gainKg, 1e-9)
        assertEquals(5.0 / 60.0 * 100.0, progress.gainPercent!!, 1e-9)
        assertEquals(11L, progress.daysSinceLastIncrease(HEUTE, ZONE))
    }

    @Test
    fun tempoMisstVomStartBisZurJuengstenSteigerung() {
        val logs = listOf(weight(60.0, 40), weight(62.5, 30), weight(65.0, 18))
        val tempo = strengthProgress("Bankdrücken", logs, isDecreasing = false)!!.tempo(ZONE)!!
        // 22 Tage für 2 Steigerungen – die 18 Tage seither zählen nicht mit.
        assertEquals(11.0, tempo.daysPerIncrease, 1e-9)
        assertEquals(2.5, tempo.amountPerIncrease, 1e-9)
    }

    @Test
    fun eineSteigerungErgibtNochKeinTempo() {
        val logs = listOf(weight(60.0, 40), weight(62.5, 30))
        assertNull(strengthProgress("Bankdrücken", logs, isDecreasing = false)!!.tempo(ZONE))
    }

    @Test
    fun beiPfeilNachUntenIstDieSenkungDerFortschritt() {
        val logs = listOf(weight(40.0, 30), weight(35.0, 20), weight(37.5, 15), weight(30.0, 5))
        val progress = strengthProgress("Klimmzug", logs, isDecreasing = true)!!
        assertEquals(2, progress.increases)
        assertEquals(12.5, progress.increasedKg, 1e-9)
        assertEquals(10.0, progress.gainKg, 1e-9)
        assertEquals(25.0, progress.gainPercent!!, 1e-9)
    }

    @Test
    fun ohneStartgewichtKeinProzentwert() {
        val logs = listOf(weight(0.0, 20), weight(5.0, 10))
        val progress = strengthProgress("Dips", logs, isDecreasing = false)!!
        assertEquals(5.0, progress.gainKg, 0.0)
        assertNull(progress.gainPercent)
    }

    // --- Cardio --------------------------------------------------------------

    @Test
    fun cardioMisstDenWertDesPfeils() {
        val logs = listOf(
            cardio(20, duration = 20.0, distance = 3.0),
            cardio(10, duration = 25.0),
            cardio(2, duration = 30.0, distance = 4.5)
        )
        val targets = CardioTargets(durationMin = 30.0, arrowValue = CardioValue.DISTANCE)
        val progress = cardioProgress("Laufband", null, logs, targets)!!
        assertEquals(CardioValue.DISTANCE, progress.value)
        assertEquals(3.0, progress.from, 0.0)
        assertEquals(4.5, progress.to, 0.0)
        // Nur die Einheiten, in denen die Distanz steht.
        assertEquals(2, progress.entries)
        assertEquals(50.0, progress.gainPercent!!, 1e-9)
        assertFalse(progress.isDecreasing)
    }

    @Test
    fun cardioOhnePfeilNimmtDenErstenVorhandenenWert() {
        val logs = listOf(cardio(5, distance = 2.0), cardio(1, distance = 2.5, incline = 3.0))
        val progress = cardioProgress("Rad", null, logs, CardioTargets())!!
        assertEquals(CardioValue.DISTANCE, progress.value)
        assertNull(cardioProgress("Rad", null, emptyList(), CardioTargets()))
    }

    @Test
    fun cardioMitPfeilNachUntenZaehltWenigerAlsMehr() {
        val logs = listOf(cardio(9, duration = 30.0), cardio(1, duration = 27.0))
        val targets = CardioTargets(durationMin = 27.0, arrowValue = CardioValue.DURATION, arrowDown = true)
        val progress = cardioProgress("Laufband", null, logs, targets)!!
        assertTrue(progress.isDecreasing)
        assertEquals(3.0, progress.gain, 1e-9)
        assertEquals(10.0, progress.gainPercent!!, 1e-9)
    }

    @Test
    fun cardioTempoNurInDerEinheitDerUebung() {
        val logs = listOf(
            cardio(30, intensity = 6.0, unit = IntensityUnit.KMH),
            cardio(20, intensity = 8.0, unit = IntensityUnit.LEVEL),
            cardio(10, intensity = 10.0, unit = IntensityUnit.LEVEL)
        )
        val targets = CardioTargets(
            intensity = 10.0,
            intensityUnit = IntensityUnit.LEVEL,
            arrowValue = CardioValue.INTENSITY
        )
        val progress = cardioProgress("Crosstrainer", null, logs, targets)!!
        assertEquals(IntensityUnit.LEVEL, progress.unit)
        assertEquals(8.0, progress.from, 0.0)
        assertEquals(10.0, progress.to, 0.0)
        assertEquals(2, progress.entries)
    }

    private fun weight(kg: Double, daysAgo: Long) = WeightLog(
        exerciseName = "Bankdrücken",
        weightKg = kg,
        recordedAt = millis(daysAgo)
    )

    private fun cardio(
        daysAgo: Long,
        duration: Double? = null,
        distance: Double? = null,
        intensity: Double? = null,
        unit: IntensityUnit? = null,
        incline: Double? = null
    ) = CardioLog(
        exerciseName = "Laufband",
        dayId = 1,
        performedAt = millis(daysAgo),
        durationMin = duration,
        distanceKm = distance,
        intensity = intensity,
        intensityUnit = unit ?: IntensityUnit.KMH.takeIf { intensity != null },
        inclinePercent = incline
    )

    private fun millis(daysAgo: Long) =
        HEUTE.minusDays(daysAgo).atTime(18, 0).toInstant(ZONE).toEpochMilli()
}
