package de.beispiel.meintraining.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.backup.BackupRepository
import de.beispiel.meintraining.data.model.DEFAULT_DAY_COUNT
import de.beispiel.meintraining.data.model.MAX_DAY_COUNT
import de.beispiel.meintraining.data.model.MIN_DAY_COUNT
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.DEFAULT_DELOAD_CYCLE_WEEKS
import de.beispiel.meintraining.util.MAX_CYCLE_WEEKS
import de.beispiel.meintraining.util.MIN_CYCLE_WEEKS
import de.beispiel.meintraining.util.parseOptionalDecimal
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Übung, wie sie in den Einstellungen zum Löschen angeboten wird. */
data class ManagedExercise(
    val name: String,
    /** An wie vielen Trainingstagen sie vorkommt; 0 heißt: nur noch im Verlauf. */
    val dayCount: Int,
    val historyEntries: Int
)

data class SettingsUiState(
    /** Nur die Tage der eingestellten Runde; darüber hinaus liegen sie still in der Datenbank. */
    val days: List<TrainingDay> = emptyList(),
    val dayCount: Int = DEFAULT_DAY_COUNT,
    val appTitle: String = "",
    val bodyweightKg: Double? = null,
    val deloadCycleWeeks: Int = DEFAULT_DELOAD_CYCLE_WEEKS,
    val exercises: List<ManagedExercise> = emptyList()
)

@OptIn(FlowPreview::class)
class SettingsViewModel(
    private val repository: TrainingRepository,
    /** Nur fürs Zurücksetzen: Die automatische Sicherung muss dabei mit abbestellt werden. */
    private val backups: BackupRepository
) : ViewModel() {

    /**
     * Tastendrücke landen nicht sofort in der Datenbank.
     *
     * Beim Körpergewicht ist das keine Frage der Sparsamkeit, sondern der Richtigkeit: Jede
     * Änderung schreibt allen Körpergewichtsübungen einen Verlaufseintrag. Ohne diese Bremse
     * hinterließe die Eingabe „74,5“ dauerhaft die Zwischenstände 7 kg und 74 kg im Graphen.
     */
    private val bodyweightInput = MutableStateFlow<String?>(null)
    private val titleInput = MutableStateFlow<String?>(null)

    /** Offene Umbenennungen je Tag – als Sammlung, damit zwei schnell nacheinander
     *  bearbeitete Tage sich nicht gegenseitig verdrängen. */
    private val dayNameInput = MutableStateFlow<Map<Int, String>>(emptyMap())

    val uiState = combine(
        repository.observeAllExercises(),
        repository.observeDefinitions(),
        repository.observeWeightLogs(),
        repository.observeDays(),
        combine(
            repository.appTitle,
            repository.bodyweightKg,
            repository.deloadCycleWeeks,
            repository.dayCount
        ) { title, bodyweight, weeks, dayCount ->
            GeneralSettings(title, bodyweight, weeks, dayCount)
        }
    ) { exercises, definitions, logs, days, general ->
        val historyCounts = logs.groupingBy { it.exerciseName }.eachCount()
        val dayCounts = exercises.groupBy { it.name }
            .mapValues { (_, entries) -> entries.map { it.dayId }.distinct().size }

        // Auch Übungen, die nur noch im Verlauf stehen, lassen sich hier endgültig entfernen.
        val names = (definitions.map { it.name } + historyCounts.keys).distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        SettingsUiState(
            days = days.filter { it.id <= general.dayCount },
            dayCount = general.dayCount,
            appTitle = general.title,
            bodyweightKg = general.bodyweightKg,
            deloadCycleWeeks = general.cycleWeeks,
            exercises = names.map { name ->
                ManagedExercise(
                    name = name,
                    dayCount = dayCounts[name] ?: 0,
                    historyEntries = historyCounts[name] ?: 0
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            bodyweightInput.filterNotNull().debounce(INPUT_DEBOUNCE_MILLIS).collect { input ->
                repository.setBodyweightKg(parseOptionalDecimal(input)?.takeIf { it > 0.0 })
            }
        }
        viewModelScope.launch {
            titleInput.filterNotNull().debounce(INPUT_DEBOUNCE_MILLIS).collect {
                repository.setAppTitle(it)
            }
        }
        viewModelScope.launch {
            // Ohne Aufräumen: Die Sammlung ist auf vier Tage begrenzt, und ein erneutes
            // Schreiben desselben Namens ändert nichts.
            dayNameInput.filter { it.isNotEmpty() }.debounce(INPUT_DEBOUNCE_MILLIS)
                .collect { pending ->
                    pending.forEach { (dayId, name) -> repository.renameDay(dayId, name) }
                }
        }
    }

    fun onDeleteExercise(name: String) {
        viewModelScope.launch { repository.deleteExerciseEverywhere(name) }
    }

    /**
     * Setzt die App vollständig zurück – siehe [TrainingRepository.deleteAllData].
     *
     * Zuerst die automatische Sicherung abbestellen, und zwar bevor die Einstellungen fallen:
     * Danach wäre die Zieldatei nicht mehr bekannt, der Zeitplan liefe bei WorkManager aber
     * weiter.
     */
    fun onDeleteAllData() {
        viewModelScope.launch {
            backups.disableAutoBackup()
            repository.deleteAllData()
        }
    }

    /** Löscht die ausgewählten Übungen in einem Rutsch. */
    fun onDeleteExercises(names: Set<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch { repository.deleteExercisesEverywhere(names) }
    }

    /**
     * Nimmt die Anzahl der Trainingstage nur im erlaubten Bereich an – aus demselben Grund
     * wie bei der Zykluslänge: Ein gekappter Wert käme sofort zurück ins Textfeld.
     */
    fun onDayCountChange(input: String) {
        val count = input.trim().toIntOrNull() ?: return
        if (count !in MIN_DAY_COUNT..MAX_DAY_COUNT) return
        viewModelScope.launch { repository.setDayCount(count) }
    }

    fun onRenameDay(dayId: Int, name: String) {
        dayNameInput.value = dayNameInput.value + (dayId to name)
    }

    fun onAppTitleChange(title: String) {
        titleInput.value = title
    }

    /** Leere oder unsinnige Eingaben löschen das Körpergewicht wieder. */
    fun onBodyweightChange(input: String) {
        bodyweightInput.value = input
    }

    /**
     * Nimmt die Zykluslänge nur an, wenn sie schon im erlaubten Bereich liegt.
     *
     * Würde stattdessen jede Eingabe zurechtgebogen, käme der gekappte Wert sofort ins
     * Textfeld zurück: Aus der angefangenen „1“ von „12“ würde eine „2“, und zweistellige
     * Längen ließen sich gar nicht mehr eintippen.
     */
    fun onDeloadCycleChange(input: String) {
        val weeks = input.trim().toIntOrNull() ?: return
        if (weeks !in MIN_CYCLE_WEEKS..MAX_CYCLE_WEEKS) return
        viewModelScope.launch { repository.setDeloadCycleWeeks(weeks) }
    }

    /** Die Werte aus den Einstellungen, gebündelt für den zusammengesetzten Fluss. */
    private data class GeneralSettings(
        val title: String,
        val bodyweightKg: Double?,
        val cycleWeeks: Int,
        val dayCount: Int
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val INPUT_DEBOUNCE_MILLIS = 400L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                SettingsViewModel(app.repository, app.backupRepository)
            }
        }
    }
}
