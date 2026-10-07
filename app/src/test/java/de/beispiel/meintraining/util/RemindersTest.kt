package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
private val ABENDS: LocalTime = LocalTime.of(18, 0)

private fun berlin(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE)

class RemindersTest {

    // --- Uhrzeit ------------------------------------------------------------

    @Test
    fun vorDerUhrzeitIstHeuteDran() {
        assertEquals(berlin(2026, 10, 7, 18), nextReminderAt(berlin(2026, 10, 7, 9), ABENDS))
    }

    /** Läuft der Auftrag pünktlich um 18:00, ist der nächste Termin morgen – nicht gleich noch einmal. */
    @Test
    fun genauZurUhrzeitIstMorgenDran() {
        assertEquals(berlin(2026, 10, 8, 18), nextReminderAt(berlin(2026, 10, 7, 18), ABENDS))
        assertEquals(berlin(2026, 10, 8, 18), nextReminderAt(berlin(2026, 10, 7, 21), ABENDS))
    }

    /** Herbst: Der Tag hat 25 Stunden, 18:00 bleibt trotzdem 18:00. */
    @Test
    fun ueberDieUmstellungAufWinterzeitBleibtEsBeiDerUhrzeit() {
        val next = nextReminderAt(berlin(2026, 10, 24, 19), ABENDS)
        assertEquals(berlin(2026, 10, 25, 18), next)
        assertEquals(25L * 60 - 60, java.time.Duration.between(berlin(2026, 10, 24, 19), next).toMinutes())
    }

    /** Frühjahr: Der Tag hat 23 Stunden. */
    @Test
    fun ueberDieUmstellungAufSommerzeitBleibtEsBeiDerUhrzeit() {
        val next = nextReminderAt(berlin(2026, 3, 28, 19), ABENDS)
        assertEquals(berlin(2026, 3, 29, 18), next)
        assertEquals(22L * 60, java.time.Duration.between(berlin(2026, 3, 28, 19), next).toMinutes())
    }

    /** 02:30 gibt es am Tag der Umstellung nicht – dann gilt die Uhrzeit danach. */
    @Test
    fun eineUhrzeitInDerUebersprungenenStundeRueckt() {
        val next = nextReminderAt(berlin(2026, 3, 28, 19), LocalTime.of(2, 30))
        assertEquals(LocalTime.of(3, 30), next.toLocalTime())
        assertEquals(LocalDate.of(2026, 3, 29), next.toLocalDate())
    }

    /** Nach einem Wechsel der Zeitzone gilt die Uhrzeit der neuen Zone. */
    @Test
    fun dieUhrzeitGiltInDerZoneDesGeraets() {
        val lissabon = ZonedDateTime.of(2026, 10, 7, 9, 0, 0, 0, ZoneId.of("Europe/Lisbon"))
        val next = nextReminderAt(lissabon, ABENDS)
        assertEquals(ABENDS, next.toLocalTime())
        assertEquals(ZoneId.of("Europe/Lisbon"), next.zone)
    }

    @Test
    fun vorDerUhrzeitWirdNichtErinnert() {
        assertFalse(isReminderTime(berlin(2026, 10, 8, 3), ABENDS))
        assertTrue(isReminderTime(berlin(2026, 10, 7, 18), ABENDS))
        assertTrue(isReminderTime(berlin(2026, 10, 7, 23, 30), ABENDS))
    }

    @Test
    fun dieMinuteDesTagesWirdZurUhrzeit() {
        assertEquals(LocalTime.of(18, 0), DEFAULT_REMINDER_MINUTE.toReminderTime())
        assertEquals(LocalTime.of(7, 45), (7 * 60 + 45).toReminderTime())
        assertEquals(LocalTime.of(23, 59), 5000.toReminderTime())
        assertEquals(LocalTime.MIDNIGHT, (-3).toReminderTime())
    }

    // --- Deload ---------------------------------------------------------------

