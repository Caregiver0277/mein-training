package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.IntensityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UNITS = CardioUnits(
    minutes = "min",
    kilometers = "km",
    meters = "m",
    kmh = "km/h",
    level = "Stufe",
    percent = "%"
)

class CardioTest {

    // --- Dauer -------------------------------------------------------------

    @Test
    fun ganzeMinutenOhneSekunden() {
        assertEquals("20 min", formatDuration(20.0, UNITS))
        assertEquals("20", formatDurationValue(20.0))
    }

    @Test
    fun bruchteileAlsMinutenUndSekunden() {
        assertEquals("7:30 min", formatDuration(7.5, UNITS))
        assertEquals("0:45", formatDurationValue(0.75))
        assertEquals("7:05", formatDurationValue(7 + 5 / 60.0))
    }

    @Test
    fun aufDieSekundeGerundet() {
        // 7:20 ist als Kommazahl 7,3333…; zurück muss genau „7:20“ herauskommen.
        assertEquals("7:20", formatDurationValue(parseCardioDuration("7:20")!!))
        // 59,7 Sekunden runden auf die nächste volle Minute, nicht auf „7:60“.
        assertEquals("8", formatDurationValue(7 + 59.7 / 60))
    }

    @Test
    fun dauerAlsMinutenMitKommaOderPunkt() {
        assertEquals(20.0, parseCardioDuration("20")!!, 0.0)
        assertEquals(7.5, parseCardioDuration("7,5")!!, 0.0)
        assertEquals(7.5, parseCardioDuration(" 7.5 ")!!, 0.0)
    }

    @Test
    fun dauerAlsMinutenUndSekunden() {
        assertEquals(7.5, parseCardioDuration("7:30")!!, 0.0)
        assertEquals(0.75, parseCardioDuration("0:45")!!, 0.0)
        assertEquals(75.5, parseCardioDuration("75:30")!!, 0.0)
    }

    @Test
    fun ungueltigeDauerWirdNichtGelesen() {
        listOf("", "  ", "abc", "7:5", "7:75", "7:300", ":30", "7:", "1:02:30", "-5", "0", "0:00", "7,5:30")
            .forEach { assertNull(it, parseCardioDuration(it)) }
        assertTrue(isValidCardioDurationInput(""))
        assertTrue(isValidCardioDurationInput("7:30"))
        assertFalse(isValidCardioDurationInput("7:75"))
    }

    @Test
    fun jedeDauerUeberstehtDenWegDurchsFeld() {
        listOf(1.0, 7.5, 20.0, 0.75, 45.25).forEach { minutes ->
            assertEquals(minutes, parseCardioDuration(formatDurationValue(minutes))!!, 1e-9)
        }
    }

    // --- Distanz, Tempo, Steigung -----------------------------------------

    @Test
    fun distanzAbEinemKilometerInKm() {
        assertEquals("1 km", formatDistance(1.0, UNITS))
        assertEquals("3,4 km", formatDistance(3.4, UNITS))
        assertEquals("5,25 km", formatDistance(5.25, UNITS))
    }

    @Test
    fun distanzUnterEinemKilometerInMetern() {
        assertEquals("800 m", formatDistance(0.8, UNITS))
        assertEquals("250 m", formatDistance(0.25, UNITS))
        assertEquals("0 m", formatDistance(0.0, UNITS))
        // Erst runden, dann entscheiden: knapp unter 1 km ist auf den Meter genau 1 km.
        assertEquals("1 km", formatDistance(0.9996, UNITS))
    }

    @Test
    fun tempoUndStufe() {
        assertEquals("6,5 km/h", formatIntensity(6.5, IntensityUnit.KMH, UNITS))
        assertEquals("Stufe 8", formatIntensity(8.0, IntensityUnit.LEVEL, UNITS))
        assertEquals("8 %", formatIncline(8.0, UNITS))
        assertEquals("2,5 %", formatIncline(2.5, UNITS))
    }

    @Test
    fun einzelnerWertInSeinerSchreibweise() {
        assertEquals("7:30 min", formatCardioValue(CardioValue.DURATION, 7.5, null, UNITS))
        assertEquals("500 m", formatCardioValue(CardioValue.DISTANCE, 0.5, null, UNITS))
        assertEquals("Stufe 1", formatCardioValue(CardioValue.INTENSITY, 1.0, IntensityUnit.LEVEL, UNITS))
        assertEquals("0,1 km/h", formatCardioValue(CardioValue.INTENSITY, 0.1, null, UNITS))
        assertEquals("0,5 %", formatCardioValue(CardioValue.INCLINE, 0.5, null, UNITS))
    }

    // --- Eine ganze Einheit ------------------------------------------------

