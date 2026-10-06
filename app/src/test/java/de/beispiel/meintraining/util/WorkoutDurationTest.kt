package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MINUTE = 60_000L

/** Ein beliebiger Zeitpunkt – die Rechnungen hier kennen kein Datum. */
private const val T0 = 1_760_000_000_000L

private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

/** 18:00 am 1. Oktober 2026 in Berlin – für alles, was am Kalendertag hängt. */
private val ABEND = ZonedDateTime.of(2026, 10, 1, 18, 0, 0, 0, ZONE).toInstant().toEpochMilli()

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

    // --- Automatisches Ende -------------------------------------------------

    /** Ein Training von 18:00 bis 19:04, die letzte Aktivität eine Pausenuhr bis 19:06. */
    private val training = WorkoutMarker(
        dayId = 2,
        startedAt = ABEND,
        lastActivityAt = ABEND + 64 * MINUTE,
        activeUntil = ABEND + 66 * MINUTE
    )

    @Test
    fun ohneBeginnEndetNichts() {
        assertEquals(AutoEnd.None, autoEndDecision(null, null, ABEND, ZONE))
    }

    /** Eine Pausenuhr, die noch läuft, hält das Training offen; die Ruhezeit zählt ab ihrem Ende. */
    @Test
    fun eineLaufendePausenuhrHaeltDasTrainingOffen() {
        val laufend = training.copy(activeUntil = ABEND + 100 * MINUTE)
        assertEquals(
            AutoEnd.Wait(ABEND + 100 * MINUTE + WORKOUT_IDLE_MILLIS),
            autoEndDecision(laufend, null, ABEND + 99 * MINUTE, ZONE)
        )
    }

    @Test
    fun vorAblaufDerRuhezeitWirdGewartet() {
        val frist = ABEND + 66 * MINUTE + WORKOUT_IDLE_MILLIS
        assertEquals(AutoEnd.Wait(frist), autoEndDecision(training, null, frist - 1, ZONE))
    }

    /** Abgehakt wird zur letzten Aktivität, nicht zu dem Moment, in dem die halbe Stunde um war. */
    @Test
    fun dasEndeIstDieLetzteAktivitaet() {
        val spaeter = ABEND + 5 * 60 * MINUTE
        assertEquals(
            AutoEnd.End(dayId = 2, endAt = ABEND + 64 * MINUTE, startedAt = ABEND),
            autoEndDecision(training, null, spaeter, ZONE)
        )
    }

    /** Am selben Kalendertag schon abgehakt: Ein zweites Abhaken nähme das erste zurück. */
    @Test
    fun einSchonAbgehakterTagBleibtWieErIst() {
        val heuteMittag = ABEND - 6 * 60 * MINUTE
        assertEquals(AutoEnd.Discard, autoEndDecision(training, heuteMittag, ABEND + 3 * 60 * MINUTE, ZONE))
        // Ein Abhaken von gestern zählt nicht – das war ein anderes Training.
        val gestern = ABEND - 24 * 60 * MINUTE
        assertEquals(
            AutoEnd.End(2, ABEND + 64 * MINUTE, ABEND),
            autoEndDecision(training, gestern, ABEND + 3 * 60 * MINUTE, ZONE)
        )
    }

    /** Eine einzelne, versehentlich gestartete Pausenuhr hakt keinen Tag ab. */
    @Test
    fun unterZehnMinutenWirdNichtAbgehakt() {
        val versehen = WorkoutMarker(1, ABEND, ABEND + 2 * MINUTE, ABEND + 3 * MINUTE)
        assertEquals(AutoEnd.Discard, autoEndDecision(versehen, null, ABEND + 60 * MINUTE, ZONE))
    }

    /** Über vier Stunden wird abgehakt – trainiert wurde ja –, nur ohne Dauer. */
    @Test
    fun ueberVierStundenWirdOhneDauerAbgehakt() {
        val lang = WorkoutMarker(1, ABEND - 4 * 60 * MINUTE, ABEND + MINUTE)
        assertEquals(
            AutoEnd.End(dayId = 1, endAt = ABEND + MINUTE, startedAt = null),
            autoEndDecision(lang, null, ABEND + 2 * 60 * MINUTE, ZONE)
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
