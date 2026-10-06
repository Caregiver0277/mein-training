package de.beispiel.meintraining.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.util.CurrentDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Ein einzelner Verlaufseintrag, wie er in der Punktliste steht. */
data class TrackedPoint(
    val id: Long,
    val recordedAt: Long,
    val weightKg: Double
)

/** Zustand des Tracking-Screens. */
data class TrackingUiState(
    val range: TimeRange = TimeRange.TOTAL,
    val manualYear: Int = 0,
    /** Alle Übungen, für die es einen Verlauf gibt. */
    val trackedNames: List<String> = emptyList(),
    /** Davon die gerade angezeigten. */
    val visibleNames: Set<String> = emptySet(),
    val availableYears: List<Int> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
    /** Zeigt der Graph Prozent statt Kilogramm? Siehe [toPercentSeries]. */
    val isPercent: Boolean = false,
    val window: TimeWindow = TimeWindow(0, 0),
    val ticks: List<AxisTick> = emptyList(),
    val pickerOpen: Boolean = false,
    /** Übung, deren Datenpunkte gerade offen liegen; `null`, wenn keine Liste offen ist. */
    val pointsExercise: String? = null,
    /** Deren Punkte, jüngster zuerst – so steht der letzte Eintrag oben. */
    val points: List<TrackedPoint> = emptyList()
) {
    /** Sind alle bekannten Übungen sichtbar? Steuert den Umschalter im Auswahlfenster. */
    val allVisible: Boolean get() = trackedNames.isNotEmpty() && visibleNames.size == trackedNames.size

    /** Warum der Graph leer ist; `null`, solange er etwas zeigt. */
    val emptyReason: ChartEmptyReason?
        get() = when {
            series.any { it.points.isNotEmpty() } -> null
            // Kurven gibt es, aber keine mit Punkten: in Prozent lauter Übungen ab 0 kg.
            series.isNotEmpty() -> ChartEmptyReason.NO_PERCENT_BASE
            trackedNames.isEmpty() -> ChartEmptyReason.NOTHING_RECORDED
            visibleNames.isEmpty() -> ChartEmptyReason.NOTHING_SELECTED
            else -> ChartEmptyReason.NOTHING_IN_RANGE
        }
}