    @Test
    fun alleWerteInEinerZeile() {
        val values = CardioValues(
            durationMin = 22.0,
            distanceKm = 3.4,
            intensity = 6.0,
            intensityUnit = IntensityUnit.KMH,
            inclinePercent = 8.0
        )
        assertEquals("22 min · 3,4 km · 6 km/h · 8 %", formatCardioValues(values, UNITS))
    }

    @Test
    fun nurDieGesetztenWerte() {
        val values = CardioValues(durationMin = 20.0, intensity = 6.0, intensityUnit = IntensityUnit.KMH, inclinePercent = 8.0)
        assertEquals("20 min · 6 km/h · 8 %", formatCardioValues(values, UNITS))
        assertNull(formatCardioValues(CardioValues(), UNITS))
    }

    @Test
    fun beiEinerStreckeStehtDieDistanzVorn() {
        val values = CardioValues(durationMin = 30.0, distanceKm = 5.0)
        assertEquals("5 km · 30 min", formatCardioValues(values, UNITS, distanceFirst = true))
        assertEquals("30 min · 5 km", formatCardioValues(values, UNITS))
    }

    @Test
    fun dieEinheitDesTempoZaehltNurMitTempo() {
        val ziele = CardioTargets(durationMin = 20.0, intensityUnit = IntensityUnit.LEVEL)
        assertNull(ziele.values.intensityUnit)
        assertEquals("20 min", formatCardioValues(ziele.values, UNITS))
        val mitStufe = ziele.copy(intensity = 8.0)
        assertEquals("20 min · Stufe 8", formatCardioValues(mitStufe.values, UNITS))
    }

    // --- Der Pfeil ---------------------------------------------------------

    @Test
    fun schnellauswahlPassendZumWert() {
        assertEquals(listOf(1.0, 2.0, 5.0), cardioStepSuggestions(CardioValue.DURATION, IntensityUnit.KMH))
        assertEquals(listOf(0.25, 0.5, 1.0), cardioStepSuggestions(CardioValue.DISTANCE, IntensityUnit.KMH))
        assertEquals(listOf(0.1, 0.5, 1.0), cardioStepSuggestions(CardioValue.INTENSITY, IntensityUnit.KMH))
        assertEquals(listOf(1.0), cardioStepSuggestions(CardioValue.INTENSITY, IntensityUnit.LEVEL))
        assertEquals(listOf(0.5, 1.0, 2.0), cardioStepSuggestions(CardioValue.INCLINE, IntensityUnit.KMH))
    }

    @Test
    fun dieVorgabeStehtInDerSchnellauswahl() {
        CardioValue.entries.forEach { value ->
            IntensityUnit.entries.forEach { unit ->
                assertTrue(defaultCardioStep(value, unit) in cardioStepSuggestions(value, unit))
            }
        }
    }

    @Test
    fun schrittEinlesen() {
        assertEquals(0.5, parseCardioStep("0,5", CardioValue.DISTANCE, IntensityUnit.KMH), 0.0)
        assertEquals(0.5, parseCardioStep("0:30", CardioValue.DURATION, IntensityUnit.KMH), 0.0)
        assertEquals(2.0, parseCardioStep("2", CardioValue.DURATION, IntensityUnit.KMH), 0.0)
        // Leer, ungültig oder null: die Vorgabe des Werts.
        assertEquals(1.0, parseCardioStep("", CardioValue.DURATION, IntensityUnit.KMH), 0.0)
        assertEquals(1.0, parseCardioStep("0", CardioValue.INTENSITY, IntensityUnit.LEVEL), 0.0)
        assertEquals(0.5, parseCardioStep("x", CardioValue.INCLINE, IntensityUnit.KMH), 0.0)
        assertEquals("0:30", formatCardioStepInput(0.5, CardioValue.DURATION))
        assertEquals("0,25", formatCardioStepInput(0.25, CardioValue.DISTANCE))
    }

    @Test
    fun pfeilRechnetExaktUndHaeltBeiNull() {
        assertEquals(6.1, stepCardioTarget(6.0, 0.1, down = false), 0.0)
        assertEquals(29.0, stepCardioTarget(30.0, 1.0, down = true), 0.0)
        assertEquals(31.0, stepCardioTarget(30.0, 1.0, down = true, reverse = true), 0.0)
        assertEquals(0.0, stepCardioTarget(0.5, 1.0, down = true), 0.0)
    }

    @Test
    fun zieleKennenIhrenWert() {
        val ziele = CardioTargets(durationMin = 20.0, inclinePercent = 8.0)
        assertEquals(20.0, ziele.valueOf(CardioValue.DURATION)!!, 0.0)
        assertNull(ziele.valueOf(CardioValue.DISTANCE))
        assertEquals(9.0, ziele.with(CardioValue.INCLINE, 9.0).inclinePercent!!, 0.0)
    }
}
