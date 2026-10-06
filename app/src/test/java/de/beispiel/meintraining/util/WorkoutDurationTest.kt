package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINUTE = 60_000L

/** 18:00 an einem beliebigen Tag – die Rechnungen hier kennen kein Datum. */
private const val T0 = 1_760_000_000_000L

class WorkoutDurationTest {

    // --- Plausible Dauer ----------------------------------------------------

    @Test
    fun eineDauerZwischenZehnMinutenUndVierStundenZaehlt() {
        assertEquals(T0, plausibleStart(T0, T0 + 64 * MINUTE))
        assertEquals(T0, plausibleStart(T0, T0 + MIN_WORKOUT_MILLIS))
        assertEquals(T0, plausibleStart(T0, T0 + MAX_WORKOUT_MILLIS))
    }

    /** Eine aus Versehen gestartete Pausenuhr ist kein Training. */
    @Test
    fun unterZehnMinutenBleibtDerBeginnLeer() {
        assertNull(plausibleStart(T0, T0 + 9 * MINUTE))
    }

    /** Wer den Haken erst am Abend nachholt, hat nicht sieben Stunden trainiert. */
    @Test
    fun ueberVierStundenBleibtDerBeginnLeer() {
        assertNull(plausibleStart(T0, T0 + MAX_WORKOUT_MILLIS + 1))
    }

    @Test
    fun ohneMerkerGibtEsKeinenBeginn() {
        assertNull(plausibleStart(null, T0))
    }

    @Test
    fun dieDauerWirdAufMinutenGerundet() {
        assertEquals(64, durationMinutes(T0, T0 + 64 * MINUTE + 20_000))
        assertEquals(65, durationMinutes(T0, T0 + 64 * MINUTE + 30_000))
        assertNull(durationMinutes(null, T0))
        assertNull(durationMinutes(T0 + 1, T0))
    }

    // --- Merker ------------------------------------------------------------

    @Test
    fun dieErsteAktivitaetBeginntDasTraining() {
        val marker = null.withActivity(dayId = 2, at = T0, activeUntil = T0 + 2 * MINUTE)
        assertEquals(WorkoutMarker(2, T0, T0, T0 + 2 * MINUTE), marker)
    }

    /** Das Training läuft weiter: Beginn bleibt, die jüngste Aktivität und ihr Tag zählen. */
    @Test
    fun weitereAktivitaetVerschiebtNurDasEnde() {
        val marker = null.withActivity(dayId = 2, at = T0, activeUntil = T0 + 2 * MINUTE)
            .withActivity(dayId = 3, at = T0 + 20 * MINUTE)
        assertEquals(WorkoutMarker(3, T0, T0 + 20 * MINUTE, T0 + 20 * MINUTE), marker)
    }

    /** Eine Pausenuhr, die noch läuft, hält das Training offen – auch über die Ruhezeit hinaus. */
    @Test
    fun eineLaufendePausenuhrZaehltBisZuIhremEnde() {
        val marker = null.withActivity(dayId = 1, at = T0, activeUntil = T0 + 10 * MINUTE)
        val danach = marker.withActivity(dayId = 1, at = T0 + 10 * MINUTE + WORKOUT_IDLE_MILLIS - 1)
        assertEquals(T0, danach.startedAt)
    }

    @Test
    fun nachDerRuhezeitBeginntEinNeuesTraining() {
        val marker = null.withActivity(dayId = 1, at = T0)
        val neu = marker.withActivity(dayId = 1, at = T0 + WORKOUT_IDLE_MILLIS)
        assertEquals(T0 + WORKOUT_IDLE_MILLIS, neu.startedAt)
    }

    @Test
    fun einZurueckgenommenesAbhakenBringtDenMerkerZurueck() {
        val verbraucht = WorkoutMarker(1, T0, T0 + 50 * MINUTE)
        assertEquals(verbraucht, restoredMarker(verbraucht, current = null))
    }

    /** Was seit dem Fehltipp geschah, gehört zum selben Training – mit dem früheren Beginn. */
    @Test
    fun beimZurueckholenZaehltDerFruehereBeginn() {
        val verbraucht = WorkoutMarker(1, T0, T0 + 50 * MINUTE)
        val seither = WorkoutMarker(2, T0 + 52 * MINUTE, T0 + 55 * MINUTE, T0 + 57 * MINUTE)
        assertEquals(
            WorkoutMarker(2, T0, T0 + 55 * MINUTE, T0 + 57 * MINUTE),
            restoredMarker(verbraucht, seither)
        )
    }

    // --- Statistik ---------------------------------------------------------

    @Test
    fun durchschnittGesamtUndJeTag() {
        val summary = durationSummary(
            listOf(
                SessionTimes(dayId = 2, startedAt = T0, completedAt = T0 + 70 * MINUTE),
                SessionTimes(dayId = 1, startedAt = T0, completedAt = T0 + 50 * MINUTE),
                SessionTimes(dayId = 1, startedAt = T0, completedAt = T0 + 60 * MINUTE),
                // Ohne Dauer zählt ein Training nicht mit, statt den Schnitt zu drücken.
                SessionTimes(dayId = 3, startedAt = null, completedAt = T0)
            )
        )!!
        assertEquals(60, summary.averageMinutes)
        assertEquals(listOf(1 to 55, 2 to 70), summary.perDay)
    }

    @Test
    fun ohneBekannteDauerGibtEsKeineStatistik() {
        assertNull(durationSummary(listOf(SessionTimes(1, null, T0))))
        assertNull(durationSummary(emptyList()))
    }
}
