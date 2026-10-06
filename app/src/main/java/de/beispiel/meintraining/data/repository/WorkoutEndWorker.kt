package de.beispiel.meintraining.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.beispiel.meintraining.MeinTrainingApp
import java.util.concurrent.TimeUnit

/**
 * Wo das automatische Ende eines Trainings angemeldet wird.
 *
 * Eine eigene Schnittstelle statt des Workers selbst, damit das Repository ohne WorkManager
 * auskommt – in den Tests gibt es keinen.
 */
fun interface WorkoutEndScheduler {

    /** Meldet die Prüfung für den Zeitpunkt [atMillis] an; `null` meldet sie ab. */
    fun schedule(atMillis: Long?)

    companion object {
        /** Meldet nichts an – für die Tests; dort ruft man das Ende von Hand. */
        val None = WorkoutEndScheduler { }
    }
}

/**
 * Beendet ein Training, in dem sich nichts mehr tut – auch bei geschlossener App (siehe
 * [TrainingRepository.finishIdleWorkout]).
 *
 * Ein einmaliger Auftrag unter festem Namen, bei jeder Aktivität ersetzt: Es gibt immer
 * höchstens einen, und er steht auf dem Zeitpunkt, an dem die Ruhezeit nach der jüngsten
 * Aktivität um ist. Kommt er zu früh oder hat sich seither etwas getan, meldet das Repository
 * ihn für den neuen Zeitpunkt wieder an.
 *
 * Auf die Minute genau muss er nicht kommen: Abgehakt wird ohnehin zum Zeitpunkt der letzten
 * Aktivität. Verschiebt Android ihn, steht das Training nur später im Verlauf – und öffnet man
 * die App vorher, holt sie das Ende selbst nach.
 */
class WorkoutEndWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // In Tests und in einem fremden Prozess ist das nicht die App-Klasse; dann gibt es auch
        // kein Training, das hier enden könnte.
        val app = applicationContext as? MeinTrainingApp ?: return Result.success()
        app.repository.finishIdleWorkout()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "trainingsende"

        /** Der [WorkoutEndScheduler] der App. */
        fun scheduler(context: Context) = WorkoutEndScheduler { atMillis ->
            val workManager = WorkManager.getInstance(context)
            if (atMillis == null) {
                workManager.cancelUniqueWork(WORK_NAME)
            } else {
                val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                val request = OneTimeWorkRequestBuilder<WorkoutEndWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
}
