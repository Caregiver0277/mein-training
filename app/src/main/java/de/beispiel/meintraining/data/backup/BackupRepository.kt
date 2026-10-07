package de.beispiel.meintraining.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.SettingsSnapshot
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.model.Exercise
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.SetLog
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.data.repository.TrainingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Obergrenze für eine einzulesende Datei; eine echte Sicherung bleibt weit darunter. */
private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

/**
 * Sichern und Wiederherstellen des kompletten Bestands.
 *
 * Die Sicherung ist eine einzelne JSON-Datei – lesbar, klein und ohne Abhängigkeit von der
 * internen Struktur der Datenbank. Beim Einlesen wird der bisherige Bestand vollständig
 * ersetzt, nicht ergänzt: Zusammenführen zweier Verläufe führte nur zu Dubletten, die niemand
 * mehr auseinanderbekommt.
 */
class BackupRepository(
    context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val trainingRepository: TrainingRepository
) {

    private val appContext = context.applicationContext

    /**
     * Sammelt den gesamten Bestand ein.
     *
     * Die Tabellen werden in einer Transaktion gelesen: Einzeln abgefragt, könnte sich zwischen
     * zwei Abfragen eine Änderung schieben – etwa eine eben angelegte Übung, deren Definition
     * schon gelesen war, die Zeile selbst aber noch nicht. Die Sicherung enthielte dann einen
     * Stand, den es so nie gab. Die Einstellungen kommen vorher und außerhalb dazu; sie liegen
     * nicht in der Datenbank, und auf sie zu warten hielte deren einzigen Transaktionsfaden auf.
     */
    suspend fun createBackup(now: Long = System.currentTimeMillis()): BackupFile {
        val settings = settingsStore.snapshot()
        return database.withTransaction { collectBackup(now, settings) }
    }

    private suspend fun collectBackup(now: Long, settings: SettingsSnapshot): BackupFile {
        val days = database.trainingDayDao().listAll()
        val exercises = database.exerciseDao().listAll()
        val definitions = database.exerciseDefinitionDao().listAll()
        val logs = database.weightLogDao().listAll()
        val sessions = database.workoutSessionDao().listAll()
        val setLogs = database.setLogDao().listAll()
        val cardioLogs = database.cardioLogDao().listAll()

        return BackupFile(
            createdAt = now,
            days = days.map { BackupDay(id = it.id, name = it.name) },
            exercises = exercises.map {
                BackupExercise(
                    id = it.id,
                    dayId = it.dayId,
                    name = it.name,
                    variation = it.variation,
                    sets = it.sets,
                    repsMin = it.repsMin,
                    repsMax = it.repsMax,
                    position = it.position,
                    supersetId = it.supersetId
                )
            },
            definitions = definitions.map {
                BackupDefinition(
                    name = it.name,
                    weightKg = it.weightKg,
                    progressionStepKg = it.progressionStepKg,
                    progressionDown = it.progressionDown,
                    note = it.note,
                    logSets = it.logSets,
                    kind = it.kind,
                    cardio = with(it.cardio) {
                        BackupCardio(
                            durationMin = durationMin,
                            distanceKm = distanceKm,
                            intensity = intensity,
                            intensityUnit = intensityUnit,
                            inclinePercent = inclinePercent,
                            arrowValue = arrowValue,
                            arrowStep = arrowStep,
                            arrowDown = arrowDown
                        )
                    }
                )
            },
            weightLogs = logs.map {
                BackupWeightLog(
                    exerciseName = it.exerciseName,
                    weightKg = it.weightKg,
                    recordedAt = it.recordedAt
                )
            },
            sessions = sessions.map {
                BackupSession(dayId = it.dayId, completedAt = it.completedAt, startedAt = it.startedAt)
            },
            setLogs = setLogs.map {
                BackupSetLog(
                    exerciseName = it.exerciseName,
                    variation = it.variation,
                    dayId = it.dayId,
                    performedAt = it.performedAt,
                    setNumber = it.setNumber,
                    reps = it.reps,
                    weightKg = it.weightKg
                )
            },
            cardioLogs = cardioLogs.map {
                BackupCardioLog(
                    exerciseName = it.exerciseName,
                    variation = it.variation,
                    dayId = it.dayId,
                    performedAt = it.performedAt,
                    durationMin = it.durationMin,
                    distanceKm = it.distanceKm,
                    intensity = it.intensity,
                    intensityUnit = it.intensityUnit,
                    inclinePercent = it.inclinePercent
                )
            },
            settings = with(settings) {
                BackupSettings(
                    appTitle = appTitle,
                    deloadCycleWeeks = deloadCycleWeeks,
                    weeklyGoal = weeklyGoal,
                    dayCount = dayCount,
                    selectedDayId = selectedDayId,
                    hiddenTrackingNames = hiddenTrackingNames.toList(),
                    hiddenExerciseNames = hiddenExerciseNames.toList()
                )
            }
        )
    }

    fun encode(backup: BackupFile): String = BackupCodec.encode(backup)

    fun decode(text: String): BackupFile = BackupCodec.decode(text)

    /**
     * Schreibt die Sicherung in die angegebene Datei und liest sie sofort wieder ein.
     *
     * Das Zurücklesen ist der eigentliche Punkt: "rwt" kürzt die Datei, bevor der neue Inhalt
     * darin steht. Bricht das Schreiben dazwischen ab – kein Platz mehr, Zugriff entzogen,
     * Prozess beendet –, bliebe ein Rest zurück, den niemand mehr einspielen kann; gemeldet
     * wäre die Sicherung trotzdem als gelungen. Erst wenn dieselbe Sicherung wieder
     * herauskommt, gilt sie als geschrieben.
     *
     * Die Datei liegt womöglich in der Cloud, deshalb läuft beides abseits des Hauptthreads.
     */
    suspend fun writeTo(uri: Uri, backup: BackupFile) {
        val text = encode(backup)
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "rwt")?.use { stream ->
                stream.write(text.toByteArray())
            } ?: throw BackupFormatException(BackupProblem.NotWritable)
        }

        val written = try {
            decode(readFrom(uri))
        } catch (throwable: Exception) {
            throw BackupFormatException(BackupProblem.NotReadBack, throwable)
        }
        if (written != backup) throw BackupFormatException(BackupProblem.Incomplete)
    }

    /**
     * Liest den Text einer Sicherungsdatei.
     *
     * Mit einer Obergrenze, weil der Auswahldialog beim Einlesen jede Datei anbietet: Ein
     * versehentlich gewähltes Video würde die App sonst beim Verschlucken abstürzen lassen,
     * statt einen Fehler zu zeigen. Für eine Sicherung ist die Grenze reichlich bemessen.
     */
    suspend fun readFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val collected = ByteArrayOutputStream()
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                collected.write(chunk, 0, read)
                if (collected.size() > MAX_BACKUP_BYTES) {
                    throw BackupFormatException(
                        BackupProblem.TooLarge(MAX_BACKUP_BYTES / (1024 * 1024))
                    )
                }
            }
            collected.toByteArray().decodeToString()
        } ?: throw BackupFormatException(BackupProblem.NotReadable)
    }

    /**
     * Der Name der Datei, wie ihr Anbieter ihn führt.
     *
     * Aus der Adresse allein lässt er sich nicht verlässlich ablesen: Bei Dateien auf dem Gerät
     * steht er zwar an ihrem Ende, bei Google Drive und anderen Cloud-Anbietern dagegen eine
     * interne Kennung wie „acc=1;doc=encoded=…“. `null`, wenn der Anbieter keinen Namen nennt –
     * etwa weil der Zugriff inzwischen entzogen wurde.
     */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeUnless { it.isBlank() }
    }

    /**
     * Sichert den dauerhaften Zugriff auf eine Datei. Ohne das wäre die Berechtigung nach dem
     * nächsten Neustart weg und die automatische Sicherung liefe ins Leere.
     */
    fun persistAccess(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Gibt den Zugriff auf eine nicht mehr benötigte Datei zurück. Die Zahl dauerhafter
     * Berechtigungen je App ist begrenzt; ohne das sammelt jedes gewechselte Ziel eine an.
     */
    fun releaseAccess(uri: Uri) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Ist die automatische Sicherung eingeschaltet, aber zuletzt gescheitert? Dann zeigt die
     * Einstellungs-Übersicht einen Hinweis. Ist sie aus, zählt ein alter Fehler nicht mehr.
     */
    val autoBackupFailing: Flow<Boolean> = combine(
        settingsStore.backupEnabled,
        settingsStore.lastBackupError
    ) { enabled, error -> enabled && error != null }

    /**
     * Stellt beim Start der App sicher, dass eine eingeschaltete automatische Sicherung auch
     * wirklich läuft.
     *
     * Nach einem Handywechsel stellt Android die Einstellungen wieder her – Schalter an, alte
     * Zieladresse, alter Erfolg –, aber weder den Auftrag bei WorkManager noch die dauerhafte
     * Berechtigung für die Datei. Ohne diese Prüfung würde nie mehr gesichert, und nichts wiese
     * darauf hin.
     *
     * Steht die Berechtigung noch, wird der Auftrag angemeldet, ohne seinen Takt neu zu beginnen
     * (siehe [BackupWorker.ensureScheduled]). Fehlt sie, lässt sich nichts retten: Die Datei
     * muss neu ausgewählt werden, und genau das steht dann als Fehler der letzten Sicherung da.
     */
    suspend fun ensureAutoBackup() {
        val accessible = appContext.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission }
            .mapTo(HashSet()) { it.uri.toString() }
        val check = autoBackupCheck(
            enabled = settingsStore.backupEnabled.first(),
            target = settingsStore.backupTargetUri.first(),
            accessibleTargets = accessible
        )
        when (check) {
            AutoBackupCheck.OFF -> Unit
            AutoBackupCheck.SCHEDULE ->
                BackupWorker.ensureScheduled(appContext, settingsStore.backupIntervalDays.first())
            AutoBackupCheck.ACCESS_LOST -> {
                val reason = appContext.getString(R.string.backup_error_access_lost)
                // Einmal vermerkt genügt – sonst rückte der Zeitpunkt bei jedem Start nach, und
                // es sähe aus, als sei eben erst eine Sicherung versucht worden.
                if (settingsStore.lastBackupError.first() != reason) {
                    settingsStore.setLastBackupResult(System.currentTimeMillis(), reason)
                }
            }
        }
    }

    /**
     * Beendet die automatische Sicherung vollständig: Zeitplan abbestellen, Zugriff auf die
     * Zieldatei zurückgeben, Einstellungen leeren.
     *
     * Der Zeitplan liegt bei WorkManager und überlebt sowohl das Leeren der Einstellungen als
     * auch einen Neustart des Geräts – ohne dieses Abbestellen liefe er ins Leere weiter.
     * Die Sicherungsdatei selbst bleibt liegen; sie gehört dem Nutzer, nicht der App.
     */
    suspend fun disableAutoBackup() {
        BackupWorker.cancel(appContext)
        settingsStore.backupTargetUri.first()?.let { releaseAccess(it.toUri()) }
        settingsStore.setBackupTargetUri(null)
        settingsStore.setBackupEnabled(false)
    }

    /**
     * Ersetzt den gesamten Bestand durch den der Sicherung.
     *
     * Der Inhalt der Datenbank wechselt in einer Transaktion: Bricht das Einspielen dort ab,
     * bleibt der bisherige Bestand unangetastet, statt dass eine halbe Sicherung zurückbleibt.
     *
     * Die Einstellungen liegen in einer eigenen Datei und lassen sich nicht in dieselbe
     * Transaktion aufnehmen. Sie werden deshalb erst danach geschrieben – geht dabei etwas
     * schief, steht der neue Bestand neben alten Einstellungen. Damit daraus kein
     * unbedienbarer Zustand wird, sind die Werte, die auf Tage zeigen, gegen die
     * eingespielten Tage abgeglichen.
     */
    suspend fun restore(backup: BackupFile) {
        database.withTransaction {
            database.weightLogDao().deleteAll()
            database.setLogDao().deleteAll()
            database.cardioLogDao().deleteAll()
            database.workoutSessionDao().deleteAll()
            database.exerciseDao().deleteAll()
            database.exerciseDefinitionDao().deleteAll()
            database.trainingDayDao().deleteAll()

            database.trainingDayDao().insertAll(
                backup.days.map { TrainingDay(id = it.id, name = it.name) }
            )
            database.exerciseDefinitionDao().upsertAll(
                backup.definitions.map {
                    ExerciseDefinition(
                        name = it.name,
                        weightKg = it.weightKg,
                        progressionStepKg = it.progressionStepKg,
                        progressionDown = it.progressionDown,
                        note = it.note,
                        logSets = it.logSets,
                        kind = it.kind,
                        cardio = with(it.cardio) {
                            CardioTargets(
                                durationMin = durationMin,
                                distanceKm = distanceKm,
                                intensity = intensity,
                                intensityUnit = intensityUnit,
                                inclinePercent = inclinePercent,
                                arrowValue = arrowValue,
                                arrowStep = arrowStep,
                                arrowDown = arrowDown
                            )
                        }
                    )
                }
            )
            database.exerciseDao().insertAll(
                backup.exercises.map {
                    Exercise(
                        id = it.id,
                        dayId = it.dayId,
                        name = it.name,
                        variation = it.variation,
                        sets = it.sets,
                        repsMin = it.repsMin,
                        repsMax = it.repsMax,
                        position = it.position,
                        supersetId = it.supersetId
                    )
                }
            )
            database.weightLogDao().insertAll(
                backup.weightLogs.map {
                    WeightLog(
                        exerciseName = it.exerciseName,
                        weightKg = it.weightKg,
                        recordedAt = it.recordedAt
                    )
                }
            )
            database.workoutSessionDao().insertAll(
                backup.sessions.map {
                    WorkoutSession(dayId = it.dayId, completedAt = it.completedAt, startedAt = it.startedAt)
                }
            )
            database.setLogDao().insertAll(
                backup.setLogs.map {
                    SetLog(
                        exerciseName = it.exerciseName,
                        variation = it.variation,
                        dayId = it.dayId,
                        performedAt = it.performedAt,
                        setNumber = it.setNumber,
                        reps = it.reps,
                        weightKg = it.weightKg
                    )
                }
            )
            database.cardioLogDao().insertAll(
                backup.cardioLogs.map {
                    CardioLog(
                        exerciseName = it.exerciseName,
                        variation = it.variation,
                        dayId = it.dayId,
                        performedAt = it.performedAt,
                        durationMin = it.durationMin,
                        distanceKm = it.distanceKm,
                        intensity = it.intensity,
                        intensityUnit = it.intensityUnit,
                        inclinePercent = it.inclinePercent
                    )
                }
            )
        }

        with(backup.settings) {
            settingsStore.setAppTitle(appTitle)
            deloadCycleWeeks?.let { settingsStore.setDeloadCycleWeeks(it) }
            weeklyGoal?.let { settingsStore.setWeeklyGoal(it) }
            dayCount?.let { settingsStore.setDayCount(it) }
            settingsStore.setHiddenTrackingNames(hiddenTrackingNames.toSet())
            settingsStore.setHiddenExerciseNames(hiddenExerciseNames.toSet())
            // Die Rundenschnitte gehören zum ersetzten Verlauf und wandern deshalb nicht mit in
            // die Datei: Ein Schnitt von diesem Gerät läge hinter allen eingespielten Trainings
            // und die laufende Runde stünde für immer auf null.
            settingsStore.setRotationCuts(emptyList())
            // Ein laufendes Training gehört ebenso zum ersetzten Bestand: Sein Merker zeigte auf
            // einen Tag und einen Eintrag, die es so nicht mehr gibt.
            settingsStore.setWorkoutMarkers(current = null, consumed = null)
            // Ein ausgewählter Tag außerhalb der Runde wäre ein Reiter, den es nicht gibt:
            // Die Liste bliebe leer und keine Auswahl ließe sich mehr treffen.
            //
            // Über das Trainings-Repository statt direkt in die Einstellungen: Es führt den
            // ausgewählten Tag im Speicher und würde einen an ihm vorbei geschriebenen Wert
            // mit der Auswahl aus der laufenden Sitzung überstimmen.
            val available = settingsStore.dayCount.first()
            trainingRepository.selectDay(
                selectedDayId?.takeIf { it in FIRST_DAY_ID..available } ?: FIRST_DAY_ID
            )
        }

        // Die Sicherung bringt genau die Tage mit, die sie kennt – eine Datei aus einer
        // kürzeren Runde oder aus einer Fassung ohne gespeicherte Tageszahl lässt sonst
        // Tage fehlen, und die App stünde ohne Reiter da. Angelegt wird erst jetzt, weil
        // die Anzahl aus der Sicherung stammt.
        trainingRepository.ensureSeeded()

        // Was die Sicherung erreicht hat, gilt still als gefeiert – sonst gäbe es gleich nach dem
        // Einlesen ein Konfetti-Gewitter. Ganz zuletzt, weil auch Wochenziel und Tageszahl aus der
        // Sicherung in die Meilensteine eingehen. Was zwischen dem Einspielen und hier schon
        // gemerkt wurde, schadet nicht: Gefeiert wird ohnehin nur, was heute erreicht wurde.
        trainingRepository.resetCelebratedMilestones()
    }
}
