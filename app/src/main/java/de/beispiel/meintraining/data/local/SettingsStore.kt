package de.beispiel.meintraining.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.util.DEFAULT_DELOAD_CYCLE_WEEKS
import de.beispiel.meintraining.util.MAX_CYCLE_WEEKS
import de.beispiel.meintraining.util.MIN_CYCLE_WEEKS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "einstellungen")

/** Kleine Einstellungen, die nicht in die Datenbank gehören (aktuell nur der gewählte Tag). */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    /**
     * Eine beschädigte oder unlesbare Einstellungsdatei darf die App nicht mitreißen: Bei einem
     * Lesefehler gelten wieder die Vorgabewerte, statt dass jeder Sammler eine Ausnahme bekommt.
     */
    private val preferences: Flow<Preferences> = store.data.catch { throwable ->
        if (throwable is IOException) emit(emptyPreferences()) else throw throwable
    }

    /**
     * Ein einzelner Wert aus den Einstellungen.
     *
     * DataStore meldet jede Änderung *irgendeiner* Einstellung an alle Sammler, deshalb filtert
     * [distinctUntilChanged] alles weg, was diesen Wert gar nicht betrifft. Ohne das würde etwa
     * jeder Tastendruck im Titelfeld den ausgewählten Tag neu melden – und damit die
     * Übungsabfrage der Datenbank neu starten.
     */
    private fun <T> preference(read: (Preferences) -> T): Flow<T> =
        preferences.map(read).distinctUntilChanged()

    val selectedDayId: Flow<Int> = preference { prefs -> prefs[KEY_SELECTED_DAY] ?: FIRST_DAY_ID }

    suspend fun setSelectedDayId(dayId: Int) {
        store.edit { prefs -> prefs[KEY_SELECTED_DAY] = dayId }
    }

    /** Merkt sich, dass der Trainingsplan schon eingespielt wurde – er wird nur einmal geladen. */
    suspend fun isPlanImported(): Boolean = preferences.first()[KEY_PLAN_IMPORTED] ?: false

    suspend fun setPlanImported() {
        store.edit { prefs -> prefs[KEY_PLAN_IMPORTED] = true }
    }

    /**
     * Tag der letzten automatischen Weiterschaltung als Epochentag. So springt die App
     * höchstens einmal pro Kalendertag weiter und überschreibt keine Auswahl von Hand.
     */
    suspend fun lastDayAdvance(): Long = preferences.first()[KEY_LAST_DAY_ADVANCE] ?: 0L

    suspend fun setLastDayAdvance(epochDay: Long) {
        store.edit { prefs -> prefs[KEY_LAST_DAY_ADVANCE] = epochDay }
    }

    /**
     * Im Tracking ausgeblendete Übungen. Gespeichert wird das Ausgeblendete, nicht das
     * Sichtbare – so tauchen neu hinzukommende Übungen von selbst im Graphen auf.
     */
    val hiddenTrackingNames: Flow<Set<String>> = preference { prefs ->
        prefs[KEY_HIDDEN_TRACKING].orEmpty()
    }

    suspend fun setHiddenTrackingNames(names: Set<String>) {
        store.edit { prefs -> prefs[KEY_HIDDEN_TRACKING] = names }
    }

    /** Eigenes Körpergewicht in kg; `null`, solange nichts eingetragen ist. */
    val bodyweightKg: Flow<Double?> = preference { prefs -> prefs[KEY_BODYWEIGHT] }

    suspend fun setBodyweightKg(weightKg: Double?) {
        store.edit { prefs ->
            if (weightKg == null) prefs.remove(KEY_BODYWEIGHT) else prefs[KEY_BODYWEIGHT] = weightKg
        }
    }

    /** Länge eines Trainingsblocks in Wochen; die letzte Woche ist die Deload-Woche. */
    val deloadCycleWeeks: Flow<Int> = preference { prefs ->
        prefs[KEY_DELOAD_WEEKS] ?: DEFAULT_DELOAD_CYCLE_WEEKS
    }

    suspend fun setDeloadCycleWeeks(weeks: Int) {
        store.edit { prefs -> prefs[KEY_DELOAD_WEEKS] = weeks.coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS) }
    }

    /** Überschrift des Hauptscreens; leer heißt: Vorgabe aus den Textressourcen. */
    val appTitle: Flow<String> = preference { prefs -> prefs[KEY_APP_TITLE].orEmpty() }

    suspend fun setAppTitle(title: String) {
        store.edit { prefs -> prefs[KEY_APP_TITLE] = title }
    }

    private companion object {
        val KEY_SELECTED_DAY = intPreferencesKey("selected_day_id")
        val KEY_BODYWEIGHT = doublePreferencesKey("bodyweight_kg")
        val KEY_DELOAD_WEEKS = intPreferencesKey("deload_cycle_weeks")
        val KEY_APP_TITLE = stringPreferencesKey("app_title")
        val KEY_HIDDEN_TRACKING = stringSetPreferencesKey("hidden_tracking_names")
        val KEY_PLAN_IMPORTED = booleanPreferencesKey("plan_imported")
        val KEY_LAST_DAY_ADVANCE = longPreferencesKey("last_day_advance")
    }
}
