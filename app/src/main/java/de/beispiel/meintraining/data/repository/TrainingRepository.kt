package de.beispiel.meintraining.data.repository

import android.content.Context
import androidx.room.withTransaction
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.ExerciseDao
import de.beispiel.meintraining.data.local.ExerciseDefinitionDao
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.local.TrainingDayDao
import de.beispiel.meintraining.data.local.WeightLogDao
import de.beispiel.meintraining.data.local.WorkoutSessionDao
import de.beispiel.meintraining.data.model.Exercise
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.effectiveWeightKg
import de.beispiel.meintraining.util.nextDayId
import de.beispiel.meintraining.util.survivingSupersetMembers
import de.beispiel.meintraining.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Einzige Datenquelle für die ViewModels; kapselt Room und die Einstellungen. */
class TrainingRepository(
    context: Context,
    private val database: AppDatabase,
    private val dayDao: TrainingDayDao,
    private val exerciseDao: ExerciseDao,
    private val definitionDao: ExerciseDefinitionDao,
    private val weightLogDao: WeightLogDao,
    private val sessionDao: WorkoutSessionDao,
    private val settingsStore: SettingsStore
) {

    private val appContext = context.applicationContext

    val selectedDayId: Flow<Int> = settingsStore.selectedDayId

    fun observeDays(): Flow<List<TrainingDay>> = dayDao.observeAll()

    fun observeExercises(dayId: Int): Flow<List<ExerciseItem>> = exerciseDao.observeByDay(dayId)

    /** Alle Übungen aller Tage – für die Statistiken. */
    fun observeAllExercises(): Flow<List<ExerciseItem>> = exerciseDao.observeAll()

    /** Alle bekannten Übungen – Grundlage für die Vorschläge im Namensfeld. */
    fun observeDefinitions(): Flow<List<ExerciseDefinition>> = definitionDao.observeAll()

    /** Der komplette Gewichtsverlauf – Grundlage für den Tracking-Graphen. */
    fun observeWeightLogs(): Flow<List<WeightLog>> = weightLogDao.observeAll()

    /** Alle abgehakten Trainings, das jüngste zuerst. */
    fun observeSessions(): Flow<List<WorkoutSession>> = sessionDao.observeAll()

    /** Hakt das Training eines Tages ab und liefert die Kennung für „Rückgängig“. */
    suspend fun completeWorkout(dayId: Int): Long = sessionDao.insert(
        WorkoutSession(dayId = dayId, completedAt = System.currentTimeMillis())
    )

    suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)

    /** Im Tracking ausgeblendete Übungen. */
    val hiddenTrackingNames: Flow<Set<String>> = settingsStore.hiddenTrackingNames

    // --- Einstellungen -----------------------------------------------------

    val bodyweightKg: Flow<Double?> = settingsStore.bodyweightKg
    val deloadCycleWeeks: Flow<Int> = settingsStore.deloadCycleWeeks
    val appTitle: Flow<String> = settingsStore.appTitle

    /** Anzahl der Trainingstage in einer Runde. */
    val dayCount: Flow<Int> = settingsStore.dayCount

    suspend fun setDeloadCycleWeeks(weeks: Int) = settingsStore.setDeloadCycleWeeks(weeks)

    suspend fun setAppTitle(title: String) = settingsStore.setAppTitle(title)

    /**
     * Ändert die Anzahl der Trainingstage.
     *
     * Beim Verkleinern werden die überzähligen Tage nur ausgeblendet, nicht gelöscht – ihre
     * Übungen stehen unverändert wieder da, sobald die Runde wieder länger wird. Wer sie
     * wirklich loswerden will, leert sie von Hand.
     *
     * Zeigt die Auswahl auf einen Tag, den es nicht mehr gibt, springt sie auf den ersten.
     */
    suspend fun setDayCount(count: Int) {
        settingsStore.setDayCount(count)
        val effective = settingsStore.dayCount.first()
        ensureDaysExist(effective)
        if (settingsStore.selectedDayId.first() > effective) {
            settingsStore.setSelectedDayId(FIRST_DAY_ID)
        }
    }

    /** Legt fehlende Trainingstage an; vorhandene bleiben samt Namen unangetastet. */
    private suspend fun ensureDaysExist(count: Int) {
        val existing = dayDao.listAll().map { it.id }.toSet()
        val missing = (FIRST_DAY_ID..count).filterNot { it in existing }
        if (missing.isEmpty()) return
        dayDao.insertAll(
            missing.map { id -> TrainingDay(id = id, name = appContext.getString(R.string.day_name, id)) }
        )
    }

    suspend fun renameDay(dayId: Int, name: String) = dayDao.updateName(dayId, name)

    /**
     * Setzt das Körpergewicht. Weil damit auch die Last aller Körpergewichtsübungen steigt
     * oder fällt, bekommen diese einen neuen Verlaufseintrag – sonst zeigte der Graph eine
     * Änderung, die nie stattgefunden hätte.
     */
    suspend fun setBodyweightKg(weightKg: Double?) {
        val previous = settingsStore.bodyweightKg.first()
        settingsStore.setBodyweightKg(weightKg)
        if (weightKg == null || weightKg == previous) return
        database.withTransaction {
            definitionDao.listBodyweightExercises().forEach { definition ->
                logWeight(definition.name, weightKg + (definition.weightKg ?: 0.0))
            }
        }
    }

    suspend fun setHiddenTrackingNames(names: Set<String>) =
        settingsStore.setHiddenTrackingNames(names)

    /**
     * Löscht eine Übung restlos: aus allen Trainingstagen, aus der Übungsdatenbank und
     * samt Gewichtsverlauf. Das lässt sich nicht rückgängig machen.
     */
    suspend fun deleteExerciseEverywhere(name: String) = deleteExercisesEverywhere(listOf(name))

    /**
     * Dasselbe für mehrere Übungen auf einmal – alles in einer Transaktion, damit nicht die
     * halbe Auswahl verschwindet, wenn etwas dazwischenkommt.
     */
    suspend fun deleteExercisesEverywhere(names: Collection<String>) {
        if (names.isEmpty()) return
        database.withTransaction {
            val affectedDays = mutableSetOf<Int>()
            names.forEach { name ->
                affectedDays += exerciseDao.listDayIdsForName(name)
                exerciseDao.deleteByName(name)
                definitionDao.deleteByName(name)
                weightLogDao.deleteByName(name)
            }
            affectedDays.forEach { normalizeSupersets(it) }
        }
        // Sonst blieben die Namen im Tracking ausgeblendet und später neu angelegte
        // Übungen gleichen Namens wären von Anfang an unsichtbar.
        val hidden = settingsStore.hiddenTrackingNames.first()
        val remaining = hidden - names.toSet()
        if (remaining.size != hidden.size) settingsStore.setHiddenTrackingNames(remaining)
    }

    /**
     * Schaltet beim ersten Start an einem neuen Kalendertag auf den Tag nach dem zuletzt
     * abgehakten weiter – wer gestern Tag 1 gemacht hat, sieht heute Tag 2.
     *
     * Läuft höchstens einmal pro Tag, damit eine Auswahl von Hand nicht wieder umspringt.
     */
    suspend fun advanceDayIfNewDate(today: LocalDate = LocalDate.now()) {
        val epochDay = today.toEpochDay()
        if (settingsStore.lastDayAdvance() >= epochDay) return
        settingsStore.setLastDayAdvance(epochDay)

        val latest = sessionDao.latest() ?: return
        // Am Tag des Trainings selbst bleibt die Ansicht stehen.
        if (!latest.completedAt.toLocalDate().isBefore(today)) return
        settingsStore.setSelectedDayId(nextDayId(latest.dayId, settingsStore.dayCount.first()))
    }

    suspend fun selectDay(dayId: Int) = settingsStore.setSelectedDayId(dayId)

    suspend fun findExercise(id: Long): ExerciseItem? = exerciseDao.findById(id)

    /**
     * Legt eine Übung an oder aktualisiert sie.
     *
     * [weightKg] und [progressionStepKg] landen in der gemeinsamen Definition und gelten damit
     * an *allen* Tagen, an denen [name] vorkommt. Sätze, Wiederholungen und [variation] bleiben
     * bei dieser einen Zeile. Ein geändertes Gewicht wandert zusätzlich in den Verlauf.
     */
    suspend fun saveExercise(
        id: Long?,
        dayId: Int,
        name: String,
        variation: String?,
        weightKg: Double?,
        sets: Int?,
        repsMin: Int?,
        repsMax: Int?,
        progressionStepKg: Double,
        usesBodyweight: Boolean
    ) {
        // Vor der Transaktion lesen: Die Einstellungen liegen in einer eigenen Datei, und
        // solange darauf gewartet wird, bliebe die Schreibsperre der Datenbank unnötig offen.
        val bodyweight = settingsStore.bodyweightKg.first()

        database.withTransaction {
            // Zuerst prüfen, ob es die zu ändernde Zeile überhaupt noch gibt – sonst bliebe
            // beim Abbruch eine schon geschriebene Definition samt Verlaufseintrag zurück.
            val existing = id?.let { exerciseDao.findEntityById(it) }
            if (id != null && existing == null) return@withTransaction

            val previous = definitionDao.find(name)
            definitionDao.upsert(
                ExerciseDefinition(
                    name = name,
                    weightKg = weightKg,
                    progressionStepKg = progressionStepKg,
                    usesBodyweight = usesBodyweight
                )
            )
            // Auch das Umschalten auf Körpergewicht ändert die tatsächliche Last.
            val newEffective = effectiveWeightKg(weightKg, usesBodyweight, bodyweight)
            val oldEffective = previous?.let {
                effectiveWeightKg(it.weightKg, it.usesBodyweight, bodyweight)
            }
            if (newEffective != null && newEffective != oldEffective) logWeight(name, newEffective)

            if (existing == null) {
                exerciseDao.insert(
                    Exercise(
                        dayId = dayId,
                        name = name,
                        variation = variation,
                        sets = sets,
                        repsMin = repsMin,
                        repsMax = repsMax,
                        position = exerciseDao.nextPosition(dayId)
                    )
                )
            } else {
                exerciseDao.update(
                    existing.copy(
                        name = name,
                        variation = variation,
                        sets = sets,
                        repsMin = repsMin,
                        repsMax = repsMax
                    )
                )
            }
            // Ein umbenannter letzter Eintrag kann die alte Definition verwaist zurücklassen.
            definitionDao.deleteOrphans()
        }
    }

    /**
     * Setzt die eingetragene Last der Übung – an jedem Tag, an dem sie vorkommt. In den
     * Verlauf wandert das tatsächliche Gewicht, bei Körpergewichtsübungen also samt Körpergewicht.
     */
    suspend fun setWeight(name: String, weightKg: Double) {
        val bodyweight = settingsStore.bodyweightKg.first()
        database.withTransaction {
            definitionDao.updateWeight(name, weightKg)
            val definition = definitionDao.find(name)
            val effective = effectiveWeightKg(
                weightKg = weightKg,
                usesBodyweight = definition?.usesBodyweight == true,
                bodyweightKg = bodyweight
            )
            if (effective != null) logWeight(name, effective)
        }
    }

    /**
     * Nimmt eine Erhöhung zurück: Das Gewicht geht auf den alten Wert und der eben
     * geschriebene Verlaufseintrag verschwindet wieder – sonst zeigte der Graph
     * einen Ausschlag nach oben und sofort wieder zurück.
     */
    suspend fun revertWeight(name: String, weightKg: Double?) = database.withTransaction {
        definitionDao.updateWeight(name, weightKg)
        weightLogDao.deleteLatest(name)
    }

    /**
     * Löscht die Zeilen. War es die letzte Zeile mit einem Namen, verschwindet die Übung
     * auch aus der Datenbank und damit aus den Vorschlägen. Der Gewichtsverlauf bleibt
     * erhalten – er ist die wertvollste Information und wäre sonst unwiederbringlich weg.
     */
    suspend fun deleteExercises(items: List<ExerciseItem>) = database.withTransaction {
        exerciseDao.deleteByIds(items.map { it.id })
        definitionDao.deleteOrphans()
        items.map { it.dayId }.distinct().forEach { normalizeSupersets(it) }
    }

    /** Stellt gelöschte Übungen mit ihrer ursprünglichen id, Position und Gewicht wieder her. */
    suspend fun restoreExercises(items: List<ExerciseItem>) = database.withTransaction {
        items.forEach { item ->
            definitionDao.upsert(item.toDefinition())
            exerciseDao.insert(item.toExercise())
        }
        items.map { it.dayId }.distinct().forEach { normalizeSupersets(it) }
    }

    /**
     * Schreibt die Reihenfolge nach dem Umsortieren zurück: [orderedIds] enthält die Übungen
     * eines Tages in der gewünschten Reihenfolge, die Positionen werden auf 0..n-1 normalisiert.
     * Alles in einer Transaktion, damit die Liste nie in einem halb sortierten Zustand auftaucht.
     */
    suspend fun reorderExercises(dayId: Int, orderedIds: List<Long>) = database.withTransaction {
        orderedIds.forEachIndexed { index, id -> exerciseDao.updatePosition(id, index) }
        normalizeSupersets(dayId)
    }

    /**
     * Fasst die ausgewählten Übungen zu einem Superset zusammen. Sie rücken dafür an die
     * Position der obersten Ausgewählten zusammen, denn ein Superset ist nur als
     * zusammenhängender Block sinnvoll.
     */
    suspend fun createSuperset(dayId: Int, ids: Set<Long>) = database.withTransaction {
        val ordered = exerciseDao.listByDay(dayId)
        val selected = ordered.filter { it.id in ids }
        if (selected.size < MIN_SUPERSET_SIZE) return@withTransaction

        val supersetId = exerciseDao.nextSupersetId()
        // Alle Einträge vor der ersten Ausgewählten sind nicht ausgewählt, deshalb passt
        // dieser Index auch in der Liste ohne die ausgewählten Einträge.
        val insertAt = ordered.indexOfFirst { it.id in ids }
        val rearranged = ordered.filterNot { it.id in ids }.toMutableList()
        rearranged.addAll(insertAt, selected)

        rearranged.forEachIndexed { index, exercise -> exerciseDao.updatePosition(exercise.id, index) }
        selected.forEach { exerciseDao.updateSuperset(it.id, supersetId) }
        normalizeSupersets(dayId)
    }

    /** Hebt die Superset-Zugehörigkeit der ausgewählten Übungen auf. */
    suspend fun dissolveSuperset(dayId: Int, ids: Set<Long>) = database.withTransaction {
        ids.forEach { exerciseDao.updateSuperset(it, null) }
        normalizeSupersets(dayId)
    }

    /**
     * Räumt Supersets auf, nachdem sich die Reihenfolge geändert hat. Welche Mitglieder
     * zusammenbleiben, entscheidet [survivingSupersetMembers]; hier wird das Ergebnis nur noch
     * in die Datenbank geschrieben.
     */
    private suspend fun normalizeSupersets(dayId: Int) {
        val ordered = exerciseDao.listByDay(dayId)
        val surviving = survivingSupersetMembers(
            orderedIds = ordered.map { it.id },
            supersetIds = ordered.map { it.supersetId }
        )
        ordered.filter { it.supersetId != null && it.id !in surviving }
            .forEach { exerciseDao.updateSuperset(it.id, null) }
    }

    private suspend fun logWeight(name: String, weightKg: Double) {
        weightLogDao.insert(
            WeightLog(
                exerciseName = name,
                weightKg = weightKg,
                recordedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Setzt die App auf den Zustand direkt nach der Installation zurück.
     *
     * Weg sind: der Verlauf abgehakter Trainings, der komplette Gewichtsverlauf, alle Übungen
     * samt ihrer geteilten Werte, die Namen der Trainingstage und sämtliche Einstellungen –
     * also alles, was die App je über das Training gesammelt hat.
     *
     * Übrig bleiben die leeren Trainingstage, genau wie nach der Installation. Das lässt sich
     * nicht rückgängig machen.
     */
    suspend fun deleteAllData() {
        database.withTransaction {
            weightLogDao.deleteAll()
            sessionDao.deleteAll()
            exerciseDao.deleteAll()
            definitionDao.deleteAll()
            dayDao.deleteAll()
        }
        settingsStore.clear()
        ensureSeeded()
    }

    /**
     * Legt die Trainingstage an, falls sie fehlen.
     *
     * Mehr passiert beim ersten Start bewusst nicht: Die App beginnt leer. Ein mitgelieferter
     * Plan wäre fremdes Training – die Übungen trägt jeder selbst ein.
     */
    suspend fun ensureSeeded() {
        ensureDaysExist(settingsStore.dayCount.first())
    }
}
