package de.beispiel.meintraining.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Zustand des Verlaufs. */
data class HistoryUiState(
    /** Abgehakte Trainings, das jüngste zuerst. */
    val sessions: List<WorkoutSession> = emptyList(),
    /** Für die Namen der Trainingstage in den Einträgen. */
    val days: List<TrainingDay> = emptyList(),
    /** Kommt von außen, damit „heute“ auch nach Mitternacht noch heute ist. */
    val today: LocalDate = LocalDate.now()
)

/**
 * Eigener Zustand für den Verlauf, wie ihn Statistik und Tracking auch haben.
 *
 * Der komplette Sitzungsverlauf hing vorher am Zustand des Hauptscreens, obwohl der ihn nur
 * durchreichte: Jeder abgehakte Haken verglich beim Zusammenlegen die ganze Liste, und die
 * Daten lagen auch dann im Speicher, wenn der Verlauf gar nicht offen war. Hier wird er nur
 * abonniert, solange der Bereich sichtbar ist.
 */
class HistoryViewModel(
    private val repository: TrainingRepository,
    currentDate: CurrentDate
) : ViewModel() {

    val uiState = combine(
        repository.observeSessions(),
        repository.observeDays(),
        currentDate.flow
    ) { sessions, days, today ->
        HistoryUiState(sessions = sessions, days = days, today = today)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HistoryUiState()
    )

    /** Entfernt einen versehentlich abgehakten Eintrag aus dem Verlauf. */
    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                HistoryViewModel(app.repository, app.currentDate)
            }
        }
    }
}
