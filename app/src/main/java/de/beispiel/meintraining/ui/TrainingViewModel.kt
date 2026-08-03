package de.beispiel.meintraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.completedDaysInRotation
import de.beispiel.meintraining.util.deloadStatus
import de.beispiel.meintraining.util.increaseWeight
import de.beispiel.meintraining.util.parseOptionalDecimal
import de.beispiel.meintraining.util.parseOptionalInt
import de.beispiel.meintraining.util.parseProgressionStep
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModel(
    private val repository: TrainingRepository,
    private val currentDate: CurrentDate
) : ViewModel() {

    private val formState = MutableStateFlow<ExerciseForm?>(null)

    /**
     * Das Bearbeiten-Sheet steht bewusst neben [uiState] statt darin.
     *
     * Es ändert sich bei jedem Tastendruck. Läge es im selben `combine`, liefe mit jedem
     * Buchstaben die ganze abgeleitete Rechnung erneut – Deload-Zyklus samt Sortieren aller
     * Trainingstermine, Rotation, Tagesliste – und der Hauptscreen würde dabei jedes Mal neu
     * zusammengesetzt, obwohl sich dort nichts geändert hat.
     */
    val editorForm: StateFlow<ExerciseForm?> = formState.asStateFlow()

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val eventChannel = Channel<TrainingEvent>(Channel.BUFFERED)
    val events: Flow<TrainingEvent> = eventChannel.receiveAsFlow()

    private val exercises = repository.selectedDayId.flatMapLatest { dayId ->
        repository.observeExercises(dayId)
    }

    // Dauerhaft aktiv, weil das Formular die geteilten Werte beim Tippen sofort braucht.
    private val definitions = repository.observeDefinitions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Einmal abgefragt und geteilt: Verlauf und Deload-Rechnung brauchen dieselben Sitzungen.
     *
     * `shareIn` statt `stateIn`, weil letzteres einen Anfangswert braucht: Mit einer leeren
     * Liste als Start liefe der Zustand einmal ohne Sitzungen durch, und die Haken an den
     * Trainingstagen blitzten beim Öffnen kurz als offen auf.
     */
    private val sessions = repository.observeSessions()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val dayState = combine(
        repository.observeDays(),
        repository.selectedDayId,
        sessions
    ) { days, id, sessionList -> Triple(days, id, sessionList) }

    private val preferences = combine(
        repository.dayCount,
        repository.appTitle,
        currentDate.flow
    ) { dayCount, title, today -> Preferences(dayCount, title, today) }

    /**
     * Der teure Teil, getrennt gehalten: Er hängt nur an den Sitzungen, der Rundenlänge, der
     * Blocklänge und dem Datum. `combine` merkt sich den letzten Wert eines Zuflusses, deshalb
     * rechnet das hier nicht mit, wenn anderswo nur eine Markierung umspringt.
     */
    private val sessionSummary = combine(
        sessions,
        repository.dayCount,
        repository.deloadCycleWeeks,
        currentDate.flow
    ) { sessionList, dayCount, cycleWeeks, today ->
        SessionSummary(
            // Die Sitzungen kommen neueste zuerst; die Rotation zählt in Eintragsreihenfolge.
            completedDayIds = completedDaysInRotation(
                dayIdsOldestFirst = sessionList.asReversed().map { it.dayId },
                dayCount = dayCount
            ),
            deload = deloadStatus(
                sessionDates = sessionList.map { it.completedAt.toLocalDate() },
                today = today,
                cycleWeeks = cycleWeeks
            )
        )
    }

    private data class Preferences(
        val dayCount: Int,
        val title: String,
        val today: LocalDate
    )

    private data class SessionSummary(
        val completedDayIds: Set<Int>,
        val deload: DeloadStatus
    )

    val uiState = combine(
        dayState,
        exercises,
        definitions,
        selectedIds,
        combine(preferences, sessionSummary) { prefs, summary -> prefs to summary }
    ) { (days, selectedDayId, sessionList),
        exerciseList,
        definitionList,
        selection,
        (prefs, summary) ->
        // Über die eingestellte Anzahl hinausgehende Tage bleiben in der Datenbank stehen,
        // werden aber nicht angezeigt – so ist eine verkürzte Runde jederzeit umkehrbar.
        val visibleDays = days.filter { it.id <= prefs.dayCount }
        TrainingUiState(
            days = visibleDays,
            selectedDayId = selectedDayId,
            exercises = exerciseList,
            knownExerciseNames = definitionList.map { it.name },
            // Zeilen, die inzwischen weg sind, dürfen nicht markiert bleiben.
            selectedIds = selection intersect exerciseList.map { it.id }.toSet(),
            sessions = sessionList,
            completedDayIds = summary.completedDayIds,
            deload = summary.deload,
            appTitle = prefs.title,
            today = prefs.today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TrainingUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            repository.advanceDayIfNewDate()
        }
    }

    /**
     * Beim Zurückkehren in den Vordergrund kann ein neuer Tag angebrochen sein – dann stimmen
     * Datumsangaben, Deload-Woche und der vorausgewählte Trainingstag nicht mehr.
     */
    fun onResumed() {
        currentDate.refresh()
        viewModelScope.launch { repository.advanceDayIfNewDate(currentDate.value) }
    }

    // --- Tag-Auswahl -------------------------------------------------------

    fun onDaySelected(dayId: Int) {
        selectedIds.value = emptySet()
        viewModelScope.launch { repository.selectDay(dayId) }
    }

    // --- Training abhaken --------------------------------------------------

    /**
     * Trägt das Training des aktuellen Tages in den Verlauf ein.
     *
     * Pro Runde geht das nur einmal je Tag. Erst „Rückgängig“ oder das Löschen im Verlauf
     * gibt den Haken wieder frei.
     */
    fun onCompleteWorkout() {
        val state = uiState.value
        if (state.isSelectedDayCompleted) return
        val dayId = state.selectedDayId
        viewModelScope.launch {
            val sessionId = repository.completeWorkout(dayId)
            eventChannel.send(TrainingEvent.WorkoutCompleted(sessionId))
        }
    }

    /** Entfernt einen versehentlich abgehakten Eintrag aus dem Verlauf. */
    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }

    // --- Mehrfachauswahl ---------------------------------------------------

    /** Langer Druck startet die Auswahl, im Auswahlmodus schaltet ein Tippen sie um. */
    fun onExerciseLongClick(exercise: ExerciseItem) {
        selectedIds.value = selectedIds.value + exercise.id
    }

    fun onSelectionToggle(exercise: ExerciseItem) {
        val current = selectedIds.value
        selectedIds.value = if (exercise.id in current) current - exercise.id else current + exercise.id
    }

    fun onSelectionClear() {
        selectedIds.value = emptySet()
    }

    fun onDeleteSelected() {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        val items = uiState.value.exercises.filter { it.id in selection }
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.deleteExercises(items)
            eventChannel.send(TrainingEvent.ExercisesDeleted(items))
        }
    }

    // --- Supersets ---------------------------------------------------------

    fun onCreateSuperset() {
        val selection = selectedIds.value
        if (selection.size < MIN_SUPERSET_SIZE) return
        val dayId = uiState.value.selectedDayId
        selectedIds.value = emptySet()
        viewModelScope.launch { repository.createSuperset(dayId, selection) }
    }

    fun onDissolveSuperset() {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        val dayId = uiState.value.selectedDayId
        selectedIds.value = emptySet()
        viewModelScope.launch { repository.dissolveSuperset(dayId, selection) }
    }

    // --- Bearbeiten-Sheet --------------------------------------------------

    /**
     * Der Tag wird beim Öffnen festgehalten, nicht erst beim Speichern nachgeschlagen: Die
     * Übung landet dort, wo der Nutzer sie angelegt hat – auch wenn die Auswahl inzwischen
     * weitergesprungen ist, etwa weil um Mitternacht ein neuer Tag angebrochen ist.
     */
    fun onAddClick() {
        viewModelScope.launch {
            formState.value = ExerciseForm(dayId = repository.selectedDayId.first())
        }
    }

    fun onExerciseClick(exercise: ExerciseItem) {
        formState.value = exercise.toForm()
    }

    /**
     * Sobald der eingetippte Name auf eine bekannte Übung passt, werden Gewicht und
     * Progressionsschritt übernommen – egal ob getippt oder aus der Vorschlagsliste gewählt.
     * Sätze und Wiederholungen bleiben bewusst unangetastet, die gehören zum jeweiligen Tag.
     */
    fun onFormChange(form: ExerciseForm) {
        val nameChanged = formState.value?.name != form.name
        formState.value = if (nameChanged) form.withSharedValues() else form
    }

    fun onVariationToggle() {
        val form = formState.value ?: return
        formState.value = if (form.showVariation) {
            form.copy(showVariation = false, variation = "")
        } else {
            form.copy(showVariation = true)
        }
    }

    fun onFormDismiss() {
        formState.value = null
    }

    fun onFormSave() {
        val form = formState.value ?: return
        if (!form.canSave) return

        viewModelScope.launch {
            val rawMin = parseOptionalInt(form.repsMin)
            val rawMax = parseOptionalInt(form.repsMax)
            // Vertauschte Grenzen still korrigieren
            val repsMin = if (rawMin != null && rawMax != null) minOf(rawMin, rawMax) else rawMin
            val repsMax = if (rawMin != null && rawMax != null) maxOf(rawMin, rawMax) else rawMax

            repository.saveExercise(
                id = form.id,
                dayId = form.dayId,
                name = form.name.trim(),
                variation = form.variation.trim().takeIf { form.showVariation && it.isNotEmpty() },
                weightKg = parseOptionalDecimal(form.weight),
                sets = parseOptionalInt(form.sets),
                repsMin = repsMin,
                repsMax = repsMax,
                progressionStepKg = parseProgressionStep(form.progressionStep)
            )
            formState.value = null
        }
    }

    /** Löschen aus dem geöffneten Sheet heraus. */
    fun onFormDelete() {
        val id = formState.value?.id ?: return
        formState.value = null
        viewModelScope.launch {
            val exercise = repository.findExercise(id) ?: return@launch
            val items = listOf(exercise)
            repository.deleteExercises(items)
            eventChannel.send(TrainingEvent.ExercisesDeleted(items))
        }
    }

    // --- Sortieren ---------------------------------------------------------

    /**
     * Übernimmt die per Drag-and-drop entstandene Reihenfolge. [orderedIds] sind die
     * Übungen des aktuellen Tages von oben nach unten; während des Ziehens sortiert die
     * Oberfläche nur ihre eigene Kopie, gespeichert wird erst beim Loslassen.
     */
    fun onReorder(orderedIds: List<Long>) {
        val dayId = uiState.value.selectedDayId
        viewModelScope.launch { repository.reorderExercises(dayId, orderedIds) }
    }

    // --- Progression -------------------------------------------------------

    /**
     * Erhöht das Gewicht um den bei dieser Übung hinterlegten Schritt – an allen Tagen,
     * an denen sie vorkommt. Ohne gesetztes Gewicht öffnet sich stattdessen das Sheet.
     */
    fun onProgressClick(exercise: ExerciseItem) {
        val current = exercise.weightKg
        if (current == null) {
            onExerciseClick(exercise)
            return
        }
        viewModelScope.launch {
            val newWeight = increaseWeight(current, exercise.progressionStepKg)
            repository.setWeight(exercise.name, newWeight)
            eventChannel.send(
                TrainingEvent.WeightIncreased(
                    exerciseName = exercise.name,
                    previousWeightKg = exercise.weightKg,
                    newWeightKg = newWeight
                )
            )
        }
    }

    // --- Rückgängig --------------------------------------------------------

    fun onUndo(event: TrainingEvent) {
        viewModelScope.launch {
            when (event) {
                is TrainingEvent.WeightIncreased ->
                    repository.revertWeight(event.exerciseName, event.previousWeightKg)
                is TrainingEvent.ExercisesDeleted ->
                    repository.restoreExercises(event.exercises)
                is TrainingEvent.WorkoutCompleted ->
                    repository.deleteSession(event.sessionId)
            }
        }
    }

    /**
     * Füllt Gewicht und Progressionsschritt aus der bekannten Übung, wenn der Name passt.
     * Die Schreibweise wird dabei auf die gespeicherte angeglichen – sonst entstünde aus
     * „bankdrücken“ eine zweite Übung neben „Bankdrücken“.
     */
    private fun ExerciseForm.withSharedValues(): ExerciseForm {
        val match = definitions.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: return this
        return copy(
            name = match.name,
            weight = match.weightKg?.toDecimalString().orEmpty(),
            progressionStep = match.progressionStepKg.toDecimalString()
        )
    }

    private fun ExerciseItem.toForm() = ExerciseForm(
        id = id,
        dayId = dayId,
        name = name,
        variation = variation.orEmpty(),
        showVariation = !variation.isNullOrBlank(),
        weight = weightKg?.toDecimalString().orEmpty(),
        sets = sets?.toString().orEmpty(),
        repsMin = repsMin?.toString().orEmpty(),
        repsMax = repsMax?.toString().orEmpty(),
        progressionStep = progressionStepKg.toDecimalString()
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                TrainingViewModel(app.repository, app.currentDate)
            }
        }
    }
}
