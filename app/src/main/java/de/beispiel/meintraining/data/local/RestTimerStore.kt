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
    /** Restsekunden im angehaltenen Zustand; `null`, wenn nicht angehalten. */
    val pausedSeconds: Int? = null
) {
    val isRunning: Boolean get() = endAtMillis != null

    val isPaused: Boolean get() = endAtMillis == null && pausedSeconds != null

    /**
     * Verbleibende Sekunden. Aufgerundet, weil sonst direkt nach dem Start schon eine Sekunde
     * fehlte: Nach 1 ms wären von 90 s abgerundet bereits 89 übrig.
     */
    fun remainingSeconds(nowMillis: Long): Int = when {
        endAtMillis != null -> {
            val left = endAtMillis - nowMillis
            if (left <= 0L) 0 else ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
        }
        pausedSeconds != null -> pausedSeconds
        else -> durationSeconds
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
            pausedSeconds = prefs[pausedKey(index)]
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

    suspend fun setPaused(index: Int, remainingSeconds: Int) {
        store.edit { prefs ->
            prefs.remove(endAtKey(index))
            prefs[pausedKey(index)] = remainingSeconds.coerceAtLeast(0)
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
        fun pausedKey(index: Int) = intPreferencesKey("timer_${index}_paused")
    }
}
