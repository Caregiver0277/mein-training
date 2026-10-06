package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC
private val HEUTE = LocalDate.of(2026, 10, 7)

/** Ein Satz an [date] um [hour] Uhr – Name und Variation sind hier egal, es geht um eine Übung. */
private fun satz(
    date: LocalDate,
    dayId: Int,
    number: Int,
    reps: Int,
    weightKg: Double? = 60.0,
    hour: Int = 18,
    id: Long = 0
) = SetLog(
    id = id,
    exerciseName = "Bankdrücken",
    dayId = dayId,
    performedAt = date.atTime(hour, number).toInstant(ZONE).toEpochMilli(),
    setNumber = number,
    reps = reps,
    weightKg = weightKg
)

private fun einheiten(vararg sets: SetLog) = setUnits(sets.sortedBy { it.performedAt }, ZONE)

private fun kg(weight: Double) = "${weight.toDecimalString()} kg"

class SetLoggingTest {

    // --- Einheiten ---------------------------------------------------------

    /** Ein Kalendertag an einem Trainingstag ist eine Einheit – zwei Tage am selben Abend zwei. */
    @Test
    fun saetzeWerdenNachKalendertagUndTrainingstagGefasst() {
        val units = einheiten(
            satz(HEUTE.minusDays(3), dayId = 1, number = 1, reps = 10),
            satz(HEUTE.minusDays(3), dayId = 1, number = 2, reps = 9),
            satz(HEUTE, dayId = 1, number = 1, reps = 11, hour = 17),
            satz(HEUTE, dayId = 3, number = 1, reps = 8, hour = 19)
        )
        assertEquals(3, units.size)
        assertEquals(listOf(2, 1, 1), units.map { it.sets.size })
        assertEquals(listOf(1, 1, 3), units.map { it.dayId })
    }

    /** Sätze stehen nach Nummer, auch wenn einer später nachgetragen wurde. */
    @Test
    fun saetzeEinerEinheitStehenNachNummer() {
        val unit = einheiten(
            satz(HEUTE, dayId = 1, number = 2, reps = 9, hour = 18),
            satz(HEUTE, dayId = 1, number = 1, reps = 10, hour = 19)
        ).single()
        assertEquals(listOf(1, 2), unit.sets.map { it.setNumber })
    }

    /** Vollständig ist eine Einheit mit allen geplanten Sätzen; Zusatzsätze ersetzen keinen. */
    @Test
    fun vollstaendigHeisstAlleGeplantenSaetze() {
        val unit = einheiten(
            satz(HEUTE, dayId = 1, number = 1, reps = 10),
            satz(HEUTE, dayId = 1, number = 3, reps = 10),
            satz(HEUTE, dayId = 1, number = 4, reps = 10)
        ).single()
        assertEquals(2, unit.plannedLogged(3))
        assertFalse(unit.isComplete(3))
        assertTrue(unit.isComplete(1))
        assertFalse(unit.isComplete(0))
    }

    // --- Heute und letztes Mal -----------------------------------------------

    @Test
    fun heuteIstDieEinheitVonHeuteAnDiesemTag() {
        val units = einheiten(
            satz(HEUTE.minusDays(2), dayId = 1, number = 1, reps = 10),
            satz(HEUTE, dayId = 3, number = 1, reps = 8)
        )
        assertNull(todaysUnit(units, dayId = 1, today = HEUTE))
        assertEquals(3, todaysUnit(units, dayId = 3, today = HEUTE)!!.dayId)
    }

    /** „Letztes Mal“ kommt von diesem Trainingstag, auch wenn ein anderer jünger ist. */
    @Test
    fun letztesMalKommtVonDiesemTrainingstag() {
        val units = einheiten(
            satz(HEUTE.minusDays(7), dayId = 1, number = 1, reps = 12),
            satz(HEUTE.minusDays(3), dayId = 3, number = 1, reps = 6),
            satz(HEUTE, dayId = 1, number = 1, reps = 11)
        )
        val last = lastUnit(units, dayId = 1, today = HEUTE)!!
        assertEquals(HEUTE.minusDays(7), last.date)
    }

    /** Ohne eigene Einheit greift „Letztes Mal“ auf die jüngste eines anderen Tages zurück. */
    @Test
    fun ohneEigeneEinheitKommtLetztesMalVomLetztenAnderenTag() {
        val units = einheiten(
            satz(HEUTE.minusDays(9), dayId = 2, number = 1, reps = 5),
            satz(HEUTE.minusDays(4), dayId = 3, number = 1, reps = 6),
            satz(HEUTE, dayId = 1, number = 1, reps = 11)
        )
        assertEquals(3, lastUnit(units, dayId = 1, today = HEUTE)!!.dayId)
        assertNull(lastUnit(einheiten(satz(HEUTE, dayId = 1, number = 1, reps = 9)), 1, HEUTE))
    }

    // --- Vorbelegung ---------------------------------------------------------

    @Test
    fun wiederholungenKommenVomSelbenSatzDesLetztenMals() {
        val last = einheiten(
            satz(HEUTE.minusDays(4), dayId = 1, number = 1, reps = 12),
            satz(HEUTE.minusDays(4), dayId = 1, number = 2, reps = 11)
        ).single()
        assertEquals(11, suggestedReps(last, setNumber = 2, repsMin = 8, repsMax = 12))
        // Satz 3 gab es letztes Mal nicht: das untere Ende der Spanne.
        assertEquals(8, suggestedReps(last, setNumber = 3, repsMin = 8, repsMax = 12))
        assertEquals(12, suggestedReps(null, setNumber = 1, repsMin = null, repsMax = 12))
        assertEquals(DEFAULT_LOGGED_REPS, suggestedReps(null, setNumber = 1, repsMin = null, repsMax = null))
    }

    // --- Schreibweise --------------------------------------------------------

    @Test
    fun eineSatzfolgeStehtInEinerZeile() {
        val sets = listOf(
            satz(HEUTE, 1, number = 1, reps = 12),
            satz(HEUTE, 1, number = 2, reps = 11),
            satz(HEUTE, 1, number = 3, reps = 10)
        )
        assertEquals("60 kg × 12 / 11 / 10", formatSetSeries(sets, ::kg))
    }

    @Test
    fun einGewichtswechselBeginntEineNeueGruppe() {
        val sets = listOf(
            satz(HEUTE, 1, number = 1, reps = 12),
            satz(HEUTE, 1, number = 2, reps = 11),
            satz(HEUTE, 1, number = 3, reps = 8, weightKg = 62.5)
        )
        assertEquals("60 kg × 12 / 11 · 62,5 kg × 8", formatSetSeries(sets, ::kg))
    }

    @Test
    fun ohneGewichtBleibenNurDieWiederholungen() {
        val sets = listOf(
            satz(HEUTE, 1, number = 1, reps = 15, weightKg = null),
            satz(HEUTE, 1, number = 2, reps = 12, weightKg = null)
        )
        assertEquals("15 / 12", formatSetSeries(sets, ::kg))
    }
}