    /** Drei Trainings pro Woche ab [start], [weeks] Wochen lang. */
    private fun trainings(start: LocalDate, weeks: Int): List<LocalDate> =
        (0 until weeks * 7 step 2).map { start.plusDays(it.toLong()) }

    @Test
    fun amErstenTagDerDeloadWocheKommtDieErinnerung() {
        val start = LocalDate.of(2026, 8, 3)
        val deloadStart = start.plusWeeks(5)
        val status = deloadStatus(trainings(start, 5), deloadStart, 6)
        assertTrue(status.isDeloadWeek)
        assertTrue(deloadReminderDue(status, deloadStart, ReminderLog()))
    }

    @Test
    fun spaeterInDerDeloadWocheKommtKeine() {
        val start = LocalDate.of(2026, 8, 3)
        val day = start.plusWeeks(5).plusDays(2)
        val status = deloadStatus(trainings(start, 5), day, 6)
        assertTrue(status.isDeloadWeek)
        assertFalse(deloadReminderDue(status, day, ReminderLog()))
    }

    @Test
    fun dieDeloadErinnerungKommtNurEinmal() {
        val start = LocalDate.of(2026, 8, 3)
        val deloadStart = start.plusWeeks(5)
        val status = deloadStatus(trainings(start, 5), deloadStart, 6)
        assertFalse(deloadReminderDue(status, deloadStart, ReminderLog(deloadWeekStart = deloadStart)))
    }

    @Test
    fun ausserhalbDerDeloadWocheKommtKeine() {
        val start = LocalDate.of(2026, 8, 3)
        val day = start.plusWeeks(2)
        assertFalse(deloadReminderDue(deloadStatus(trainings(start, 2), day, 6), day, ReminderLog()))
    }

    // --- Trainingspause -------------------------------------------------------

    private val heute = LocalDate.of(2026, 10, 7)

    @Test
    fun nachDenEingestelltenTagenKommtDiePausenErinnerung() {
        assertEquals(
            PauseReminder(days = 4, dueDayId = 3),
            pauseReminder(heute.minusDays(4), heute, 4, 3, ReminderLog())
        )
        assertEquals(6, pauseReminder(heute.minusDays(6), heute, 4, 3, ReminderLog())?.days)
    }

    @Test
    fun vorherKommtKeine() {
        assertNull(pauseReminder(heute.minusDays(3), heute, 4, 3, ReminderLog()))
        assertNull(pauseReminder(heute, heute, 4, 3, ReminderLog()))
    }

    @Test
    fun ohneEinzigesTrainingGibtEsKeinePause() {
        assertNull(pauseReminder(null, heute, 4, 1, ReminderLog()))
    }

    @Test
    fun hoechstensEinmalAmTag() {
        val log = ReminderLog(pauseAfterSession = heute.minusDays(5), pauseCount = 1, pauseRemindedOn = heute)
        assertNull(pauseReminder(heute.minusDays(5), heute, 4, 3, log))
    }

    @Test
    fun nachDreiErinnerungenIstRuheBisZumNaechstenTraining() {
        val last = heute.minusDays(7)
        val log = ReminderLog(pauseAfterSession = last, pauseCount = 3, pauseRemindedOn = heute.minusDays(1))
        assertNull(pauseReminder(last, heute, 4, 3, log))
        // Ein neues Training beginnt die Zählung von vorn.
        val neu = heute.minusDays(4)
        assertEquals(4, pauseReminder(neu, heute, 4, 3, log)?.days)
    }

    @Test
    fun dieTageWerdenAufDenErlaubtenBereichBegrenzt() {
        assertNull(pauseReminder(heute.minusDays(1), heute, 0, 1, ReminderLog()))
        assertEquals(2, pauseReminder(heute.minusDays(2), heute, 0, 1, ReminderLog())?.days)
        assertNull(pauseReminder(heute.minusDays(13), heute, 99, 1, ReminderLog()))
    }

    // --- Täglicher Lauf -------------------------------------------------------

