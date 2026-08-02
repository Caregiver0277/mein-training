package de.beispiel.meintraining.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.repository.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

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
    val window: TimeWindow = TimeWindow(0, 0),
    val ticks: List<AxisTick> = emptyList(),
    val pickerOpen: Boolean = false
) {
    /** Sind alle bekannten Übungen sichtbar? Steuert den Umschalter im Auswahlfenster. */
    val allVisible: Boolean get() = trackedNames.isNotEmpty() && visibleNames.size == trackedNames.size
}

class TrackingViewModel(private val repository: TrainingRepository) : ViewModel() {

    private val range = MutableStateFlow(TimeRange.TOTAL)
    private val manualYear = MutableStateFlow(currentYear())
    private val pickerOpen = MutableStateFlow(false)

    /**
     * Gespeichert wird das Ausgeblendete, nicht das Sichtbare: So bleibt die Auswahl über
     * Neustarts erhalten und neu angelegte Übungen erscheinen trotzdem von selbst im Graphen.
     */
    private val hiddenNames = repository.hiddenTrackingNames

    val uiState = combine(
        repository.observeWeightLogs(),
        combine(range, manualYear) { range, year -> range to year },
        hiddenNames,
        pickerOpen
    ) { logs, (selectedRange, year), hidden, isPickerOpen ->
        // Groß- und Kleinschreibung darf die Liste nicht auseinanderreißen.
        val trackedNames = logs.map { it.exerciseName }.distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val visibleNames = trackedNames.filterNot { hidden.contains(it) }.toSet()
        val now = System.currentTimeMillis()
        val window = timeWindowFor(selectedRange, year, logs, now)

        TrackingUiState(
            range = selectedRange,
            manualYear = year,
            trackedNames = trackedNames,
            visibleNames = visibleNames,
            availableYears = logs.map { it.recordedAt.year() }.distinct().sorted(),
            series = buildSeries(logs, visibleNames, window, now),
            window = window,
            ticks = buildTimeAxis(window),
            pickerOpen = isPickerOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TrackingUiState()
    )

    fun onRangeSelected(newRange: TimeRange) {
        range.value = newRange
    }

    fun onManualYearSelected(year: Int) {
        manualYear.value = year
        range.value = TimeRange.MANUAL_YEAR
    }

    fun onPickerOpen() {
        pickerOpen.value = true
    }

    fun onPickerDismiss() {
        pickerOpen.value = false
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

    private fun currentYear(): Int = System.currentTimeMillis().year()

    private fun Long.year(): Int =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).year

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                TrackingViewModel(app.repository)
            }
        }
    }
}
