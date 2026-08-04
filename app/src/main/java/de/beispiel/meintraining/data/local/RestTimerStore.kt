package de.beispiel.meintraining.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Eigene Datei statt eines Ablegers der Einstellungen: Der Wecker-Empfänger schreibt hier
 * hinein, wenn eine Uhr abgelaufen ist. Er läuft, während die App geschlossen ist, und soll
 * dafür nicht den ganzen Einstellungsbestand aufziehen müssen.
 */
private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pausenuhren"
)

/** Anzahl der Pausenuhren nebeneinander über der Übungsliste. */
const val REST_TIMER_COUNT = 2

/** Kürzer als fünf Sekunden ist keine Pause, länger als eine Stunde keine mehr im Training. */
const val MIN_REST_TIMER_SECONDS = 5
const val MAX_REST_TIMER_SECONDS = 59 * 60 + 59

/** Vorgabe: kurz für Isolationsübungen, lang für schwere Grundübungen. */
val DEFAULT_REST_TIMER_SECONDS = listOf(90, 180)

const val SECONDS_PER_MINUTE = 60

/**
 * Eine Pausenuhr.
 *
 * Gespeichert wird nicht die Restzeit, sondern der Zeitpunkt, zu dem die Uhr klingelt. Nur so
 * stimmt die Anzeige noch, wenn die App zwischendurch weggelegt oder ihr Prozess vom System
 * beendet wurde – eine heruntergezählte Restzeit wäre dann stehen geblieben.
 */
data class RestTimer(
    val durationSeconds: Int,
    /** Wanduhrzeit des Ablaufs; `null`, wenn die Uhr gerade nicht läuft. */
    val endAtMillis: Long? = null,
    /** Restzeit im angehaltenen Zustand; `null`, wenn nicht angehalten. */
    val pausedMillis: Long? = null
) {
    val isRunning: Boolean get() = endAtMillis != null

    val isPaused: Boolean get() = endAtMillis == null && pausedMillis != null

    /**
     * Verbleibende Zeit in Millisekunden.
     *
     * Millisekunden und nicht Sekunden, weil zwei Dinge daran hängen, die feiner sind als die
     * Anzeige: der gleitende Balken und das Anhalten. Auf ganze Sekunden gerundet angehalten,
     * schöbe jedes Weiterlaufen die Pause um bis zu einer Sekunde nach hinten – und der Balken
     * spränge beim Weiterlaufen genau um diesen Rest nach rechts.
     */
    fun remainingMillis(nowMillis: Long): Long = when {
        endAtMillis != null -> (endAtMillis - nowMillis).coerceAtLeast(0L)
        pausedMillis != null -> pausedMillis
        else -> durationSeconds * MILLIS_PER_SECOND
    }

    /**
     * Verbleibende Sekunden. Aufgerundet, weil sonst direkt nach dem Start schon eine Sekunde
     * fehlte: Nach 1 ms wären von 90 s abgerundet bereits 89 übrig.
     */
    fun remainingSeconds(nowMillis: Long): Int =
        ((remainingMillis(nowMillis) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

    /** Anteil der Pause, der noch aussteht – der Füllstand des Balkens. */
    fun remainingFraction(nowMillis: Long): Float {
        if (durationSeconds <= 0) return 0f
        val total = durationSeconds * MILLIS_PER_SECOND
        return (remainingMillis(nowMillis).toFloat() / total).coerceIn(0f, 1f)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/** Dauer und Lauf-Zustand der Pausenuhren. */
class RestTimerStore(context: Context) {

    private val store = context.applicationContext.timerDataStore

    /** Wie in [SettingsStore]: Eine unlesbare Datei gibt Vorgabewerte, statt alles mitzureißen. */
    private val preferences: Flow<Preferences> = store.data.catch { throwable ->
        if (throwable is IOException) emit(emptyPreferences()) else throw throwable
    }

    val timers: Flow<List<RestTimer>> = preferences.map(::readTimers).distinctUntilChanged()

    private fun readTimers(prefs: Preferences): List<RestTimer> = List(REST_TIMER_COUNT) { index ->
        RestTimer(
            durationSeconds = (prefs[durationKey(index)] ?: defaultSeconds(index))
                .coerceIn(MIN_REST_TIMER_SECONDS, MAX_REST_TIMER_SECONDS),
            endAtMillis = prefs[endAtKey(index)],
            pausedMillis = prefs[pausedKey(index)]
        )
    }

    /** Eine neu eingestellte Dauer setzt die Uhr zurück – sonst liefe sie mit der alten weiter. */
    suspend fun setDuration(index: Int, seconds: Int) {
        store.edit { prefs ->
            prefs[durationKey(index)] =
                seconds.coerceIn(MIN_REST_TIMER_SECONDS, MAX_REST_TIMER_SECONDS)
            prefs.remove(endAtKey(index))
            prefs.remove(pausedKey(index))
        }
    }

    suspend fun setRunningUntil(index: Int, endAtMillis: Long) {
        store.edit { prefs ->
            prefs[endAtKey(index)] = endAtMillis
            prefs.remove(pausedKey(index))
        }
    }

    suspend fun setPaused(index: Int, remainingMillis: Long) {
        store.edit { prefs ->
            prefs.remove(endAtKey(index))
            prefs[pausedKey(index)] = remainingMillis.coerceAtLeast(0L)
        }
    }

    /** Zurück auf die eingestellte Dauer – nach dem Klingeln wie nach langem Druck auf den Knopf. */
    suspend fun clearRun(index: Int) {
        store.edit { prefs ->
            prefs.remove(endAtKey(index))
            prefs.remove(pausedKey(index))
        }
    }

    private companion object {
        fun defaultSeconds(index: Int): Int =
            DEFAULT_REST_TIMER_SECONDS.getOrElse(index) { DEFAULT_REST_TIMER_SECONDS.last() }

        fun durationKey(index: Int) = intPreferencesKey("timer_${index}_duration")
        fun endAtKey(index: Int) = longPreferencesKey("timer_${index}_end_at")

        /**
         * Eigener Name statt des früheren `timer_N_paused`: Dort liegen ganze Sekunden als Int,
         * und ein Long-Schlüssel auf denselben Namen läse einen alten Stand als ClassCastException
         * aus. Eine beim Umstieg angehaltene Uhr steht danach wieder auf ihrer vollen Dauer.
         */
        fun pausedKey(index: Int) = longPreferencesKey("timer_${index}_paused_millis")
    }
}
