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

    // --- Oberes Ende erreicht ------------------------------------------------

    /** Drei Sätze bei 60 kg mit den Wiederholungen [reps], an [date] auf Tag [dayId]. */
    private fun einheit(date: LocalDate, vararg reps: Int, dayId: Int = 1, weightKg: Double? = 60.0) =
        reps.mapIndexed { index, r -> satz(date, dayId, number = index + 1, reps = r, weightKg = weightKg) }

    private fun erreicht(
        units: List<SetUnit>,
        planned: Int? = 3,
        repsMax: Int? = 12,
        weight: Double? = 60.0,
        deload: Boolean = false
    ) = isTopOfRangeReached(units, dayId = 1, planned, repsMax, weight, deload)

    @Test
    fun alleSaetzeAmOberenEndeBeimAktuellenGewicht() {
        val units = setUnits(einheit(HEUTE.minusDays(4), 12, 12, 13), ZONE)
        assertTrue(erreicht(units))
    }

    @Test
    fun einSatzDarunterReichtNicht() {
        assertFalse(erreicht(setUnits(einheit(HEUTE.minusDays(4), 12, 12, 11), ZONE)))
    }

    /** Wurde das Gewicht seither geändert, stammen die Sätze von einem anderen – kein Hinweis. */
    @Test
    fun nachEinerGewichtsaenderungIstDerHinweisWeg() {
        val units = setUnits(einheit(HEUTE.minusDays(4), 12, 12, 12), ZONE)
        assertFalse(erreicht(units, weight = 62.5))
    }

    /** Gewertet werden nur vollständige Einheiten: Die unvollständige von heute zählt nicht. */
    @Test
    fun eineUnvollstaendigeEinheitZaehltNicht() {
        val units = setUnits(
            einheit(HEUTE.minusDays(4), 12, 12, 12) + einheit(HEUTE, 9),
            ZONE
        )
        assertTrue(erreicht(units))
        assertFalse(erreicht(setUnits(einheit(HEUTE, 12, 12), ZONE)))
    }

    /** Eine volle Einheit von heute zählt sofort – auch wenn sie das obere Ende verfehlt. */
    @Test
    fun dieJuengsteVollstaendigeEinheitEntscheidet() {
        val units = setUnits(
            einheit(HEUTE.minusDays(4), 12, 12, 12) + einheit(HEUTE, 12, 11, 10),
            ZONE
        )
        assertFalse(erreicht(units))
    }

    /** Zusatzsätze ändern nichts: Satz 4 mit 6 Wiederholungen nimmt den Hinweis nicht weg. */
    @Test
    fun zusatzsaetzeZaehlenNicht() {
        val units = setUnits(
            einheit(HEUTE.minusDays(2), 12, 12, 12) +
                satz(HEUTE.minusDays(2), dayId = 1, number = 4, reps = 6),
            ZONE
        )
        assertTrue(erreicht(units))
    }

    /** Nur dieser Trainingstag: Dieselbe Übung an Tag 3 mit „3 x 4-6“ zählt hier nicht. */
    @Test
    fun einheitenAndererTageZaehlenNicht() {
        val units = setUnits(einheit(HEUTE.minusDays(1), 12, 12, 12, dayId = 3), ZONE)
        assertFalse(erreicht(units))
    }

    @Test
    fun keinHinweisInDerDeloadWocheOhneObereGrenzeOhneSaetzeOderGewicht() {
        val units = setUnits(einheit(HEUTE.minusDays(4), 12, 12, 12), ZONE)
        assertFalse(erreicht(units, deload = true))
        assertFalse(erreicht(units, repsMax = null))
        assertFalse(erreicht(units, planned = null))
        assertFalse(erreicht(units, weight = null))
        val ohneGewicht = setUnits(einheit(HEUTE.minusDays(4), 12, 12, 12, weightKg = null), ZONE)
        assertFalse(erreicht(ohneGewicht))
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

    // --- Verlauf -------------------------------------------------------------

    /** Nur dieser Tag an diesem Datum, je Übung samt Variation, in Trainingsreihenfolge. */
    @Test
    fun einVerlaufseintragZeigtDieSaetzeDiesesTagesJeUebung() {
        val trizepsSeil = satz(HEUTE, dayId = 2, number = 1, reps = 15, hour = 19)
            .copy(exerciseName = "Trizeps", variation = "Seil")
        val trizepsStange = satz(HEUTE, dayId = 2, number = 1, reps = 9, hour = 19)
            .copy(exerciseName = "Trizeps", variation = "Stange", performedAt = trizepsSeil.performedAt + 1)
        val logs = listOf(
            satz(HEUTE.minusDays(1), dayId = 2, number = 1, reps = 7),
            satz(HEUTE, dayId = 2, number = 2, reps = 11, hour = 18),
            satz(HEUTE, dayId = 2, number = 1, reps = 12, hour = 18),
            satz(HEUTE, dayId = 1, number = 1, reps = 5, hour = 18),
            trizepsSeil,
            trizepsStange
        ).sortedBy { it.performedAt }

        val session = setsOfSession(logs, dayId = 2, date = HEUTE, zone = ZONE)

        assertEquals(listOf("Bankdrücken", "Trizeps", "Trizeps"), session.map { it.name })
        assertEquals(listOf(null, "Seil", "Stange"), session.map { it.variation })
        assertEquals(listOf(12, 11), session.first().sets.map { it.reps })
        assertTrue(setsOfSession(logs, dayId = 3, date = HEUTE, zone = ZONE).isEmpty())
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

    // --- Festgefahren: steigen die Wiederholungen noch? ---------------------

    /** Eine Einheit mit drei Sätzen an Tag [dayId], [daysAgo] Tage vor heute. */
    private fun einheit(daysAgo: Long, vararg reps: Int, dayId: Int = 1, weightKg: Double = 60.0) =
        reps.mapIndexed { index, r ->
            satz(HEUTE.minusDays(daysAgo), dayId, number = index + 1, reps = r, weightKg = weightKg)
        }

    private fun steigend(vararg units: List<SetLog>, since: Long = Long.MIN_VALUE) =
        repsStillRising(units.flatMap { it }.sortedBy { it.performedAt }, 60.0, since, ZONE)

    @Test
    fun ohneVergleichSteigtNichts() {
        assertFalse(steigend())
        assertFalse(steigend(einheit(3, 10, 10, 10)))
    }

    @Test
    fun mehrWiederholungenAlsZuvorSindSteigend() {
        assertTrue(steigend(einheit(7, 10, 10, 9), einheit(3, 10, 10, 10)))
    }

    @Test
    fun gleichbleibendeWiederholungenSindKeinSteigen() {
        assertFalse(steigend(einheit(14, 10, 10, 10), einheit(7, 10, 10, 10), einheit(3, 10, 10, 10)))
    }

    @Test
    fun einSchwacherTagNachDemRekordZaehltNochMit() {
        // 30, 32, 31: Der Rekord liegt in den letzten beiden Einheiten.
        assertTrue(steigend(einheit(14, 10, 10, 10), einheit(7, 11, 11, 10), einheit(3, 11, 10, 10)))
    }

    @Test
    fun einAlterRekordIstKeinSteigen() {
        // 30, 33, 31, 31: Seit dem Rekord kam nichts mehr.
        assertFalse(
            steigend(
                einheit(21, 10, 10, 10),
                einheit(14, 11, 11, 11),
                einheit(7, 11, 10, 10),
                einheit(3, 11, 10, 10)
            )
        )
    }

    @Test
    fun nurDasAktuelleGewichtUndDieZeitSeitDerAenderungZaehlen() {
        // Bei 55 kg waren es weniger – das ist kein Steigen bei 60 kg.
        assertFalse(steigend(einheit(7, 8, 8, 8, weightKg = 55.0), einheit(3, 10, 10, 10)))
        // Vor der letzten Änderung (vor 5 Tagen) Gelaufenes zählt nicht.
        val since = HEUTE.minusDays(5).atStartOfDay(ZONE).toInstant().toEpochMilli()
        assertFalse(steigend(einheit(7, 8, 8, 8), einheit(3, 10, 10, 10), since = since))
    }

    @Test
    fun verglichenWirdJeTrainingstag() {
        // Tag 2 hat ein anderes Schema; nur dort steigt es.
        assertTrue(
            steigend(
                einheit(9, 6, 6, 6, dayId = 2),
                einheit(8, 10, 10, 10),
                einheit(2, 7, 6, 6, dayId = 2),
                einheit(1, 10, 10, 10)
            )
        )
        // Zwischen den Tagen gemischt sähe es nach Steigen aus, ist es aber nicht.
        assertFalse(steigend(einheit(8, 6, 6, 6, dayId = 2), einheit(1, 10, 10, 10)))
    }
}
