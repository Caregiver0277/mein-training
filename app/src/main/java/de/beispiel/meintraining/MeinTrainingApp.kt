package de.beispiel.meintraining

import android.app.Application
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.repository.TrainingRepository

/** Einfache manuelle Abhängigkeitsverwaltung – für diese App reicht das aus. */
class MeinTrainingApp : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }
    private val settingsStore by lazy { SettingsStore(this) }

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
}
