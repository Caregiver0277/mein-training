package de.beispiel.meintraining.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.StagnatingExercise
import de.beispiel.meintraining.util.currentWeeklyStreak
import de.beispiel.meintraining.util.exerciseGains
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
    /** Trainings je Wochentag, beginnend mit Montag. */
    val weekdayCounts: List<Int> = emptyList(),
    val typicalTime: LocalTime? = null,
    val totalGainKg: Double = 0.0,
    val stagnating: List<StagnatingExercise> = emptyList(),
    val exerciseCount: Int = 0,
    val heaviestExercise: Pair<String, Double>? = null
) {
    val hasSessions: Boolean get() = totalSessions > 0
}

class StatsViewModel(repository: TrainingRepository, currentDate: CurrentDate) : ViewModel() {

    /** Was vom Plan gerade läuft – und das Datum, an dem sich alle Zeitangaben ausrichten. */
    private data class PlanView(
        val today: LocalDate,
        val dayCount: Int,
        val hiddenExerciseNames: Set<String>
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
            ::PlanView
        )
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

        // Verlaufseinträge kommen älteste zuerst – genau die Reihenfolge, die der Zuwachs braucht.
        val gains = exerciseGains(logs.map { it.exerciseName to it.weightKg }, decreasing)
        val lastChanged = logs.groupBy { it.exerciseName }
            .mapValues { (_, entries) -> entries.maxOf { it.recordedAt }.toLocalDate() }
        val currentWeights = definitions
            .filter { it.name in plannedNames }
            .mapNotNull { definition -> definition.weightKg?.let { definition.name to it } }
            .toMap()
        StatsUiState(
            totalSessions = sessions.size,
            sessionsPerWeek = sessionsPerWeek(dates, today),
            currentStreak = currentWeeklyStreak(dates, today),
            longestStreak = longestWeeklyStreak(dates),
            firstSession = dates.minOrNull(),
            weekdayCounts = weekdayDistribution(dates),
            typicalTime = typicalTimeOfDay(times),
            totalGainKg = gains.sumOf { it.gainKg },
            stagnating = stagnatingExercises(lastChanged, currentWeights, today).take(TOP_ENTRIES),
            exerciseCount = planned.size,
            // Die Last einer Übung mit Pfeil nach unten ist Unterstützung, keine Last – die
            // schwerste Übung wäre sonst womöglich die, bei der am meisten geholfen wird.
            heaviestExercise = currentWeights.filterKeys { it !in decreasing }
                .maxByOrNull { it.value }?.toPair()
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
