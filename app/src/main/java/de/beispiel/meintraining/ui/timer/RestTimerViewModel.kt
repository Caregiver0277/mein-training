package de.beispiel.meintraining.ui.timer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.local.REST_TIMER_COUNT
import de.beispiel.meintraining.data.local.RestTimer
import de.beispiel.meintraining.data.local.RestTimerStore
import de.beispiel.meintraining.data.local.DEFAULT_REST_TIMER_SECONDS
import de.beispiel.meintraining.timer.RestTimerAlarm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bedient die beiden Pausenuhren.
 *
 * Der Zustand liegt vollständig im [RestTimerStore]; dieses ViewModel schaltet nur um und hält
 * den Wecker im System mit dem gespeicherten Endzeitpunkt gleich. Beides muss immer zusammen
 * passieren – ein Endzeitpunkt ohne Wecker klingelt nie, ein Wecker ohne Endzeitpunkt klingelt
 * zu einer Uhr, die längst zurückgesetzt wurde.
 */
class RestTimerViewModel(
    /** Die Application, nicht die Activity: Der Wecker wird beim System angemeldet. */
    private val context: Application,
    private val store: RestTimerStore
) : ViewModel() {

    val timers: StateFlow<List<RestTimer>> = store.timers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = List(REST_TIMER_COUNT) { index ->
            RestTimer(
                durationSeconds = DEFAULT_REST_TIMER_SECONDS.getOrElse(index) {
                    DEFAULT_REST_TIMER_SECONDS.last()
                }
            )
        }
    )

    /**
     * Fängt Wecker ab, die nie ankommen.
     *
     * Der Wecker liegt beim System und überlebt einiges nicht: Beim Neustart des Geräts
     * verfällt er, ein „Beenden erzwingen“ räumt ihn ebenso ab wie das Einspielen einer neuen
     * Fassung der App. Zurück bleibt ein Endzeitpunkt ohne Wecker – und weil nur der Empfänger
     * den Lauf zurücksetzt, stünde die Uhr danach für immer auf 0:00.
     *
     * Gewartet wird bis genau zu diesem Zeitpunkt, nicht getickt. Räumt der Empfänger wie
     * vorgesehen auf, sendet der Speicher einen neuen Stand und `collectLatest` bricht das
     * Warten ab; wer hier ankommt, weiß, dass der Wecker ausgeblieben ist. Aufgeräumt wird
     * dann genau wie sonst – nur ohne Vibrieren, denn das hätte längst kommen müssen.
     */
    init {
        viewModelScope.launch {
            store.timers.collectLatest { timers ->
                clearExpired(timers)
                val nextEnd = timers.mapNotNull { it.endAtMillis }
                    .filter { it > System.currentTimeMillis() }
                    .minOrNull() ?: return@collectLatest
                delay(nextEnd - System.currentTimeMillis() + ALARM_GRACE_MILLIS)
                clearExpired(store.timers.first())
            }
        }
    }

    private suspend fun clearExpired(timers: List<RestTimer>) {
        val now = System.currentTimeMillis()
        timers.forEachIndexed { index, timer ->
            val endAt = timer.endAtMillis ?: return@forEachIndexed
            if (endAt <= now) store.clearRun(index)
        }
    }

    /** Kurzer Druck auf den Knopf: starten, anhalten oder weiterlaufen lassen. */
    fun onToggle(index: Int) {
        viewModelScope.launch {
            val timer = store.timers.first().getOrNull(index) ?: return@launch
            if (timer.isRunning) {
                val remaining = timer.remainingSeconds(System.currentTimeMillis())
                RestTimerAlarm.cancel(context, index)
                // Genau auf 0 angehalten wäre nicht fortsetzbar – dann lieber gleich von vorn.
                if (remaining <= 0) store.clearRun(index) else store.setPaused(index, remaining)
            } else {
                val seconds = timer.pausedSeconds ?: timer.durationSeconds
                val endAt = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND
                store.setRunningUntil(index, endAt)
                RestTimerAlarm.schedule(context, index, endAt)
            }
        }
    }

    /** Langer Druck auf den Knopf: zurück auf die eingestellte Dauer. */
    fun onReset(index: Int) {
        viewModelScope.launch {
            RestTimerAlarm.cancel(context, index)
            store.clearRun(index)
        }
    }

    /** Langer Druck auf die Box: neue Dauer einstellen. */
    fun onDurationChange(index: Int, seconds: Int) {
        viewModelScope.launch {
            RestTimerAlarm.cancel(context, index)
            store.setDuration(index, seconds)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MILLIS_PER_SECOND = 1000L

        /**
         * Wartezeit, bevor ein ausgebliebener Wecker als ausgeblieben gilt. Der Empfänger
         * braucht selbst ein paar Millisekunden, bis sein Aufräumen im Speicher steht.
         */
        private const val ALARM_GRACE_MILLIS = 1_500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                RestTimerViewModel(app, app.restTimerStore)
            }
        }
    }
}
