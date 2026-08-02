package de.beispiel.meintraining

import android.app.Application
import de.beispiel.meintraining.data.backup.BackupRepository
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.repository.TrainingRepository

/** Einfache manuelle Abhängigkeitsverwaltung – für diese App reicht das aus. */
class MeinTrainingApp : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }

    /** Auch der Sicherungs-Worker greift darauf zu, deshalb nicht privat. */
    val settingsStore by lazy { SettingsStore(this) }

    val repository: TrainingRepository by lazy {
        TrainingRepository(
            context = this,
            database = database,
            dayDao = database.trainingDayDao(),
            exerciseDao = database.exerciseDao(),
            definitionDao = database.exerciseDefinitionDao(),
            weightLogDao = database.weightLogDao(),
            sessionDao = database.workoutSessionDao(),
            settingsStore = settingsStore
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = this,
            database = database,
            settingsStore = settingsStore,
            // Nach dem Einspielen müssen die Trainingstage der Sicherung wieder vollständig
            // sein; dafür kennt nur das Trainings-Repository die Regeln.
            trainingRepository = repository
        )
    }
}
