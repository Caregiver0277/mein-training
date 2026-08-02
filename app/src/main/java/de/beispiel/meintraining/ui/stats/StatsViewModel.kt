package de.beispiel.meintraining.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.repository.TrainingRepository
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

class StatsViewModel(repository: TrainingRepository) : ViewModel() {

    val uiState = combine(
        repository.observeSessions(),
        repository.observeWeightLogs(),
        repository.observeAllExercises(),
        repository.observeDefinitions(),
        repository.bodyweightKg
    ) { sessions, logs, exercises, definitions, bodyweightKg ->
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dates = sessions.map { it.completedAt.toLocalDate() }
        val times = sessions.map {
            Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalTime()
        }

        // Verlaufseinträge kommen älteste zuerst – genau die Reihenfolge, die der Zuwachs braucht.
        val gains = exerciseGains(logs.map { it.exerciseName to it.weightKg })
        val lastChanged = logs.groupBy { it.exerciseName }
            .mapValues { (_, entries) -> entries.maxOf { it.recordedAt }.toLocalDate() }
        val currentWeights = definitions.mapNotNull { definition ->
            definition.weightKg?.let { definition.name to it }
        }.toMap()
        // Für „schwerste Übung“ zählt die tatsächliche Last: Bei Körpergewichtsübungen ist
        // der eingetragene Wert nur die Zusatzlast, sonst stünde ein Klimmzug bei 0 kg.
        val effectiveWeights = definitions.mapNotNull { definition ->
            val effective = when {
                !definition.usesBodyweight -> definition.weightKg
                bodyweightKg == null -> definition.weightKg
                else -> bodyweightKg + (definition.weightKg ?: 0.0)
            }
            effective?.let { definition.name to it }
        }.toMap()

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
            exerciseCount = exercises.size,
            heaviestExercise = effectiveWeights.maxByOrNull { it.value }?.toPair()
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
                StatsViewModel(app.repository)
            }
        }
    }
}