    @Test
    fun derLaufZaehltDiePausenErinnerungenMit() {
        val settings = ReminderSettings(pause = true, pauseDays = 4)
        val dates = listOf(heute.minusDays(10), heute.minusDays(4))

        val first = dailyReminders(settings, dates, 6, 2, heute, ReminderLog())
        assertEquals(PauseReminder(4, 2), first.pause)
        assertEquals(ReminderLog(pauseAfterSession = heute.minusDays(4), pauseCount = 1, pauseRemindedOn = heute), first.log)

        // Derselbe Tag noch einmal – etwa ein doppelter Lauf: nichts.
        val again = dailyReminders(settings, dates, 6, 2, heute, first.log)
        assertNull(again.pause)
        assertEquals(first.log, again.log)

        val second = dailyReminders(settings, dates, 6, 2, heute.plusDays(1), first.log)
        val third = dailyReminders(settings, dates, 6, 2, heute.plusDays(2), second.log)
        val fourth = dailyReminders(settings, dates, 6, 2, heute.plusDays(3), third.log)
        assertEquals(5, second.pause?.days)
        assertEquals(6, third.pause?.days)
        assertEquals(3, third.log.pauseCount)
        assertNull(fourth.pause)
    }

    @Test
    fun ausgeschalteteErinnerungenSchickenNichts() {
        val dates = listOf(heute.minusDays(9))
        val result = dailyReminders(ReminderSettings(), dates, 6, 1, heute, ReminderLog())
        assertFalse(result.deload)
        assertNull(result.pause)
        assertEquals(ReminderLog(), result.log)
    }

    /** Ein Eintrag in der Zukunft – Zeitumstellung, eingelesene Sicherung – ist nicht das letzte Training. */
    @Test
    fun einTrainingInDerZukunftZaehltNicht() {
        val dates = listOf(heute.minusDays(5), heute.plusDays(1))
        val result = dailyReminders(ReminderSettings(pause = true), dates, 6, 1, heute, ReminderLog())
        assertEquals(5, result.pause?.days)
    }

    @Test
    fun derLaufMerktSichDieDeloadWoche() {
        val start = LocalDate.of(2026, 8, 3)
        val deloadStart = start.plusWeeks(5)
        val result = dailyReminders(
            ReminderSettings(deload = true),
            trainings(start, 5),
            6,
            1,
            deloadStart,
            ReminderLog()
        )
        assertTrue(result.deload)
        assertEquals(deloadStart, result.log.deloadWeekStart)
        assertFalse(dailyReminders(ReminderSettings(deload = true), trainings(start, 5), 6, 1, deloadStart, result.log).deload)
    }

    @Test
    fun nurDeloadUndPauseBrauchenDenTaeglichenAuftrag() {
        assertFalse(ReminderSettings().needsDailyRun)
        assertTrue(ReminderSettings().anyEnabled)
        assertTrue(ReminderSettings(deload = true).needsDailyRun)
        assertTrue(ReminderSettings(pause = true).needsDailyRun)
        assertFalse(ReminderSettings(backup = false).anyEnabled)
    }

    // --- Sicherung ------------------------------------------------------------

    @Test
    fun eineGescheiterteSicherungMeldetSichEinmal() {
        assertTrue(backupReminderDue(true, true, "Kein Zugriff", 1000L, ReminderLog()))
        assertFalse(backupReminderDue(true, true, "Kein Zugriff", 1000L, ReminderLog(backupFailureAt = 1000L)))
        // Der nächste gescheiterte Versuch ist eine neue Nachricht.
        assertTrue(backupReminderDue(true, true, "Kein Zugriff", 2000L, ReminderLog(backupFailureAt = 1000L)))
    }

    @Test
    fun ohneFehlerOderAusgeschaltetKommtNichts() {
        assertFalse(backupReminderDue(true, true, null, 1000L, ReminderLog()))
        assertFalse(backupReminderDue(false, true, "Fehler", 1000L, ReminderLog()))
        assertFalse(backupReminderDue(true, false, "Fehler", 1000L, ReminderLog()))
        assertFalse(backupReminderDue(true, true, "Fehler", null, ReminderLog()))
    }
}
