package de.beispiel.meintraining.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.beispiel.meintraining.MeinTrainingApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Schreibt die Sicherung im Hintergrund in die gewählte Datei.
 *
 * Der Worker gibt niemals `retry` zurück: Läuft eine Sicherung schief – etwa weil die Datei
 * gelöscht oder der Zugriff entzogen wurde –, hilft ein sofortiger zweiter Versuch nicht. Der
 * Grund wird stattdessen festgehalten und in den Einstellungen angezeigt.
 */
class BackupWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // In Tests und in einem fremden Prozess ist das nicht die App-Klasse; ohne Zugriff
        // auf ihre Bestandteile gibt es nichts zu sichern, aber auch nichts zu melden.
        val app = applicationContext as? MeinTrainingApp ?: return Result.success()
        val settings = app.settingsStore
        val backups = app.backupRepository

        if (!settings.backupEnabled.first()) return Result.success()
        val target = settings.backupTargetUri.first()
            ?: return finish(app, "Keine Sicherungsdatei ausgewählt.")

        return try {
            val backup = backups.createBackup()
            // Eine leere Sicherung über die vorhandene zu schreiben wäre der teuerste Fehler,
            // den diese App machen kann: Es gibt genau eine Datei, und sie wird bei jedem
            // Durchlauf überschrieben. Steht gerade nichts da – frisch zurückgesetzt, ein
            // misslungener Import –, bleibt die alte Sicherung lieber stehen.
            if (backup.hasContent) {
                backups.writeTo(Uri.parse(target), backup)
                finish(app, error = null)
            } else {
                finish(app, "Keine Daten zum Sichern – die vorhandene Datei bleibt unverändert.")
            }
        } catch (throwable: Exception) {
            finish(app, throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    private suspend fun finish(app: MeinTrainingApp, error: String?): Result {
        app.settingsStore.setLastBackupResult(System.currentTimeMillis(), error)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "automatische-sicherung"

        /**
         * Meldet die wiederkehrende Sicherung an oder ab.
         *
         * [ExistingPeriodicWorkPolicy.UPDATE] statt `KEEP`, damit ein geänderter Abstand sofort
         * gilt – sonst liefe stillschweigend der alte weiter.
         */
        fun schedule(context: Context, intervalDays: Int) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                intervalDays.toLong().coerceAtLeast(1L),
                TimeUnit.DAYS
            )
                // Auf eine Sicherung wartet niemand: Sie darf ein paar Stunden später laufen,
                // wenn der Akku dann nicht mehr im roten Bereich ist.
                //
                // Bewusst keine Netzbedingung, obwohl das Ziel in der Cloud liegen kann: Es
                // kann genauso gut eine Datei auf dem Gerät sein, und die wäre dann ohne Not
                // vom Netz abhängig.
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
