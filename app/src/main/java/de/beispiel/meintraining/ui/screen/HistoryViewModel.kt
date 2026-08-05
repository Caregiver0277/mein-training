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
    /**
     * Für die Namen der Trainingstage in den Einträgen – hier stehen auch die hinter der
     * eingestellten Rundenlänge verborgenen, sonst verlöre ein älterer Eintrag seinen Namen.
     */
    val days: List<TrainingDay> = emptyList(),
    /**
     * Die Tage, die zum Nachtragen zur Wahl stehen: nur die sichtbaren. Ein Eintrag auf einem
     * verborgenen Tag zählte in keiner Runde mit und wäre nirgends abzuhaken.
     */
    val selectableDays: List<TrainingDay> = emptyList(),
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
        repository.dayCount,
        currentDate.flow
    ) { sessions, days, dayCount, today ->
        HistoryUiState(
            sessions = sessions,
            days = days,
            selectableDays = days.filter { it.id <= dayCount },
            today = today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HistoryUiState()
    )

    /** Entfernt einen versehentlich abgehakten Eintrag aus dem Verlauf. */
    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }

    /**
     * Trägt ein vergessenes Training nach.
     *
     * Der Dialog lässt keinen Zeitpunkt in der Zukunft zu; die Prüfung steht hier trotzdem
     * noch einmal, weil ein solcher Eintrag Runde, Streak und Deload-Rechnung verstellte und
     * sich hinterher nur über den langen Druck auf die Zeile wieder loswerden ließe.
     */
    fun onAddSession(dayId: Int, completedAt: Long) {
        if (completedAt > System.currentTimeMillis()) return
        viewModelScope.launch { repository.addSession(dayId, completedAt) }
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
