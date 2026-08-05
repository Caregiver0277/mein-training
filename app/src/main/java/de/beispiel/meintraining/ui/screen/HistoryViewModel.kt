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
import de.beispiel.meintraining.util.RotationEntry
import de.beispiel.meintraining.util.rotations
import de.beispiel.meintraining.util.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Ein abgehaktes Training im Verlauf.
 *
 * Das Datum steht mit dabei, statt bei jedem Zeichnen aus dem Zeitstempel gerechnet zu werden:
 * Aus einem Zeitstempel ein Datum zu machen kostet Zeitzone und Instant, und gebraucht wird es
 * für Überschrift, Abstand zu heute und die Rundenzuordnung mehrfach.
 */
data class HistoryEntry(val session: WorkoutSession, val date: LocalDate)

/**
 * Eine Runde im Verlauf samt ihren Trainings, jüngstes zuerst.
 *
 * Der Verlauf ist nach Runden geordnet, weil sich nur so nachsehen lässt, wo ein nachgetragenes
 * Training gelandet ist: Ein Eintrag zählt für die Runde, in deren Zeitraum er fällt, und nicht
 * für die laufende. Ohne diese Überschriften ist das eine Rechnung, die man der App glauben muss.
 */
data class HistoryCycle(
    /** Fortlaufend ab 1, älteste Runde zuerst. */
    val number: Int,
    val entries: List<HistoryEntry> = emptyList(),
    /** Wie viele der [dayCount] Trainingstage in dieser Runde abgehakt sind. */
    val completedDays: Int = 0,
    val dayCount: Int = 0,
    /** Die Runde, in der gerade trainiert wird – sie steht ganz oben. */
    val isCurrent: Boolean = false
)

/** Zustand des Verlaufs. */
data class HistoryUiState(
    /** Die Runden mit ihren Trainings, jüngste zuerst. */
    val cycles: List<HistoryCycle> = emptyList(),
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
        currentDate.flow,
        repository.rotationCuts
    ) { sessions, days, dayCount, today, cuts ->
        HistoryUiState(
            cycles = toCycles(sessions, dayCount, today, cuts),
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

        /**
         * Ordnet die Trainings ihren Runden zu – dieselbe Rechnung wie auf dem Hauptscreen, damit
         * hier keine zweite Vorstellung davon entsteht, was zu welcher Runde gehört.
         *
         * [sessionsNewestFirst] kommt so aus der Datenbank; gerechnet wird auf der umgedrehten
         * Liste, weil eine Runde in Eintragsreihenfolge entsteht. Herausgereicht wird wieder
         * neueste zuerst: Der Verlauf beginnt oben mit heute.
         */
        private fun toCycles(
            sessionsNewestFirst: List<WorkoutSession>,
            dayCount: Int,
            today: LocalDate,
            cuts: List<Long>
        ): List<HistoryCycle> {
            val entries = sessionsNewestFirst.asReversed().map { session ->
                HistoryEntry(session = session, date = session.completedAt.toLocalDate())
            }
            val rotations = rotations(
                entriesOldestFirst = entries.map {
                    RotationEntry(it.session.dayId, it.date, it.session.completedAt)
                },
                dayCount = dayCount,
                today = today,
                cuts = cuts
            )
            return rotations.mapIndexed { index, rotation ->
                HistoryCycle(
                    number = index + 1,
                    entries = rotation.entryIndices.map(entries::get).asReversed(),
                    completedDays = rotation.completedDayIds.size,
                    dayCount = dayCount,
                    // Die laufende Runde steht immer am Ende der Rechnung – auch wenn sie leer
                    // ist, weil eben erst eine neue begonnen hat.
                    isCurrent = index == rotations.lastIndex
                )
            }.asReversed()
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                HistoryViewModel(app.repository, app.currentDate)
            }
        }
    }
}