class TrackingViewModel(
    private val repository: TrainingRepository,
    private val currentDate: CurrentDate
) : ViewModel() {

    private val range = MutableStateFlow(TimeRange.TOTAL)
    private val manualYear = MutableStateFlow(currentDate.value.year)
    private val pickerOpen = MutableStateFlow(false)

    /** Übung, deren Punktliste offen ist. */
    private val pointsExercise = MutableStateFlow<String?>(null)

    /**
     * Gespeichert wird das Ausgeblendete, nicht das Sichtbare: So bleibt die Auswahl über
     * Neustarts erhalten und neu angelegte Übungen erscheinen trotzdem von selbst im Graphen.
     */
    private val hiddenNames = repository.hiddenTrackingNames

    /**
     * Einmal abgefragt und geteilt: Graph und Punktliste lesen denselben Verlauf.
     *
     * `shareIn` statt `stateIn`, weil letzteres einen Anfangswert braucht: Mit einer leeren
     * Liste als Start zeigte der Graph beim Öffnen kurz „keine Daten“, bevor die Kurven kommen.
     */
    private val logs = repository.observeWeightLogs()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** Die Übungen, deren letzter Stand bis heute weiterläuft – siehe [activeExerciseNames]. */
    private val activeNames = combine(
        repository.observeAllExercises(),
        repository.dayCount,
        repository.hiddenExerciseNames
    ) { exercises, dayCount, hidden -> activeExerciseNames(exercises, dayCount, hidden) }

    /** Übungen mit Pfeil nach unten: In Prozent zählt bei ihnen eine Senkung als Fortschritt. */
    private val decreasingNames = repository.observeDefinitions()
        .map { definitions -> definitions.filter { it.progressionDown }.map { it.name }.toSet() }
        .distinctUntilChanged()

    /**
     * Der Graph für sich, getrennt von den Fensterzuständen.
     *
     * Lägen Auswahlfenster und offene Punktliste im selben `combine`, würde jedes Öffnen und
     * jeder Haken sämtliche Kurven samt Zeitachse neu berechnen – Arbeit, die mit jedem
     * Trainingsjahr wächst, für eine Änderung, die den Graphen gar nicht betrifft.
     */
    private val kgChart = combine(
        logs,
        combine(range, manualYear) { range, year -> range to year },
        hiddenNames,
        activeNames,
        currentDate.flow
    ) { logList, (selectedRange, year), hidden, active, _ ->
        // Groß- und Kleinschreibung darf die Liste nicht auseinanderreißen.
        val trackedNames = logList.map { it.exerciseName }.distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val visibleNames = trackedNames.filterNot { hidden.contains(it) }.toSet()
        // Das Datum steuert nur, *wann* neu gerechnet wird; die Fensterkante braucht die
        // volle Genauigkeit und kommt deshalb weiterhin von der Uhr.
        val now = System.currentTimeMillis()
        val window = timeWindowFor(selectedRange, year, logList, now)

        ChartState(
            range = selectedRange,
            manualYear = year,
            trackedNames = trackedNames,
            visibleNames = visibleNames,
            availableYears = logList.map { it.recordedAt.year() }.distinct().sorted(),
            series = buildSeries(logList, visibleNames, window, now, active),
            window = window,
            ticks = buildTimeAxis(window)
        )
    }

    /**
     * Der Graph in der gewählten Einheit. Die Umrechnung in Prozent sitzt obendrauf und ist
     * billig: Das Umschalten rechnet die Kurven nicht neu aus dem Verlauf.
     */
    private val chart = combine(
        kgChart,
        repository.trackingPercent,
        decreasingNames
    ) { state, percent, decreasing ->
        if (percent) {
            state.copy(series = toPercentSeries(state.series, decreasing), isPercent = true)
        } else {
            state
        }
    }

    val uiState = combine(
        chart,
        logs,
        pickerOpen,
        pointsExercise
    ) { chartState, logList, isPickerOpen, openPoints ->
        TrackingUiState(
            range = chartState.range,
            manualYear = chartState.manualYear,
            trackedNames = chartState.trackedNames,
            visibleNames = chartState.visibleNames,
            availableYears = chartState.availableYears,
            series = chartState.series,
            isPercent = chartState.isPercent,
            window = chartState.window,
            ticks = chartState.ticks,
            pickerOpen = isPickerOpen,
            // Eine Übung, deren letzter Punkt eben gelöscht wurde, verschwindet aus der
            // Liste; die offene Ansicht schließt sich dann von selbst.
            pointsExercise = openPoints?.takeIf { it in chartState.trackedNames },
            points = logList.filter { it.exerciseName == openPoints }
                .sortedByDescending { it.recordedAt }
                .map { TrackedPoint(id = it.id, recordedAt = it.recordedAt, weightKg = it.weightKg) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TrackingUiState()
    )

    /** Alles, was allein am Verlauf und am gewählten Zeitraum hängt. */
    private data class ChartState(
        val range: TimeRange,
        val manualYear: Int,
        val trackedNames: List<String>,
        val visibleNames: Set<String>,
        val availableYears: List<Int>,
        val series: List<ChartSeries>,
        val window: TimeWindow,
        val ticks: List<AxisTick>,
        val isPercent: Boolean = false
    )

    fun onRangeSelected(newRange: TimeRange) {
        range.value = newRange
    }

    fun onManualYearSelected(year: Int) {
        manualYear.value = year
        range.value = TimeRange.MANUAL_YEAR
    }

    /** Umschalter „kg | %“; die Wahl bleibt über Neustarts erhalten. */
    fun onPercentSelected(percent: Boolean) {
        viewModelScope.launch { repository.setTrackingPercent(percent) }
    }

    fun onPickerOpen() {
        pickerOpen.value = true
    }

    fun onPickerDismiss() {
        pickerOpen.value = false
    }

    /** Langer Druck auf eine Übung: zeigt ihre Datenpunkte zum Nachsehen und Löschen. */
    fun onExerciseLongPressed(name: String) {
        pointsExercise.value = name
    }

    fun onPointsDismiss() {
        pointsExercise.value = null
    }

    /**
     * Löscht einen einzelnen Punkt aus dem Verlauf. Das eingetragene Gewicht der Übung bleibt,
     * wie es ist – gelöscht wird die Aufzeichnung, nicht der heutige Stand.
     */
    fun onDeletePoint(id: Long) {
        viewModelScope.launch { repository.deleteWeightLog(id) }
    }

    fun onExerciseToggled(name: String) {
        val state = uiState.value
        val hidden = state.trackedNames.filterNot { it in state.visibleNames }.toSet()
        val updated = if (name in hidden) hidden - name else hidden + name
        viewModelScope.launch { repository.setHiddenTrackingNames(updated) }
    }

    /** Ein Schalter für beides: alles anzeigen, oder – wenn schon alles sichtbar ist – nichts. */
    fun onToggleAll() {
        val state = uiState.value
        val updated = if (state.allVisible) state.trackedNames.toSet() else emptySet()
        viewModelScope.launch { repository.setHiddenTrackingNames(updated) }
    }

    private fun Long.year(): Int =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).year

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                TrackingViewModel(app.repository, app.currentDate)
            }
        }
    }
}
