package de.beispiel.meintraining.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.beispiel.meintraining.data.backup.DEFAULT_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.backup.MAX_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.backup.MIN_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.model.DEFAULT_DAY_COUNT
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.MAX_DAY_COUNT
import de.beispiel.meintraining.data.model.MIN_DAY_COUNT
import de.beispiel.meintraining.util.DEFAULT_DELOAD_CYCLE_WEEKS
import de.beispiel.meintraining.util.MAX_CYCLE_WEEKS
import de.beispiel.meintraining.util.MIN_CYCLE_WEEKS
import de.beispiel.meintraining.util.NO_ROTATION_CUT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "einstellungen")

/** Alle Einstellungen zu einem Zeitpunkt – siehe [SettingsStore.snapshot]. */
data class SettingsSnapshot(
    val appTitle: String,
    val deloadCycleWeeks: Int,
    val dayCount: Int,
    val selectedDayId: Int,
    val hiddenTrackingNames: Set<String>
)

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

    /**
     * Alle für die Sicherung nötigen Werte in einem Zug.
     *
     * Sonst würde jeder Wert einzeln abgefragt: sechs Lesevorgänge, zwischen die sich eine
     * Änderung schieben kann – die Sicherung enthielte dann einen Zustand, den es so nie gab.
     * Gelesen wird über dieselben Funktionen wie die Flüsse darunter, damit Vorgabewerte und
     * Grenzen nur an einer Stelle stehen.
     */
    suspend fun snapshot(): SettingsSnapshot = preferences.first().let { prefs ->
        SettingsSnapshot(
            appTitle = readAppTitle(prefs),
            deloadCycleWeeks = readDeloadWeeks(prefs),
            dayCount = readDayCount(prefs),
            selectedDayId = readSelectedDay(prefs),
            hiddenTrackingNames = readHiddenTracking(prefs)
        )
    }

    private fun readSelectedDay(prefs: Preferences): Int = prefs[KEY_SELECTED_DAY] ?: FIRST_DAY_ID

    val selectedDayId: Flow<Int> = preference(::readSelectedDay)

    suspend fun setSelectedDayId(dayId: Int) {
        store.edit { prefs -> prefs[KEY_SELECTED_DAY] = dayId }
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
     * Grenze der laufenden Runde als Zeitstempel: Nur Trainings *danach* zählen für sie mit.
     *
     * Sonst zählt die Runde einfach den Verlauf durch und beginnt von selbst von vorn, sobald
     * jeder Tag einmal dran war (siehe `completedDaysInRotation`). Wer die nächste Runde von
     * Hand beginnt, ohne den nächsten Kalendertag abzuwarten, braucht diesen Schnitt – sonst
     * stünden alle Tage weiter als erledigt da.
     *
     * 0 heißt: nie von Hand geschnitten, es zählt der ganze Verlauf.
     */
    private fun readRotationStartAfter(prefs: Preferences): Long =
        prefs[KEY_ROTATION_START_AFTER] ?: NO_ROTATION_CUT

    val rotationStartAfter: Flow<Long> = preference(::readRotationStartAfter)

    suspend fun setRotationStartAfter(timestamp: Long) {
        store.edit { prefs -> prefs[KEY_ROTATION_START_AFTER] = timestamp }
    }

    /**
     * Im Tracking ausgeblendete Übungen. Gespeichert wird das Ausgeblendete, nicht das
     * Sichtbare – so tauchen neu hinzukommende Übungen von selbst im Graphen auf.
     */
    private fun readHiddenTracking(prefs: Preferences): Set<String> =
        prefs[KEY_HIDDEN_TRACKING].orEmpty()

    val hiddenTrackingNames: Flow<Set<String>> = preference(::readHiddenTracking)

    suspend fun setHiddenTrackingNames(names: Set<String>) {
        store.edit { prefs -> prefs[KEY_HIDDEN_TRACKING] = names }
    }

    private fun readDeloadWeeks(prefs: Preferences): Int =
        prefs[KEY_DELOAD_WEEKS] ?: DEFAULT_DELOAD_CYCLE_WEEKS

    /** Länge eines Trainingsblocks in Wochen; die letzte Woche ist die Deload-Woche. */
    val deloadCycleWeeks: Flow<Int> = preference(::readDeloadWeeks)

    suspend fun setDeloadCycleWeeks(weeks: Int) {
        store.edit { prefs -> prefs[KEY_DELOAD_WEEKS] = weeks.coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS) }
    }

    private fun readDayCount(prefs: Preferences): Int =
        (prefs[KEY_DAY_COUNT] ?: DEFAULT_DAY_COUNT).coerceIn(MIN_DAY_COUNT, MAX_DAY_COUNT)

    /** Anzahl der Trainingstage in einer Runde. */
    val dayCount: Flow<Int> = preference(::readDayCount)

    suspend fun setDayCount(count: Int) {
        store.edit { prefs -> prefs[KEY_DAY_COUNT] = count.coerceIn(MIN_DAY_COUNT, MAX_DAY_COUNT) }
    }

    private fun readAppTitle(prefs: Preferences): String = prefs[KEY_APP_TITLE].orEmpty()

    /** Überschrift des Hauptscreens; leer heißt: Vorgabe aus den Textressourcen. */
    val appTitle: Flow<String> = preference(::readAppTitle)

    suspend fun setAppTitle(title: String) {
        store.edit { prefs -> prefs[KEY_APP_TITLE] = title }
    }

    // --- Sicherung ---------------------------------------------------------

    /**
     * Die gewählte Sicherungsdatei als URI-Text; `null`, solange keine ausgewählt wurde.
     * Die automatische Sicherung überschreibt genau diese Datei.
     */
    val backupTargetUri: Flow<String?> = preference { prefs -> prefs[KEY_BACKUP_URI] }

    suspend fun setBackupTargetUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(KEY_BACKUP_URI) else prefs[KEY_BACKUP_URI] = uri
        }
    }

    /** Abstand zwischen zwei automatischen Sicherungen in Tagen. */
    val backupIntervalDays: Flow<Int> = preference { prefs ->
        (prefs[KEY_BACKUP_INTERVAL] ?: DEFAULT_BACKUP_INTERVAL_DAYS)
            .coerceIn(MIN_BACKUP_INTERVAL_DAYS, MAX_BACKUP_INTERVAL_DAYS)
    }

    suspend fun setBackupIntervalDays(days: Int) {
        store.edit { prefs ->
            prefs[KEY_BACKUP_INTERVAL] =
                days.coerceIn(MIN_BACKUP_INTERVAL_DAYS, MAX_BACKUP_INTERVAL_DAYS)
        }
    }

    /** Ist die automatische Sicherung eingeschaltet? */
    val backupEnabled: Flow<Boolean> = preference { prefs -> prefs[KEY_BACKUP_ENABLED] ?: false }

    suspend fun setBackupEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_BACKUP_ENABLED] = enabled }
    }

    /**
     * Ergebnis der letzten automatischen Sicherung: Zeitpunkt und – falls sie scheiterte – der
     * Grund. Eine Sicherung, die still versagt, ist schlimmer als gar keine; deshalb wird das
     * Ergebnis festgehalten und angezeigt.
     */
    val lastBackupAt: Flow<Long?> = preference { prefs -> prefs[KEY_BACKUP_LAST_AT] }
    val lastBackupError: Flow<String?> = preference { prefs -> prefs[KEY_BACKUP_LAST_ERROR] }

    suspend fun setLastBackupResult(timestamp: Long, error: String?) {
        store.edit { prefs ->
            prefs[KEY_BACKUP_LAST_AT] = timestamp
            if (error == null) prefs.remove(KEY_BACKUP_LAST_ERROR) else {
                prefs[KEY_BACKUP_LAST_ERROR] = error
            }
        }
    }

    /**
     * Verwirft alle Einstellungen; danach gelten überall wieder die Vorgabewerte.
     *
     * Auch die Angaben zur Sicherung sind damit weg. Der Zeitplan der automatischen Sicherung
     * lebt außerhalb der Einstellungen weiter – wer hier leert, muss ihn getrennt abbestellen
     * (siehe [de.beispiel.meintraining.data.backup.BackupRepository.disableAutoBackup]).
     */
    suspend fun clear() {
        store.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val KEY_SELECTED_DAY = intPreferencesKey("selected_day_id")
        val KEY_DELOAD_WEEKS = intPreferencesKey("deload_cycle_weeks")
        val KEY_DAY_COUNT = intPreferencesKey("day_count")
        val KEY_APP_TITLE = stringPreferencesKey("app_title")
        val KEY_HIDDEN_TRACKING = stringSetPreferencesKey("hidden_tracking_names")
        val KEY_LAST_DAY_ADVANCE = longPreferencesKey("last_day_advance")
        val KEY_ROTATION_START_AFTER = longPreferencesKey("rotation_start_after")
        val KEY_BACKUP_URI = stringPreferencesKey("backup_target_uri")
        val KEY_BACKUP_INTERVAL = intPreferencesKey("backup_interval_days")
        val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val KEY_BACKUP_LAST_AT = longPreferencesKey("backup_last_at")
        val KEY_BACKUP_LAST_ERROR = stringPreferencesKey("backup_last_error")
    }
}
