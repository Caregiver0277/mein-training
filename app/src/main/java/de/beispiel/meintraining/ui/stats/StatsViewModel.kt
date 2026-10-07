package de.beispiel.meintraining.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CardioTotals
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.DEFAULT_WEEKLY_GOAL
import de.beispiel.meintraining.util.DurationSummary
import de.beispiel.meintraining.util.Heatmap
import de.beispiel.meintraining.util.MilestoneData
import de.beispiel.meintraining.util.MilestoneOverview
import de.beispiel.meintraining.util.RotationEntry
import de.beispiel.meintraining.util.RotationSummary
import de.beispiel.meintraining.util.SessionTimes
import de.beispiel.meintraining.util.StagnatingExercise
import de.beispiel.meintraining.util.WeekCount
import de.beispiel.meintraining.util.WeightForecast
import de.beispiel.meintraining.util.cardioProgress
import de.beispiel.meintraining.util.cardioTotals
import de.beispiel.meintraining.util.currentWeeklyStreak
import de.beispiel.meintraining.util.durationSummary
import de.beispiel.meintraining.util.currentStrengthWeights
import de.beispiel.meintraining.util.exerciseGains
import de.beispiel.meintraining.util.heatmap
import de.beispiel.meintraining.util.longestWeeklyStreak
import de.beispiel.meintraining.util.milestones
import de.beispiel.meintraining.util.repsStillRising
import de.beispiel.meintraining.util.rotationSummary
import de.beispiel.meintraining.util.sessionsPerWeek
import de.beispiel.meintraining.util.stagnatingExercises
import de.beispiel.meintraining.util.toLocalDate
import de.beispiel.meintraining.util.typicalTimeOfDay
import de.beispiel.meintraining.util.weekdayDistribution
import de.beispiel.meintraining.util.weeklyCounts
import de.beispiel.meintraining.util.weightForecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Zahlen für den Statistik-Bereich. */
data class StatsUiState(
    val totalSessions: Int = 0,
    val sessionsPerWeek: Double = 0.0,
    /** Ziel-Serie: Wochen in Folge mit erreichtem [weeklyGoal] – siehe `currentWeeklyStreak`. */
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weeklyGoal: Int = DEFAULT_WEEKLY_GOAL,
    /** Trainings je Woche für die Balken des Wochenziels, die laufende Woche zuletzt. */
    val goalWeeks: List<WeekCount> = emptyList(),
    val firstSession: LocalDate? = null,
    /** Der Kalender der letzten zwölf Monate; `null` nur vor dem ersten Ausrechnen. */
    val heatmap: Heatmap? = null,
    /** Erreichte und nächste Meilensteine; `null` nur vor dem ersten Ausrechnen. */
    val milestones: MilestoneOverview? = null,
    /** Das „heute“ der Rechnung – für Datumsangaben, die das Jahr nur nennen, wenn es ein anderes ist. */
    val today: LocalDate = LocalDate.now(),
    /** Trainings je Wochentag, beginnend mit Montag. */
    val weekdayCounts: List<Int> = emptyList(),
    val typicalTime: LocalTime? = null,
    val totalGainKg: Double = 0.0,
    val stagnating: List<StagnatingExercise> = emptyList(),
    val exerciseCount: Int = 0,
    val heaviestExercise: Pair<String, Double>? = null,
    /** Ø Dauer gesamt und je Trainingstag; `null`, solange keine Dauer bekannt ist. */
    val duration: DurationSummary? = null,
    /** Minuten und Kilometer der Cardio-Einheiten; `null` ohne eine einzige. */
    val cardio: CardioTotals? = null,
    /**
     * Fortschritt je Übung im Plan, in der Reihenfolge des Plans: Kraftübungen mit Gewichtsverlauf
     * und Cardio-Übungen mit eingetragenen Einheiten.
     */
    val progress: List<ProgressEntry> = emptyList(),
    /** „Nächste Marken“: die Prognosen, die nächste zuerst. */
    val forecasts: List<WeightForecast> = emptyList(),
    /** Bilanz der abgeschlossenen Runden; `null`, solange keine abgeschlossen ist. */
    val rotations: RotationSummary? = null,
    /** Namen der Trainingstage für [duration] und [rotations] – auch der hinter einer verkürzten Runde. */
    val dayNames: Map<Int, String> = emptyMap()
) {
    val hasSessions: Boolean get() = totalSessions > 0
}

/**
 * [cardioUnitLabel] liefert „km/h“ und „Stufe“ für die Kurven auf der Detailseite einer
 * Cardio-Übung, die beide Einheiten kennt – wie im Tracking (siehe `cardioCurves`).
 */
