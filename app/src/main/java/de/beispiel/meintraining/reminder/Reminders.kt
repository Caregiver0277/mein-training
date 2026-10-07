package de.beispiel.meintraining.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.ReminderSettings
import de.beispiel.meintraining.util.dailyReminders
import de.beispiel.meintraining.util.isReminderTime
import de.beispiel.meintraining.util.nextReminderAt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * Die Erinnerungen als lokale Benachrichtigungen: Schalter, Auftrag und Nachrichten.
 *
 * Die Bedingungen selbst sind reine Funktionen in `util/Reminders.kt`; hier kommen die Daten
 * dazu, das Merken in den Einstellungen und WorkManager.
 */
class Reminders(
    context: Context,
    private val settingsStore: SettingsStore,
    private val repository: TrainingRepository
) {

    private val appContext = context.applicationContext

    val settings: Flow<ReminderSettings> = settingsStore.reminderSettings

    suspend fun setDeload(enabled: Boolean) {
        settingsStore.setReminderDeload(enabled)
        reschedule()
    }

    suspend fun setPause(enabled: Boolean) {
        settingsStore.setReminderPause(enabled)
        reschedule()
    }

    /** Die Tage gehen erst beim nächsten Lauf ein; am Termin ändert sich nichts. */
    suspend fun setPauseDays(days: Int) = settingsStore.setReminderPauseDays(days)

    /** Kommt ohne Termin aus – siehe [notifyBackupFailure]. */
    suspend fun setBackup(enabled: Boolean) = settingsStore.setReminderBackup(enabled)

    suspend fun setMinuteOfDay(minuteOfDay: Int) {
        settingsStore.setReminderMinute(minuteOfDay)
        reschedule()
    }

    /**
     * Beim Start der App: Steht der Auftrag, wenn er gebraucht wird?
     *
     * Nach einem Handywechsel stellt Android die Schalter wieder her, den Auftrag bei WorkManager
     * aber nicht – genau wie bei der automatischen Sicherung. Ein bestehender Auftrag bleibt
     * unberührt, damit ein gerade laufender nicht abbricht.
     */
    suspend fun ensureScheduled() = schedule(ExistingWorkPolicy.KEEP)

    /**
     * Rechnet den Termin neu aus – nach jeder Änderung an Schaltern oder Uhrzeit, nach einer neuen
     * Zeitzone und nach „Alle Daten löschen“, das die Erinnerungen auf ihre Vorgaben zurücksetzt.
     * Ist weder Deload noch Pause an, wird der Auftrag abgemeldet.
     */
    suspend fun reschedule() = schedule(ExistingWorkPolicy.REPLACE)

    private suspend fun schedule(policy: ExistingWorkPolicy) {
        val current = settings.first()
        if (!current.needsDailyRun) {
            ReminderWorker.cancel(appContext)
            return
        }
        val next = nextReminderAt(ZonedDateTime.now(), current.time)
        ReminderWorker.schedule(appContext, next.toInstant().toEpochMilli(), policy)
    }

    /**
     * Der tägliche Lauf: Deload und Pause prüfen, melden, was fällig ist, und den nächsten Termin
     * anmelden.
     *
     * Ohne erlaubte Benachrichtigungen wird nichts als gemeldet vermerkt: Sonst gälte die
     * Deload-Erinnerung als verschickt, die nie jemand gesehen hat.
     */
    suspend fun runDaily(now: ZonedDateTime = ZonedDateTime.now()) {
        val current = settings.first()
        if (!current.needsDailyRun) return

        if (isReminderTime(now, current.time) && ReminderNotifications.allowed(appContext)) {
            val today = now.toLocalDate()
            val facts = repository.reminderFacts(today)
            val result = settingsStore.claimDailyReminders { settings, log ->
                dailyReminders(
                    settings = settings,
                    sessionDates = facts.sessionDates,
                    cycleWeeks = facts.cycleWeeks,
                    dueDayId = facts.dueDayId,
                    today = today,
                    log = log
                )
            }
            if (result.deload) {
                ReminderNotifications.show(
                    appContext,
                    ReminderKind.DELOAD,
                    title = appContext.getString(R.string.reminder_deload_title),
                    text = appContext.getString(R.string.reminder_deload_text)
                )
            }
            result.pause?.let { pause ->
                val day = facts.dueDayName.takeUnless {
                    it.isBlank() || it == appContext.getString(R.string.day_name, pause.dueDayId)
                }
                ReminderNotifications.show(
                    appContext,
                    ReminderKind.PAUSE,
                    title = appContext.getString(R.string.reminder_pause_title),
                    text = appContext.resources.getQuantityString(
                        if (day == null) R.plurals.reminder_pause_text else R.plurals.reminder_pause_text_named,
                        pause.days,
                        pause.days,
                        pause.dueDayId,
                        day.orEmpty()
                    )
                )
            }
        }

        // Aus dem Lauf heraus angehängt statt ersetzt – sonst bräche er sich selbst ab.
        val next = nextReminderAt(now, current.time)
        ReminderWorker.schedule(appContext, next.toInstant().toEpochMilli(), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /**
     * Meldet eine gescheiterte automatische Sicherung, einmal je Fehlschlag – nach jedem Lauf der
     * Sicherung und beim Start der App, wenn der Zugriff auf die Datei fehlt.
     */
    suspend fun notifyBackupFailure() {
        if (!ReminderNotifications.allowed(appContext)) return
        val reason = settingsStore.claimBackupFailureReminder() ?: return
        ReminderNotifications.show(
            appContext,
            ReminderKind.BACKUP,
            title = appContext.getString(R.string.reminder_backup_title),
            text = reason
        )
    }
}
