package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.IntensityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

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
        assertEquals("20\u00A0min", formatDuration(20.0, UNITS))
        assertEquals("20", formatDurationValue(20.0))
    }

    @Test
    fun bruchteileAlsMinutenUndSekunden() {
        assertEquals("7:30\u00A0min", formatDuration(7.5, UNITS))
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
        assertEquals("1\u00A0km", formatDistance(1.0, UNITS))
        assertEquals("3,4\u00A0km", formatDistance(3.4, UNITS))
        assertEquals("5,25\u00A0km", formatDistance(5.25, UNITS))
    }

    @Test
    fun distanzUnterEinemKilometerInMetern() {
        assertEquals("800\u00A0m", formatDistance(0.8, UNITS))
        assertEquals("250\u00A0m", formatDistance(0.25, UNITS))
        assertEquals("0\u00A0m", formatDistance(0.0, UNITS))
        // Erst runden, dann entscheiden: knapp unter 1 km ist auf den Meter genau 1 km.
        assertEquals("1\u00A0km", formatDistance(0.9996, UNITS))
    }

    @Test
    fun tempoUndStufe() {
        assertEquals("6,5\u00A0km/h", formatIntensity(6.5, IntensityUnit.KMH, UNITS))
        assertEquals("Stufe\u00A08", formatIntensity(8.0, IntensityUnit.LEVEL, UNITS))
        assertEquals("8\u00A0%", formatIncline(8.0, UNITS))
        assertEquals("2,5\u00A0%", formatIncline(2.5, UNITS))
    }

    @Test
    fun einzelnerWertInSeinerSchreibweise() {
        assertEquals("7:30\u00A0min", formatCardioValue(CardioValue.DURATION, 7.5, null, UNITS))
        assertEquals("500\u00A0m", formatCardioValue(CardioValue.DISTANCE, 0.5, null, UNITS))
        assertEquals("Stufe\u00A01", formatCardioValue(CardioValue.INTENSITY, 1.0, IntensityUnit.LEVEL, UNITS))
        assertEquals("0,1\u00A0km/h", formatCardioValue(CardioValue.INTENSITY, 0.1, null, UNITS))
        assertEquals("0,5\u00A0%", formatCardioValue(CardioValue.INCLINE, 0.5, null, UNITS))
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
        assertEquals("22\u00A0min · 3,4\u00A0km · 6\u00A0km/h · 8\u00A0%", formatCardioValues(values, UNITS))
    }

    @Test
    fun nurDieGesetztenWerte() {
        val values = CardioValues(durationMin = 20.0, intensity = 6.0, intensityUnit = IntensityUnit.KMH, inclinePercent = 8.0)
        assertEquals("20\u00A0min · 6\u00A0km/h · 8\u00A0%", formatCardioValues(values, UNITS))
        assertNull(formatCardioValues(CardioValues(), UNITS))
    }

    @Test
    fun beiEinerStreckeStehtDieDistanzVorn() {
        val values = CardioValues(durationMin = 30.0, distanceKm = 5.0)
        assertEquals("5\u00A0km · 30\u00A0min", formatCardioValues(values, UNITS, distanceFirst = true))
        assertEquals("30\u00A0min · 5\u00A0km", formatCardioValues(values, UNITS))
    }

    @Test
    fun dieEinheitDesTempoZaehltNurMitTempo() {
        val ziele = CardioTargets(durationMin = 20.0, intensityUnit = IntensityUnit.LEVEL)
        assertNull(ziele.values.intensityUnit)
        assertEquals("20\u00A0min", formatCardioValues(ziele.values, UNITS))
        val mitStufe = ziele.copy(intensity = 8.0)
        assertEquals("20\u00A0min · Stufe\u00A08", formatCardioValues(mitStufe.values, UNITS))
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

    // --- Letztes Mal --------------------------------------------------------

    @Test
    fun dieJuengsteEinheitMitAbstandInTagen() {
        val zone = ZoneOffset.UTC
        fun am(tag: Int, stunde: Int) =
            LocalDate.of(2026, 10, tag).atTime(stunde, 0).toInstant(zone).toEpochMilli()
        val logs = listOf(
            CardioLog(exerciseName = "Laufband", dayId = 1, performedAt = am(1, 18), durationMin = 20.0),
            CardioLog(exerciseName = "Laufband", dayId = 1, performedAt = am(4, 7), durationMin = 22.0, distanceKm = 3.4)
        )
        val letztes = lastCardioEntry(logs, today = LocalDate.of(2026, 10, 7), zone = zone)!!
        assertEquals(3, letztes.daysAgo)
        assertEquals(LocalDate.of(2026, 10, 4), letztes.date)
        assertEquals("22\u00A0min · 3,4\u00A0km", formatCardioValues(letztes.values, UNITS))
        // Eine zurückgestellte Uhr: nie „vor -1 Tagen“.
        assertEquals(0, lastCardioEntry(logs, today = LocalDate.of(2026, 10, 3), zone = zone)!!.daysAgo)
        assertNull(lastCardioEntry(emptyList(), today = LocalDate.of(2026, 10, 7), zone = zone))
    }

    // --- Heute eingetragen --------------------------------------------------------

    @Test
    fun dieEinheitVonHeuteGehoertZuDiesemTrainingstag() {
        val zone = ZoneOffset.UTC
        fun am(tag: Int, stunde: Int) =
            LocalDate.of(2026, 10, tag).atTime(stunde, 0).toInstant(zone).toEpochMilli()
        val logs = listOf(
            CardioLog(id = 1, exerciseName = "Rad", dayId = 1, performedAt = am(6, 18), durationMin = 20.0),
            CardioLog(id = 2, exerciseName = "Rad", dayId = 3, performedAt = am(7, 7), durationMin = 15.0),
            CardioLog(id = 3, exerciseName = "Rad", dayId = 1, performedAt = am(7, 18), durationMin = 25.0)
        )
        val heute = LocalDate.of(2026, 10, 7)
        assertEquals(3L, todaysCardioLog(logs, dayId = 1, today = heute, zone = zone)!!.id)
        assertEquals(2L, todaysCardioLog(logs, dayId = 3, today = heute, zone = zone)!!.id)
        assertNull(todaysCardioLog(logs, dayId = 2, today = heute, zone = zone))
        // Gestern an Tag 1 ist nicht heute.
        assertNull(todaysCardioLog(logs.take(1), dayId = 1, today = heute, zone = zone))
    }

    @Test
    fun letztesMalUeberspringtDieGeradeBearbeiteteEinheit() {
        val zone = ZoneOffset.UTC
        fun am(tag: Int, stunde: Int) =
            LocalDate.of(2026, 10, tag).atTime(stunde, 0).toInstant(zone).toEpochMilli()
        val gestern = CardioLog(id = 1, exerciseName = "Rad", dayId = 1, performedAt = am(6, 18), durationMin = 20.0)
        val andererTag = CardioLog(id = 2, exerciseName = "Rad", dayId = 3, performedAt = am(7, 7), durationMin = 15.0)
        val heute = CardioLog(id = 3, exerciseName = "Rad", dayId = 1, performedAt = am(7, 18), durationMin = 25.0)
        val datum = LocalDate.of(2026, 10, 7)

        // Beim Korrigieren von heute: die Einheit vom Morgen an einem anderen Trainingstag.
        val letztes = lastCardioEntryBefore(listOf(gestern, andererTag, heute), heute, datum, zone)!!
        assertEquals(15.0, letztes.values.durationMin!!, 0.0)
        assertEquals(0, letztes.daysAgo)
        // Ohne Einheit von heute: einfach die jüngste.
        assertEquals(1, lastCardioEntryBefore(listOf(gestern), null, datum, zone)!!.daysAgo)
        assertNull(lastCardioEntryBefore(listOf(heute), heute, datum, zone))
    }

    @Test
    fun imVerlaufDieEinheitenDiesesTrainings() {
        val zone = ZoneOffset.UTC
        fun am(tag: Int, stunde: Int) =
            LocalDate.of(2026, 10, tag).atTime(stunde, 0).toInstant(zone).toEpochMilli()
        val logs = listOf(
            CardioLog(id = 1, exerciseName = "Rad", dayId = 1, performedAt = am(6, 18), durationMin = 20.0),
            CardioLog(id = 2, exerciseName = "Laufband", dayId = 2, performedAt = am(7, 7), durationMin = 15.0),
            CardioLog(id = 3, exerciseName = "Rad", dayId = 2, performedAt = am(7, 8), durationMin = 25.0)
        )
        assertEquals(listOf(2L, 3L), cardioOfSession(logs, dayId = 2, date = LocalDate.of(2026, 10, 7), zone = zone).map { it.id })
        assertTrue(cardioOfSession(logs, dayId = 1, date = LocalDate.of(2026, 10, 7), zone = zone).isEmpty())
    }

    @Test
    fun nachUebungUndVariationZerlegt() {
        val logs = listOf(
            CardioLog(exerciseName = "Rad", variation = "locker", dayId = 1, performedAt = 1, durationMin = 30.0),
            CardioLog(exerciseName = "Rad", dayId = 1, performedAt = 2, durationMin = 20.0),
            CardioLog(exerciseName = "Rad", variation = "locker", dayId = 2, performedAt = 3, durationMin = 31.0)
        )
        val zerlegt = cardioLogsByExercise(logs)
        assertEquals(2, zerlegt.getValue(SetLogKey("Rad", "locker")).size)
        assertEquals(1, zerlegt.getValue(SetLogKey("Rad", null)).size)
    }

    @Test
    fun pfeilNurMitGewaehltemUndGesetztemWert() {
        assertFalse(CardioTargets(durationMin = 20.0).hasArrow)
        assertFalse(CardioTargets(durationMin = 20.0, arrowValue = CardioValue.INCLINE).hasArrow)
        assertTrue(CardioTargets(durationMin = 20.0, arrowValue = CardioValue.DURATION).hasArrow)
    }

    @Test
    fun zieleKennenIhrenWert() {
        val ziele = CardioTargets(durationMin = 20.0, inclinePercent = 8.0)
        assertEquals(20.0, ziele.valueOf(CardioValue.DURATION)!!, 0.0)
        assertNull(ziele.valueOf(CardioValue.DISTANCE))
        assertEquals(9.0, ziele.with(CardioValue.INCLINE, 9.0).inclinePercent!!, 0.0)
    }
}
