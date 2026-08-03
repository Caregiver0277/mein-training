package de.beispiel.meintraining.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Das heutige Datum als beobachtbarer Zustand statt als Aufruf mitten in einer Berechnung.
 *
 * `LocalDate.now()` innerhalb eines Flusses friert auf dem Tag ein, an dem der Fluss zuletzt
 * etwas ausgesendet hat: Eine über Nacht offen gebliebene App zeigte dann weiter die Streaks,
 * Deload-Woche und „seit N Tagen“-Angaben von gestern. Weil alle Bereiche dasselbe „heute“
 * brauchen, liegt es an einer Stelle – [refresh] beim Zurückkehren in den Vordergrund bringt
 * Trainings-, Statistik- und Tracking-Ansicht gemeinsam auf den neuen Tag.
 */
class CurrentDate(private val clock: () -> LocalDate = { LocalDate.now() }) {

    private val state = MutableStateFlow(clock())

    val flow: StateFlow<LocalDate> = state.asStateFlow()

    val value: LocalDate get() = state.value

    /** Übernimmt einen inzwischen angebrochenen Kalendertag; sonst passiert nichts. */
    fun refresh() {
        val today = clock()
        if (state.value != today) state.value = today
    }
}
