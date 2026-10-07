package de.beispiel.meintraining.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Vorgabe für die Uhrzeit der täglichen Erinnerungen: 18:00, als Minute des Tages. */
const val DEFAULT_REMINDER_MINUTE = 18 * 60

/** Letzte Minute eines Tages – 23:59; darüber hinaus gibt es keine Uhrzeit. */
const val MAX_REMINDER_MINUTE = 24 * 60 - 1

/** Grenzen und Vorgabe für „Trainingspause“: nach so vielen Tagen ohne Training. */
const val MIN_PAUSE_DAYS = 2
const val MAX_PAUSE_DAYS = 14
const val DEFAULT_PAUSE_DAYS = 4

/**
 * So viele Pausen-Erinnerungen hintereinander, danach ist Ruhe bis zum nächsten Training.
 *
 * Wer drei Tage in Folge daran erinnert wurde und trotzdem nicht trainiert, hat einen Grund – eine
 * vierte Nachricht wäre nur noch Nörgeln.
 */
const val MAX_PAUSE_REMINDERS = 3

/** Die Schalter und Werte des Bereichs „Erinnerungen“. */
data class ReminderSettings(
    /** „Deload-Woche beginnt“; Vorgabe aus. */
    val deload: Boolean = false,
    /** „Trainingspause“; Vorgabe aus. */
    val pause: Boolean = false,
    /** Nach so vielen Tagen ohne Training meldet sich „Trainingspause“. */
    val pauseDays: Int = DEFAULT_PAUSE_DAYS,
    /**
     * „Sicherung fehlgeschlagen“; Vorgabe an – eine Sicherung, die still versagt, ist schlimmer
     * als keine. Ohne eingeschaltete automatische Sicherung kann sie nicht scheitern, der Schalter
     * kostet dann nichts.
     */
    val backup: Boolean = true,
    /** Uhrzeit der täglichen Erinnerungen als Minute des Tages, 0 bis [MAX_REMINDER_MINUTE]. */
    val minuteOfDay: Int = DEFAULT_REMINDER_MINUTE
) {
    /**
     * Braucht es den täglichen Auftrag? Nur für Deload und Pause – „Sicherung fehlgeschlagen“
     * kommt nicht zur Uhrzeit, sondern sobald die Sicherung scheitert.
     */
    val needsDailyRun: Boolean get() = deload || pause

    /** Ist überhaupt eine Erinnerung an? Nur dann zählt, ob Benachrichtigungen erlaubt sind. */
    val anyEnabled: Boolean get() = deload || pause || backup

    val time: LocalTime get() = minuteOfDay.toReminderTime()
}

/** Minute des Tages als Uhrzeit, auf den gültigen Bereich begrenzt. */
fun Int.toReminderTime(): LocalTime {
    val minute = coerceIn(0, MAX_REMINDER_MINUTE)
    return LocalTime.of(minute / 60, minute % 60)
}

/**
 * Was schon gemeldet wurde – damit nichts doppelt kommt, auch wenn der Auftrag zweimal läuft,
 * Android ihn verschiebt oder die App ihn beim Start erneut anmeldet.
 */
data class ReminderLog(
    /** Erster Tag der Deload-Woche, zu der schon erinnert wurde. */
    val deloadWeekStart: LocalDate? = null,
    /** Das letzte Training, von dem aus die Pausen-Erinnerungen gezählt sind. */
    val pauseAfterSession: LocalDate? = null,
    /** So viele Pausen-Erinnerungen seit [pauseAfterSession]. */
    val pauseCount: Int = 0,
    /** Tag der letzten Pausen-Erinnerung – höchstens eine am Tag. */
    val pauseRemindedOn: LocalDate? = null,
    /** Zeitpunkt der gescheiterten Sicherung, zu der schon eine Nachricht kam. */
    val backupFailureAt: Long? = null
)

/** Die Pausen-Erinnerung, die heute fällig ist – siehe [pauseReminder]. */
data class PauseReminder(
    /** Tage seit dem letzten Training. */
    val days: Int,
    /** Der Tag, der jetzt dran wäre – derselbe wie bei der automatischen Tagesauswahl. */
    val dueDayId: Int
)

/** Was der tägliche Lauf schickt, und was er sich danach merkt – siehe [dailyReminders]. */
data class DailyReminders(
    val deload: Boolean = false,
    val pause: PauseReminder? = null,
    val log: ReminderLog
)

/**
 * Der nächste Zeitpunkt für die täglichen Erinnerungen nach [now]: heute um [time], wenn das noch
 * bevorsteht, sonst morgen.
 *
 * Gerechnet wird in der Zeitzone von [now] und erst am Schluss in einen Zeitpunkt verwandelt:
 * Ein fester Abstand von 24 Stunden käme nach jeder Zeitumstellung eine Stunde daneben heraus.
 * Fällt [time] in die übersprungene Stunde der Umstellung auf Sommerzeit, gilt die Uhrzeit danach
 * (02:30 wird 03:30); in der doppelten Stunde im Herbst die erste.
 */
