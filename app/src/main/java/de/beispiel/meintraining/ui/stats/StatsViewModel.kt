package de.beispiel.meintraining.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.DurationSummary
import de.beispiel.meintraining.util.Heatmap
import de.beispiel.meintraining.util.SessionTimes
import de.beispiel.meintraining.util.StagnatingExercise
import de.beispiel.meintraining.util.currentWeeklyStreak
import de.beispiel.meintraining.util.durationSummary
import de.beispiel.meintraining.util.currentStrengthWeights
import de.beispiel.meintraining.util.exerciseGains
import de.beispiel.meintraining.util.heatmap
import de.beispiel.meintraining.util.longestWeeklyStreak
import de.beispiel.meintraining.util.sessionsPerWeek
import de.beispiel.meintraining.util.stagnatingExercises
import de.beispiel.meintraining.util.toLocalDate
import de.beispiel.meintraining.util.typicalTimeOfDay
import de.beispiel.meintraining.util.weekdayDistribution
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
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val firstSession: LocalDate? = null,
    /** Der Kalender der letzten zwölf Monate; `null` nur vor dem ersten Ausrechnen. */
    val heatmap: Heatmap? = null,
    /** Trainings je Wochentag, beginnend mit Montag. */
    val weekdayCounts: List<Int> = emptyList(),
    val typicalTime: LocalTime? = null,
    val totalGainKg: Double = 0.0,
    val stagnating: List<StagnatingExercise> = emptyList(),
    val exerciseCount: Int = 0,
    val heaviestExercise: Pair<String, Double>? = null,
    /** Ø Dauer gesamt und je Trainingstag; `null`, solange keine Dauer bekannt ist. */
    val duration: DurationSummary? = null,
    /** Namen der Trainingstage für [duration] – auch der hinter einer verkürzten Runde. */
    val dayNames: Map<Int, String> = emptyMap()
) {
    val hasSessions: Boolean get() = totalSessions > 0
}

class StatsViewModel(repository: TrainingRepository, currentDate: CurrentDate) : ViewModel() {

    /** Was vom Plan gerade läuft – und das Datum, an dem sich alle Zeitangaben ausrichten. */
    private data class PlanView(
        val today: LocalDate,
        val dayCount: Int,
        val hiddenExerciseNames: Set<String>,
        val dayNames: Map<Int, String>
    )

    val uiState = combine(
        repository.observeSessions(),
        repository.observeWeightLogs(),
        repository.observeAllExercises(),
        repository.observeDefinitions(),
        combine(
            // Nicht `LocalDate.now()` mitten in der Rechnung: Der Wert fröre auf dem Tag ein, an
            // dem zuletzt etwas ausgesendet wurde – Streaks und „seit N Tagen“ blieben bei einer
            // über Nacht offen gebliebenen App auf gestern stehen.
            currentDate.flow,
            repository.dayCount,
            repository.hiddenExerciseNames,
            repository.observeDays()
        ) { today, dayCount, hidden, days ->
            PlanView(today, dayCount, hidden, days.associate { it.id to it.name })
        }
    ) { sessions, logs, exercises, definitions, plan ->
        val today = plan.today
        val zone = ZoneId.systemDefault()
        val dates = sessions.map { it.completedAt.toLocalDate() }
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
        StatsUiState(
            totalSessions = sessions.size,
            sessionsPerWeek = sessionsPerWeek(dates, today),
            currentStreak = currentWeeklyStreak(dates, today),
            longestStreak = longestWeeklyStreak(dates),
            firstSession = dates.minOrNull(),
            heatmap = heatmap(dates, today),
            weekdayCounts = weekdayDistribution(dates),
            typicalTime = typicalTimeOfDay(times),
            totalGainKg = gains.sumOf { it.gainKg },
            stagnating = stagnatingExercises(
                lastChanged = lastChanged,
                currentWeights = currentWeights,
                plannedDays = plannedDays,
                sessions = sessions.map { it.dayId to it.completedAt },
                today = today
            ).take(TOP_ENTRIES),
            exerciseCount = planned.size,
            // Die Last einer Übung mit Pfeil nach unten ist Unterstützung, keine Last – die
            // schwerste Übung wäre sonst womöglich die, bei der am meisten geholfen wird.
            heaviestExercise = currentWeights.filterKeys { it !in decreasing }
                .maxByOrNull { it.value }?.toPair(),
            duration = durationSummary(
                sessions.map { SessionTimes(it.dayId, it.startedAt, it.completedAt) }
            ),
            dayNames = plan.dayNames
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatsUiState()
    )

    companion object {
        private const val TOP_ENTRIES = 5
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                StatsViewModel(app.repository, app.currentDate)
            }
        }
    }
}
