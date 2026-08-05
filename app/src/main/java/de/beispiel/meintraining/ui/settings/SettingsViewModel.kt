package de.beispiel.meintraining.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.backup.BackupRepository
import de.beispiel.meintraining.data.local.DEFAULT_TIMER_SOUND_VOLUME
import de.beispiel.meintraining.data.local.RestTimerStore
import de.beispiel.meintraining.data.model.DEFAULT_DAY_COUNT
import de.beispiel.meintraining.data.model.MAX_DAY_COUNT
import de.beispiel.meintraining.data.model.MIN_DAY_COUNT
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.timer.RestTimerSound
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

/** Eine Übung, wie sie in den Einstellungen zum Ausblenden und Löschen angeboten wird. */
data class ManagedExercise(
    val name: String,
    /** An wie vielen Trainingstagen sie vorkommt; 0 heißt: nur noch im Verlauf. */
    val dayCount: Int,
    val historyEntries: Int,
    /** Ausgeblendet heißt: Sie steht an keinem Trainingstag mehr in der Liste. */
    val isHidden: Boolean = false
)

data class SettingsUiState(
    /** Nur die Tage der eingestellten Runde; darüber hinaus liegen sie still in der Datenbank. */
    val days: List<TrainingDay> = emptyList(),
    val dayCount: Int = DEFAULT_DAY_COUNT,
    val appTitle: String = "",
    val deloadCycleWeeks: Int = DEFAULT_DELOAD_CYCLE_WEEKS,
    /** Klingt am Ende einer Pause ein Ton? Vibriert wird unabhängig davon immer. */
    val timerSoundEnabled: Boolean = true,
    /** Wie laut dieser Ton ist, 0 bis 1. */
    val timerSoundVolume: Float = DEFAULT_TIMER_SOUND_VOLUME,
    val exercises: List<ManagedExercise> = emptyList()
)

@OptIn(FlowPreview::class)
class SettingsViewModel(
    /**
     * Nur zum Vorspielen des Tons beim Einstellen der Lautstärke – siehe [onTimerVolumeChange].
     * Es ist der Anwendungskontext, der überlebt dieses ViewModel ohnehin.
     */
    private val appContext: Context,
    private val repository: TrainingRepository,
    /** Nur fürs Zurücksetzen: Die automatische Sicherung muss dabei mit abbestellt werden. */
    private val backups: BackupRepository,
    /**
     * Der Ton am Ende einer Pause wird hier umgeschaltet, liegt aber bei den Uhren: Der
     * Wecker-Empfänger muss ihn lesen, ohne die übrigen Einstellungen zu öffnen – siehe
     * [RestTimerStore.soundEnabled].
     */
    private val timers: RestTimerStore
) : ViewModel() {

    /**
     * Tastendrücke landen nicht sofort in der Datenbank – sonst schriebe jeder Buchstabe
     * der Überschrift einen eigenen Stand fest.
     *
     * Der Preis: Wer die App innerhalb der Wartezeit ganz verlässt, verliert den zuletzt
     * getippten Stand, weil mit der Activity auch dieser Bereich endet. Innerhalb der App ist
     * das ungefährlich – das ViewModel hängt an der Activity und überlebt den Weg zurück zum
     * Hauptscreen. Ein Nachreichen beim Beenden gibt es bewusst nicht: Es liefe auf einen
     * Schreibvorgang außerhalb jedes Gültigkeitsbereichs hinaus, und ein halber Titel ist
     * kein Verlust, der das rechtfertigt.
     */
    private val titleInput = MutableStateFlow<String?>(null)

    /** Offene Umbenennungen je Tag – als Sammlung, damit zwei schnell nacheinander
     *  bearbeitete Tage sich nicht gegenseitig verdrängen. */
    private val dayNameInput = MutableStateFlow<Map<Int, String>>(emptyMap())

    /**
     * Die Werte aus den Einstellungen und von den Uhren, gebündelt.
     *
     * Zwei Ebenen, weil `combine` höchstens fünf Zuflüsse nimmt und der Ton zwei davon braucht:
     * Schalter und Regler gehören zusammen und kommen deshalb als Paar herein.
     */
    private val general = combine(
        repository.appTitle,
        repository.deloadCycleWeeks,
        repository.dayCount,
        repository.hiddenExerciseNames,
        combine(timers.soundEnabled, timers.soundVolume) { enabled, volume ->
            TimerSound(enabled, volume)
        }
    ) { title, weeks, dayCount, hidden, sound ->
        GeneralSettings(title, weeks, dayCount, hidden, sound)
    }

    val uiState = combine(
        repository.observeAllExercises(),
        repository.observeDefinitions(),
        repository.observeWeightLogs(),
        repository.observeDays(),
        general
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
            deloadCycleWeeks = general.cycleWeeks,
            timerSoundEnabled = general.sound.enabled,
            timerSoundVolume = general.sound.volume,
            exercises = names.map { name ->
                ManagedExercise(
                    name = name,
                    dayCount = dayCounts[name] ?: 0,
                    historyEntries = historyCounts[name] ?: 0,
                    isHidden = name in general.hiddenExerciseNames
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

    /**
     * Schaltet den Ton am Ende einer Pause um.
     *
     * Ohne Zwischenspeichern der Eingabe wie bei den Textfeldern: Ein Schalter kippt einmal,
     * nicht bei jedem Tastendruck, und der Weg zurück in die Anzeige führt ohnehin über den
     * gespeicherten Wert.
     */
    fun onTimerSoundToggled(enabled: Boolean) {
        viewModelScope.launch { timers.setSoundEnabled(enabled) }
    }

    /**
     * Übernimmt die Lautstärke des Tons – und spielt ihn gleich einmal vor.
     *
     * Ohne das Vorspielen wäre der Regler nicht einzustellen: Man hört das Ergebnis erst am Ende
     * der nächsten Pause, also Minuten später und mitten im Training. Angemeldet wird der Ton
     * dabei als Wecker wie beim Klingeln selbst, damit hier genau das zu hören ist, was später
     * auch aus dem Gerät kommt.
     *
     * Erwartet wird ein losgelassener Regler, nicht jede Zwischenstellung: Sonst schriebe jede
     * Fingerbewegung in die Einstellungen und ließe einen Ton über dem vorigen anlaufen.
     */
    fun onTimerVolumeChange(volume: Float) {
        viewModelScope.launch {
            timers.setSoundVolume(volume)
            RestTimerSound.play(appContext, volume)
        }
    }

    /** Blendet eine Übung an allen Trainingstagen aus oder wieder ein. */
    fun onExerciseHiddenToggled(name: String, hidden: Boolean) {
        viewModelScope.launch { repository.setExerciseHidden(name, hidden) }
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
        val cycleWeeks: Int,
        val dayCount: Int,
        val hiddenExerciseNames: Set<String>,
        val sound: TimerSound
    )

    /** Schalter und Regler des Tons am Pausenende. */
    private data class TimerSound(val enabled: Boolean, val volume: Float)

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val INPUT_DEBOUNCE_MILLIS = 400L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                SettingsViewModel(
                    appContext = app,
                    repository = app.repository,
                    backups = app.backupRepository,
                    timers = app.restTimerStore
                )
            }
        }
    }
}