class StatsViewModel(
    private val repository: TrainingRepository,
    private val currentDate: CurrentDate,
    private val cardioUnitLabel: (IntensityUnit) -> String = { it.name }
) : ViewModel() {

    /** Was vom Plan gerade läuft – und das Datum, an dem sich alle Zeitangaben ausrichten. */
    private data class PlanView(
        val today: LocalDate,
        val dayCount: Int,
        val hiddenExerciseNames: Set<String>,
        val dayNames: Map<Int, String>,
        val weeklyGoal: Int,
        /** Die Rundenschnitte – siehe `TrainingRepository.startNextRotation`. */
        val rotationCuts: List<Long>
    )

    val uiState = combine(
        repository.observeSessions(),
        // Was je Übung mitgeschrieben wird: Gewichtsverlauf, Satz-Protokoll, Cardio-Einheiten.
        combine(
            repository.observeWeightLogs(),
            repository.observeSetLogs(),
            repository.observeCardioLogs(),
            ::Triple
        ),
        repository.observeAllExercises(),
        repository.observeDefinitions(),
        combine(
            // Nicht `LocalDate.now()` mitten in der Rechnung: Der Wert fröre auf dem Tag ein, an
            // dem zuletzt etwas ausgesendet wurde – Streaks und „seit N Tagen“ blieben bei einer
            // über Nacht offen gebliebenen App auf gestern stehen.
            currentDate.flow,
            repository.dayCount,
            repository.hiddenExerciseNames,
            repository.observeDays(),
            combine(repository.weeklyGoal, repository.rotationCuts, ::Pair)
        ) { today, dayCount, hidden, days, (goal, cuts) ->
            PlanView(today, dayCount, hidden, days.associate { it.id to it.name }, goal, cuts)
        }
    ) { sessions, (logs, setLogs, cardioLogs), exercises, definitions, plan ->
        val today = plan.today
        val zone = ZoneId.systemDefault()
        val dates = sessions.map { it.completedAt.toLocalDate() }
        // Die Sitzungen kommen neueste zuerst, die Runden zählen in Eintragsreihenfolge.
        val rotationEntries = sessions.asReversed().mapIndexed { index, session ->
            RotationEntry(
                dayId = session.dayId,
                date = dates[sessions.lastIndex - index],
                completedAt = session.completedAt
            )
        }
        val times = sessions.map {
            Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalTime()
        }

        // Übungen, die an einem sichtbaren Tag stehen und nicht ausgeblendet sind. Eine
        // pausierte Übung soll nicht als „festgefahren“ auftauchen – sie ruht mit Absicht –, und
        // ein Tag hinter einer verkürzten Runde wird gerade gar nicht trainiert.
        val planned = exercises.filter {
            it.dayId <= plan.dayCount && it.name !in plan.hiddenExerciseNames
        }
        val plannedNames = planned.mapTo(HashSet()) { it.name }
        val decreasing = definitions.filter { it.progressionDown }.mapTo(HashSet()) { it.name }
        // Cardio bleibt bei Zuwachs, „Festgefahren“ und „Schwerste Übung“ außen vor – auch mit
        // einem Gewichtsverlauf aus der Zeit, als die Übung noch Kraft war.
        val cardio = definitions.filter { it.kind == ExerciseKind.CARDIO }.mapTo(HashSet()) { it.name }

        // Verlaufseinträge kommen älteste zuerst – genau die Reihenfolge, die der Zuwachs braucht.
        val gains = exerciseGains(
            logs.filter { it.exerciseName !in cardio }.map { it.exerciseName to it.weightKg },
            decreasing
        )
        val lastChanged = logs.groupBy { it.exerciseName }
            .mapValues { (_, entries) -> entries.maxOf { it.recordedAt } }
        val plannedDays = planned.groupBy({ it.name }, { it.dayId })
            .mapValues { (_, dayIds) -> dayIds.toSet() }
        val currentWeights = currentStrengthWeights(definitions, plannedNames)
        // Übungen mit Satz-Protokoll, die gerade über die Wiederholungen gesteigert werden.
        val setsByName = setLogs.groupBy { it.exerciseName }
        val stillProgressing = definitions.filter { it.logSets }.mapNotNullTo(HashSet()) { definition ->
            val weight = currentWeights[definition.name] ?: return@mapNotNullTo null
            val sets = setsByName[definition.name] ?: return@mapNotNullTo null
            val since = lastChanged[definition.name] ?: Long.MIN_VALUE
            definition.name.takeIf { repsStillRising(sets, weight, since, zone) }
        }

        // Fortschritt je Übung: jede Übung des Plans einmal, an ihrer ersten Stelle im Plan.
        val definitionsByName = definitions.associateBy { it.name }
        val logsByName = logs.groupBy { it.exerciseName }
        val cardioByExercise = cardioLogs.groupBy { it.exerciseName to it.variation }
        val progress = planned
            .sortedWith(compareBy({ it.dayId }, { it.position }))
            .map { ProgressKey(it.name, if (it.isCardio) it.variation else null, it.isCardio) }
            .distinct()
            .mapNotNull { key ->
                if (key.isCardio) {
                    val definition = definitionsByName[key.name] ?: return@mapNotNull null
                    val entries = cardioByExercise[key.name to key.variation].orEmpty()
                    cardioProgress(key.name, key.variation, entries, definition.cardio)
                        ?.let { ProgressEntry.Cardio(it) }
                } else {
                    strengthEntry(
                        name = key.name,
                        logsOldestFirst = logsByName[key.name].orEmpty(),
                        isDecreasing = key.name in decreasing,
                        today = today,
                        zone = zone
                    )
                }
            }
        // Nur Kraftübungen mit Pfeil nach oben – bei Pfeil nach unten gibt es keine Marke nach oben.
        val forecasts = progress.filterIsInstance<ProgressEntry.Strength>()
            .filterNot { it.progress.isDecreasing }
            .mapNotNull { weightForecast(it.progress.name, logsByName[it.progress.name].orEmpty(), today, zone) }
            .sortedBy { it.date }
        StatsUiState(
            totalSessions = sessions.size,
            sessionsPerWeek = sessionsPerWeek(dates, today),
            currentStreak = currentWeeklyStreak(dates, today, plan.weeklyGoal),
            longestStreak = longestWeeklyStreak(dates, plan.weeklyGoal),
            weeklyGoal = plan.weeklyGoal,
            goalWeeks = weeklyCounts(dates, today),
            firstSession = dates.minOrNull(),
            heatmap = heatmap(dates, today),
            milestones = milestones(
                MilestoneData(
                    sessions = sessions,
                    weightLogs = logs,
                    cardioLogs = cardioLogs,
                    definitions = definitions,
                    dayCount = plan.dayCount,
                    rotationCuts = plan.rotationCuts,
                    weeklyGoal = plan.weeklyGoal
                ),
                today,
                zone
            ),
            today = today,
            weekdayCounts = weekdayDistribution(dates),
            typicalTime = typicalTimeOfDay(times),
            totalGainKg = gains.sumOf { it.gainKg },
            stagnating = stagnatingExercises(
                lastChanged = lastChanged,
                currentWeights = currentWeights,
                plannedDays = plannedDays,
                sessions = sessions.map { it.dayId to it.completedAt },
                today = today,
                stillProgressing = stillProgressing
            ).take(TOP_ENTRIES),
            exerciseCount = planned.size,
            // Die Last einer Übung mit Pfeil nach unten ist Unterstützung, keine Last – die
            // schwerste Übung wäre sonst womöglich die, bei der am meisten geholfen wird.
            heaviestExercise = currentWeights.filterKeys { it !in decreasing }
                .maxByOrNull { it.value }?.toPair(),
            duration = durationSummary(
                sessions.map { SessionTimes(it.dayId, it.startedAt, it.completedAt) }
            ),
            cardio = cardioTotals(cardioLogs, today),
            progress = progress,
            forecasts = forecasts.take(TOP_ENTRIES),
            rotations = rotationSummary(rotationEntries, plan.dayCount, today, plan.rotationCuts),
            dayNames = plan.dayNames
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatsUiState()
    )

    /**
     * Die Detailseite einer Übung aus „Fortschritt je Übung“; `null`, solange nichts zu zeigen
     * ist. Ein eigener Fluss je Übung statt eines Teils von [uiState]: Gerechnet wird nur, solange
     * die Seite offen ist, und nur für diese eine Übung.
     */
    fun detail(key: ProgressKey): Flow<ExerciseDetail?> = combine(
        repository.observeWeightLogs(),
        repository.observeSetLogs(),
        repository.observeCardioLogs(),
        repository.observeDefinitions(),
        currentDate.flow
    ) { logs, setLogs, cardioLogs, definitions, today ->
        exerciseDetail(
            key = key,
            weightLogs = logs,
            setLogs = setLogs,
            cardioLogs = cardioLogs,
            definitions = definitions,
            today = today,
            now = System.currentTimeMillis(),
            cardioUnitLabel = cardioUnitLabel
        )
    }

    companion object {
        private const val TOP_ENTRIES = 5
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                StatsViewModel(app.repository, app.currentDate) { unit ->
                    app.getString(
                        when (unit) {
                            IntensityUnit.KMH -> R.string.cardio_unit_kmh
                            IntensityUnit.LEVEL -> R.string.cardio_unit_level
                        }
                    )
                }
            }
        }
    }
}
