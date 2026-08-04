package de.beispiel.meintraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.completedDaysInRotation
import de.beispiel.meintraining.util.deloadStatus
import de.beispiel.meintraining.util.parseOptionalDecimal
import de.beispiel.meintraining.util.parseOptionalInt
import de.beispiel.meintraining.util.parseProgressionStep
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toLocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    /**
     * Alle Übungen aller Tage, einmal abonniert und im Speicher nach Tag geschnitten.
     *
     * Eine eigene Abfrage je Tag baut bei jedem Umschalten eine neue Room-Abfrage auf und
     * verwirft die alte; hin und zurück kostet das drei Abfragen, jede mit Datenbankzugriff.
     * Bei höchstens sieben Tagen mit einer Handvoll Übungen ist der ganze Bestand so klein,
     * dass ein einziges Abonnement billiger ist – und das Umschalten damit ohne Datenbank
     * auskommt.
     *
     * `shareIn` statt `stateIn` aus demselben Grund wie bei [sessions]: Ein leerer Anfangswert
     * ließe die Liste beim Öffnen für einen Frame leer erscheinen.
     *
     * Geschnitten wird erst unten in [uiState], nicht hier in einem eigenen Zufluss. Hinge der
     * ausgewählte Tag an zwei Zweigen – einem für die Reiter, einem für die Liste –, käme jeder
     * für sich beim Zusammenlegen an: `combine` sendet bei *jeder* Änderung eines Zuflusses.
     * Ein Tippen ergäbe dann zwei Zustände nacheinander, den ersten mit dem neuen Tag und noch
     * der alten Liste. Genau das sieht man als Nachziehen der Übungen unter einem schon
     * umgesprungenen Reiter.
     */
    private val allExercises = repository.observeAllExercises()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

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
        repository.selectedDayId
    ) { days, id -> DayState(days, id) }

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

    private data class DayState(
        val days: List<TrainingDay>,
        val selectedDayId: Int
    )

    private data class Preferences(
        val dayCount: Int,
        val title: String,
        val today: LocalDate
    )

    private data class SessionSummary(
        val completedDayIds: Set<Int>,
        val deload: DeloadStatus
    )

    /**
     * Alles, was um die Übungsliste herum steht, in einem Wert.
     *
     * `combine` nimmt höchstens fünf Zuflüsse; ohne diese Zusammenfassung müssten Einstellungen
     * und Sitzungszusammenfassung als geschachtelte `Pair` durchgereicht und in der Kopfzeile
     * der Lambda wieder auseinandergenommen werden – jeder neue Wert hätte die Schachtelung
     * umgebaut.
     */
    private data class Surroundings(
        val dayCount: Int,
        val title: String,
        val today: LocalDate,
        val completedDayIds: Set<Int>,
        val deload: DeloadStatus
    )

    private val surroundings = combine(preferences, sessionSummary) { prefs, summary ->
        Surroundings(
            dayCount = prefs.dayCount,
            title = prefs.title,
            today = prefs.today,
            completedDayIds = summary.completedDayIds,
            deload = summary.deload
        )
    }

    val uiState = combine(
        dayState,
        allExercises,
        definitions,
        selectedIds,
        surroundings
    ) { day, all, definitionList, selection, around ->
        // Tag und zugehörige Liste entstehen hier gemeinsam aus *einer* Aussendung – nur so
        // springen Reiter und Übungen im selben Frame um.
        // `observeAll` sortiert bereits nach Tag, Position und id; das Filtern erhält das.
        val exerciseList = all.filter { it.dayId == day.selectedDayId }
        // Über die eingestellte Anzahl hinausgehende Tage bleiben in der Datenbank stehen,
        // werden aber nicht angezeigt – so ist eine verkürzte Runde jederzeit umkehrbar.
        val visibleDays = day.days.filter { it.id <= around.dayCount }
        TrainingUiState(
            days = visibleDays,
            selectedDayId = day.selectedDayId,
            exercises = exerciseList,
            knownExerciseNames = definitionList.map { it.name },
            // Zeilen, die inzwischen weg sind, dürfen nicht markiert bleiben. Ohne Auswahl
            // gibt es dafür nichts zu tun – der häufige Fall kostet so keine Zwischenmengen.
            selectedIds = if (selection.isEmpty()) {
                emptySet()
            } else {
                selection intersect exerciseList.mapTo(HashSet()) { it.id }
            },
            completedDayIds = around.completedDayIds,
            deload = around.deload,
            appTitle = around.title,
            today = around.today
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

    /**
     * Der Tag gilt sofort; gespeichert wird er nebenher.
     *
     * Liefe die Auswahl nur über die Coroutine, hinge das Aufleuchten des Reiters an einem
     * Schreibvorgang samt `fsync` – siehe [TrainingRepository.selectedDayId].
     */
    fun onDaySelected(dayId: Int) {
        selectedIds.value = emptySet()
        repository.selectDayNow(dayId)
        viewModelScope.launch { repository.selectDay(dayId) }
    }

    // --- Training abhaken --------------------------------------------------

    /**
     * Hakt das Training des aktuellen Tages ab – und beim zweiten Tippen wieder zurück.
     *
     * Der Haken ist damit sein eigenes „Rückgängig“. Das ersetzt die frühere Meldung am
     * unteren Rand, die sich genau über den Knopf schob und dessen Effekt verdeckte.
     * Pro Runde bleibt es bei einem Eintrag je Tag.
     */
    fun onToggleWorkoutCompleted() {
        viewModelScope.launch {
            // Der Tag kommt aus dem Repository, nicht aus dem angezeigten Zustand: Wer den
            // Reiter wechselt und sofort abhakt, träfe sonst noch das vorige Training.
            val dayId = repository.currentSelectedDay()
            if (dayId in uiState.value.completedDayIds) {
                repository.uncompleteWorkout(dayId)
            } else {
                repository.completeWorkout(dayId)
            }
        }
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
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.createSuperset(repository.currentSelectedDay(), selection)
        }
    }

    fun onDissolveSuperset() {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.dissolveSuperset(repository.currentSelectedDay(), selection)
        }
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
        viewModelScope.launch {
            repository.reorderExercises(repository.currentSelectedDay(), orderedIds)
        }
    }

    // --- Progression -------------------------------------------------------

    /**
     * Erhöht das Gewicht um den bei dieser Übung hinterlegten Schritt – an allen Tagen,
     * an denen sie vorkommt. Ohne gesetztes Gewicht öffnet sich stattdessen das Sheet.
     *
     * Gerechnet wird im Repository auf dem gespeicherten Stand; die Zeile entscheidet hier nur,
     * ob es überhaupt etwas zu erhöhen gibt – siehe [TrainingRepository.progressWeight].
     */
    fun onProgressClick(exercise: ExerciseItem) {
        if (exercise.weightKg == null) {
            onExerciseClick(exercise)
            return
        }
        viewModelScope.launch {
            val change = repository.progressWeight(exercise.name) ?: return@launch
            eventChannel.send(
                TrainingEvent.WeightIncreased(
                    exerciseName = exercise.name,
                    previousWeightKg = change.previousKg,
                    newWeightKg = change.newKg
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
