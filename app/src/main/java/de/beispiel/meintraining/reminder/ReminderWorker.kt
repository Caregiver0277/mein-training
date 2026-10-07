package de.beispiel.meintraining.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.beispiel.meintraining.MeinTrainingApp
import java.util.concurrent.TimeUnit

/**
 * Der tägliche Lauf der Erinnerungen – siehe [Reminders.runDaily].
 *
 * Ein einmaliger Auftrag bis zur nächsten eingestellten Uhrzeit, der sich nach jedem Lauf selbst
 * neu anmeldet. Ein periodischer Auftrag hielte keine Uhrzeit ein: Er läuft irgendwann im Abstand
 * von 24 Stunden, und nach jeder Zeitumstellung eine Stunde daneben.
 *
 * Einen Neustart des Geräts übersteht der Auftrag von selbst – WorkManager meldet seine Aufträge
 * danach wieder an und rechnet den Zeitpunkt in Uhrzeit, nicht in Laufzeit. Eine neue Zeitzone
 * oder eine von Hand verstellte Uhr fängt [ReminderTimeReceiver] ab.
 */
class ReminderWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // In Tests und in einem fremden Prozess ist das nicht die App-Klasse; dann gibt es auch
        // nichts, woran zu erinnern wäre.
        val app = applicationContext as? MeinTrainingApp ?: return Result.success()
        app.reminders.runDaily()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "erinnerungen"

        /**
         * Meldet den Lauf für [atMillis] an.
         *
         * [policy] ist beim Neuanmelden aus dem Lauf heraus `APPEND_OR_REPLACE`: Mit `REPLACE`
         * bräche der Auftrag sich selbst ab, mitten im Lauf. Sonst – neue Uhrzeit, neuer Schalter,
         * neue Zeitzone – `REPLACE`, damit der alte Termin verschwindet; beim Start der App
         * `KEEP`, damit ein gerade laufender Auftrag nicht abgebrochen wird.
         */
        fun schedule(context: Context, atMillis: Long, policy: ExistingWorkPolicy) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