fun nextReminderAt(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
}

/**
 * Darf der Lauf um [now] erinnern? Erst ab der eingestellten Uhrzeit des Tages.
 *
 * Ein Lauf vor der Uhrzeit ist einer, den Android über Nacht verschoben hat – oder einer von
 * gestern Abend, der erst nach Mitternacht kam. Eine Nachricht um drei Uhr früh weckt nur; dann
 * lieber zur Uhrzeit desselben Tages.
 */
fun isReminderTime(now: ZonedDateTime, time: LocalTime): Boolean =
    !now.isBefore(ZonedDateTime.of(now.toLocalDate(), time, now.zone))

/**
 * „Deload-Woche beginnt“: genau am ersten Tag der Deload-Woche, einmal.
 *
 * Nur am ersten Tag und nicht irgendwann in der Woche: „Ab heute Deload“ am Donnerstag wäre
 * falsch, und wer den Schalter mitten in der Woche einschaltet, sieht den Deload ohnehin in der
 * App.
 */
fun deloadReminderDue(status: DeloadStatus, today: LocalDate, log: ReminderLog): Boolean =
    status.isDeloadWeek && status.deloadWeekStart == today && log.deloadWeekStart != today

/**
 * „Trainingspause“: nach [afterDays] Tagen ohne Training, höchstens einmal am Tag und höchstens
 * [MAX_PAUSE_REMINDERS]-mal hintereinander; danach ist Ruhe bis zum nächsten Training. `null`,
 * wenn heute nichts fällig ist.
 *
 * Ohne ein einziges Training ([lastSession] `null`) gibt es keine Pause – nur einen Anfang, und
 * an den erinnert die App nicht.
 *
 * Gezählt wird ab dem letzten Training: Kommt ein neues dazu, beginnt die Zählung von vorn. Ein
 * nachgetragenes älteres Training ändert daran nichts, denn es ist nicht das letzte.
 */
fun pauseReminder(
    lastSession: LocalDate?,
    today: LocalDate,
    afterDays: Int,
    dueDayId: Int,
    log: ReminderLog
): PauseReminder? {
    lastSession ?: return null
    val days = ChronoUnit.DAYS.between(lastSession, today)
    if (days < afterDays.coerceIn(MIN_PAUSE_DAYS, MAX_PAUSE_DAYS)) return null
    if (log.pauseRemindedOn == today) return null
    if (log.pausesAfter(lastSession) >= MAX_PAUSE_REMINDERS) return null
    return PauseReminder(days = days.toInt(), dueDayId = dueDayId)
}

/** Erinnerungen seit [lastSession]; ein neueres Training setzt die Zählung zurück. */
private fun ReminderLog.pausesAfter(lastSession: LocalDate): Int =
    if (pauseAfterSession == lastSession) pauseCount else 0

/**
 * Der ganze tägliche Lauf: was zu schicken ist und der Stand danach.
 *
 * [sessionDates] sind die Tage der abgehakten Trainings, in beliebiger Reihenfolge; Einträge nach
 * [today] – durch eine Zeitumstellung oder eine eingelesene Sicherung – zählen nicht als letztes
 * Training, sonst stünde die Pause bei minus einem Tag.
 */
fun dailyReminders(
    settings: ReminderSettings,
    sessionDates: List<LocalDate>,
    cycleWeeks: Int,
    dueDayId: Int,
    today: LocalDate,
    log: ReminderLog
): DailyReminders {
    val deload = settings.deload &&
        deloadReminderDue(deloadStatus(sessionDates, today, cycleWeeks), today, log)
    val lastSession = sessionDates.filterNot { it.isAfter(today) }.maxOrNull()
    val pause = if (settings.pause) {
        pauseReminder(lastSession, today, settings.pauseDays, dueDayId, log)
    } else {
        null
    }

    var updated = log
    if (deload) updated = updated.copy(deloadWeekStart = today)
    if (pause != null && lastSession != null) {
        updated = updated.copy(
            pauseAfterSession = lastSession,
            pauseCount = log.pausesAfter(lastSession) + 1,
            pauseRemindedOn = today
        )
    }
    return DailyReminders(deload = deload, pause = pause, log = updated)
}

/**
 * „Sicherung fehlgeschlagen“: fällig, wenn die eingeschaltete automatische Sicherung zuletzt
 * gescheitert ist und zu genau diesem Fehlschlag noch keine Nachricht kam.
 *
 * Ein Fehlschlag ist an seinem Zeitpunkt zu erkennen: Scheitert die nächste Sicherung wieder,
 * ist das ein neuer Zeitpunkt und eine neue Nachricht – einmal je Versuch, nicht bei jedem Start.
 */
fun backupReminderDue(
    reminderEnabled: Boolean,
    autoBackupEnabled: Boolean,
    lastError: String?,
    lastAttemptAt: Long?,
    log: ReminderLog
): Boolean = reminderEnabled && autoBackupEnabled && lastError != null &&
    lastAttemptAt != null && lastAttemptAt != log.backupFailureAt
