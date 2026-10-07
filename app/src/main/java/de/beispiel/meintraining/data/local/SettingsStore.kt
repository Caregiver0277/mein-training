package de.beispiel.meintraining.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import de.beispiel.meintraining.util.DEFAULT_WEEKLY_GOAL
import de.beispiel.meintraining.util.MAX_CYCLE_WEEKS
import de.beispiel.meintraining.util.MAX_WEEKLY_GOAL
import de.beispiel.meintraining.util.MIN_CYCLE_WEEKS
import de.beispiel.meintraining.util.MIN_WEEKLY_GOAL
import de.beispiel.meintraining.util.NO_ROTATION_CUT
import de.beispiel.meintraining.util.WorkoutMarker
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
    val weeklyGoal: Int,
    val dayCount: Int,
    val selectedDayId: Int,
    val hiddenTrackingNames: Set<String>,
    val hiddenExerciseNames: Set<String>
)

/**
 * Ein beim Abhaken verbrauchter Merker samt dem Eintrag, in den er eingegangen ist – siehe
 * [SettingsStore.consumedWorkoutMarker].
 */
data class ConsumedMarker(val sessionId: Long, val marker: WorkoutMarker)

/**
 * Kleine Einstellungen, die nicht in die Datenbank gehören: gewählter Tag, Rundenlänge und
 * -schnitte, Überschrift, Blocklänge, Wochenziel, Ausblendlisten, „Bildschirm anlassen“, der Merker des
 * laufenden Trainings und die Angaben zur Sicherung.
 */
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
     * Sonst würde jeder Wert einzeln abgefragt: ein Lesevorgang je Wert, zwischen die sich eine
     * Änderung schieben kann – die Sicherung enthielte dann einen Zustand, den es so nie gab.
     * Gelesen wird über dieselben Funktionen wie die Flüsse darunter, damit Vorgabewerte und
     * Grenzen nur an einer Stelle stehen.
     */
    suspend fun snapshot(): SettingsSnapshot = preferences.first().let { prefs ->
        SettingsSnapshot(
            appTitle = readAppTitle(prefs),
            deloadCycleWeeks = readDeloadWeeks(prefs),
            weeklyGoal = readWeeklyGoal(prefs),
            dayCount = readDayCount(prefs),
            selectedDayId = readSelectedDay(prefs),
            hiddenTrackingNames = readHiddenTracking(prefs),
            hiddenExerciseNames = readHiddenExercises(prefs)
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
     * Die von Hand gezogenen Rundenschnitte als Zeitstempel, aufsteigend.
     *
     * Ohne sie zählt die Runde einfach den Verlauf durch und beginnt von selbst von vorn, sobald
     * jeder Tag einmal dran war (siehe `rotations`). Wer eine Runde abschließt, ohne alle Tage
     * geschafft zu haben, braucht diesen Schnitt – sonst stünden die erledigten Tage weiter da.
     *
     * Gespeichert wird die ganze Reihe und nicht nur der jüngste Schnitt: Erst damit lässt sich
     * ein Schnitt wieder zurücknehmen, ohne dass die Runden davor neu zerfallen.
     *
     * Eine Liste in DataStore heißt Text mit Trennzeichen – für eine Handvoll Zahlen ist das
     * billiger als ein eigenes Format, und die Datei bleibt lesbar.
     */
    private fun readRotationCuts(prefs: Preferences): List<Long> {
        val stored = prefs[KEY_ROTATION_CUTS]
            // Ältere Fassungen kannten genau einen Schnitt. Er wird beim ersten Schreiben von
            // selbst in die Reihe überführt; bis dahin gilt er unverändert weiter.
            ?: return listOfNotNull(prefs[KEY_ROTATION_START_AFTER]?.takeIf { it > NO_ROTATION_CUT })
        return stored.split(CUT_SEPARATOR)
            .mapNotNull { it.toLongOrNull()?.takeIf { value -> value > NO_ROTATION_CUT } }
            .sorted()
    }

    val rotationCuts: Flow<List<Long>> = preference(::readRotationCuts)

    /**
     * Schreibt die Schnitte zurück; die ältesten fallen dabei weg.
     *
     * Nur die jüngsten [MAX_ROTATION_CUTS] werden behalten: Ein Schnitt wirkt sich allein auf die
     * Runde aus, in der er liegt, und Runden, die Jahre zurückliegen, sieht sich niemand mehr an.
     */
    suspend fun setRotationCuts(cuts: List<Long>) {
        store.edit { prefs ->
            prefs[KEY_ROTATION_CUTS] = cuts.takeLast(MAX_ROTATION_CUTS).joinToString(CUT_SEPARATOR)
        }
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

    /**
     * Zeigt das Tracking Prozent statt Kilogramm? Vorgabe: Kilogramm.
     *
     * Nicht in [snapshot] und damit nicht in der Sicherung: Es ist eine Ansicht, mit einem
     * Tippen gewechselt – anders als die Ausblendliste, hinter der eine ganze Auswahl steckt.
     */
    val trackingPercent: Flow<Boolean> = preference { prefs -> prefs[KEY_TRACKING_PERCENT] ?: false }

    suspend fun setTrackingPercent(percent: Boolean) {
        store.edit { prefs -> prefs[KEY_TRACKING_PERCENT] = percent }
    }

    /**
     * An den Trainingstagen ausgeblendete Übungen.
     *
     * Getrennt von den im Tracking ausgeblendeten: Das eine ist eine Übung, die gerade nicht
     * trainiert wird, das andere eine Kurve, die den Graphen zustellt – wer eine Übung pausiert,
     * will ihren Verlauf gerade *nicht* verlieren.
     *
     * Gespeichert wird auch hier das Ausgeblendete und nicht das Sichtbare, damit eine neu
     * angelegte Übung von selbst in ihrem Tag auftaucht. Die Zeilen selbst bleiben unangetastet
     * in der Datenbank stehen – Ausblenden ist kein Löschen und jederzeit umkehrbar.
     */
    private fun readHiddenExercises(prefs: Preferences): Set<String> =
        prefs[KEY_HIDDEN_EXERCISES].orEmpty()

    val hiddenExerciseNames: Flow<Set<String>> = preference(::readHiddenExercises)

    suspend fun setHiddenExerciseNames(names: Set<String>) {
        store.edit { prefs -> prefs[KEY_HIDDEN_EXERCISES] = names }
    }

    private fun readDeloadWeeks(prefs: Preferences): Int =
        prefs[KEY_DELOAD_WEEKS] ?: DEFAULT_DELOAD_CYCLE_WEEKS

    /** Länge eines Trainingsblocks in Wochen; die letzte Woche ist die Deload-Woche. */
    val deloadCycleWeeks: Flow<Int> = preference(::readDeloadWeeks)

    suspend fun setDeloadCycleWeeks(weeks: Int) {
        store.edit { prefs -> prefs[KEY_DELOAD_WEEKS] = weeks.coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS) }
    }

    private fun readWeeklyGoal(prefs: Preferences): Int =
        (prefs[KEY_WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL).coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL)

    /** Trainings pro Woche, die das Wochenziel der Statistik ausmachen. */
    val weeklyGoal: Flow<Int> = preference(::readWeeklyGoal)

    suspend fun setWeeklyGoal(goal: Int) {
        store.edit { prefs -> prefs[KEY_WEEKLY_GOAL] = goal.coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL) }
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

    /**
     * Bleibt der Bildschirm an, solange die App im Vordergrund liegt? Vorgabe: nein.
     *
     * Nicht in [snapshot] und damit nicht in der Sicherung – wie der Ton der Pausenuhr eine
     * Frage des Geräts und der Gewohnheit, nicht des Trainingsplans. Wer auf einem neuen Handy
     * einliest, entscheidet dort neu, ob der Akku das mitmacht.
     */
    val keepScreenOn: Flow<Boolean> = preference { prefs -> prefs[KEY_KEEP_SCREEN_ON] ?: false }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_KEEP_SCREEN_ON] = enabled }
    }

    // --- Laufendes Training ------------------------------------------------

    /**
     * Der Merker des laufenden Trainings – siehe [WorkoutMarker]; `null`, solange seit dem
     * letzten Abhaken nichts geschah.
     *
     * Nicht in [snapshot] und damit nicht in der Sicherung: Er gilt für ein Training, das gerade
     * läuft, und wäre auf einem anderen Gerät oder nach dem Einspielen bedeutungslos.
     */
    suspend fun workoutMarker(): WorkoutMarker? = readMarker(preferences.first(), MARKER_KEYS)

    /**
     * Der Merker, der beim letzten Abhaken verbraucht wurde, samt dessen Eintrag. Nimmt ein
     * zweites Tippen genau diesen Eintrag zurück, kommt der Merker wieder.
     */
    suspend fun consumedWorkoutMarker(): ConsumedMarker? {
        val prefs = preferences.first()
        val sessionId = prefs[KEY_CONSUMED_SESSION] ?: return null
        return readMarker(prefs, CONSUMED_KEYS)?.let { ConsumedMarker(sessionId, it) }
    }

    /**
     * Schreibt den laufenden und den verbrauchten Merker in einem Zug – beim Abhaken wandert der
     * eine in den anderen, und dazwischen darf kein halber Stand auf der Platte liegen.
     */
    suspend fun setWorkoutMarkers(current: WorkoutMarker?, consumed: ConsumedMarker?) {
        store.edit { prefs ->
            writeMarker(prefs, MARKER_KEYS, current)
            writeMarker(prefs, CONSUMED_KEYS, consumed?.marker)
            if (consumed == null) {
                prefs.remove(KEY_CONSUMED_SESSION)
            } else {
                prefs[KEY_CONSUMED_SESSION] = consumed.sessionId
            }
        }
    }

    /** Nur den laufenden Merker; der verbrauchte bleibt, wie er ist. */
    suspend fun setWorkoutMarker(marker: WorkoutMarker?) {
        store.edit { prefs -> writeMarker(prefs, MARKER_KEYS, marker) }
    }

    private fun readMarker(prefs: Preferences, keys: MarkerKeys): WorkoutMarker? {
        val dayId = prefs[keys.dayId] ?: return null
        val startedAt = prefs[keys.startedAt] ?: return null
        val last = prefs[keys.lastActivityAt] ?: return null
        return WorkoutMarker(
            dayId = dayId,
            startedAt = startedAt,
            lastActivityAt = last,
            activeUntil = prefs[keys.activeUntil] ?: last
        )
    }

    private fun writeMarker(
        prefs: MutablePreferences,
        keys: MarkerKeys,
        marker: WorkoutMarker?
    ) {
        if (marker == null) {
            prefs.remove(keys.dayId)
            prefs.remove(keys.startedAt)
            prefs.remove(keys.lastActivityAt)
            prefs.remove(keys.activeUntil)
        } else {
            prefs[keys.dayId] = marker.dayId
            prefs[keys.startedAt] = marker.startedAt
            prefs[keys.lastActivityAt] = marker.lastActivityAt
            prefs[keys.activeUntil] = marker.activeUntil
        }
    }

    /** Die vier Schlüssel eines Merkers. */
    private class MarkerKeys(prefix: String) {
        val dayId = intPreferencesKey("${prefix}_day_id")
        val startedAt = longPreferencesKey("${prefix}_started_at")
        val lastActivityAt = longPreferencesKey("${prefix}_last_activity_at")
        val activeUntil = longPreferencesKey("${prefix}_active_until")
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
     * Die schon gefeierten Meilensteine (siehe [de.beispiel.meintraining.util.Milestone.id]), damit
     * keiner zweimal Konfetti bekommt.
     *
     * Nicht in [snapshot] und damit nicht in der Sicherung: Gefeiert wird auf diesem Gerät. Nach
     * dem Einlesen einer Sicherung stehen hier ohnehin die Meilensteine ihres Bestands (siehe
     * `TrainingRepository.resetCelebratedMilestones`).
     *
     * Fehlt der Eintrag ganz, hat diese Fassung noch nie gerechnet – erster Start nach dem Update
     * oder nach „Alle Daten löschen“. Dann wird alles bereits Erreichte still gemerkt, siehe
     * [markMilestonesCelebrated].
     */
    suspend fun markMilestonesCelebrated(ids: Set<String>): Set<String> {
        var fresh = emptySet<String>()
        // In einem Schreibvorgang gelesen und geschrieben: Zwei Aufrufe kurz nacheinander – etwa
        // der Haken und gleich darauf das Einlesen einer Sicherung – sehen so nie beide denselben
        // alten Stand, und nichts wird doppelt als neu gemeldet.
        store.edit { prefs ->
            val known = prefs[KEY_CELEBRATED_MILESTONES]
            fresh = if (known == null) emptySet() else ids - known
            if (known == null || fresh.isNotEmpty()) prefs[KEY_CELEBRATED_MILESTONES] = known.orEmpty() + ids
        }
        return fresh
    }

    /** Ersetzt die gefeierten Meilensteine – siehe [markMilestonesCelebrated]. */
    suspend fun setCelebratedMilestones(ids: Set<String>) {
        store.edit { prefs -> prefs[KEY_CELEBRATED_MILESTONES] = ids }
    }

    /** Schreibt die gefeierten Meilensteine um; schreibt nur, wenn sich etwas ändert. */
    suspend fun updateCelebratedMilestones(transform: (Set<String>) -> Set<String>) {
        store.edit { prefs ->
            val known = prefs[KEY_CELEBRATED_MILESTONES] ?: return@edit
            val updated = transform(known)
            if (updated != known) prefs[KEY_CELEBRATED_MILESTONES] = updated
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
        val KEY_WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val KEY_DAY_COUNT = intPreferencesKey("day_count")
        val KEY_APP_TITLE = stringPreferencesKey("app_title")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_HIDDEN_TRACKING = stringSetPreferencesKey("hidden_tracking_names")
        val KEY_TRACKING_PERCENT = booleanPreferencesKey("tracking_percent")
        val KEY_HIDDEN_EXERCISES = stringSetPreferencesKey("hidden_exercise_names")
        val KEY_LAST_DAY_ADVANCE = longPreferencesKey("last_day_advance")
        val KEY_ROTATION_CUTS = stringPreferencesKey("rotation_cuts")

        /** Nur noch gelesen: der einzelne Schnitt älterer Fassungen. */
        val KEY_ROTATION_START_AFTER = longPreferencesKey("rotation_start_after")
        val KEY_BACKUP_URI = stringPreferencesKey("backup_target_uri")
        val KEY_BACKUP_INTERVAL = intPreferencesKey("backup_interval_days")
        val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val KEY_BACKUP_LAST_AT = longPreferencesKey("backup_last_at")
        val KEY_BACKUP_LAST_ERROR = stringPreferencesKey("backup_last_error")
        val KEY_CELEBRATED_MILESTONES = stringSetPreferencesKey("celebrated_milestones")

        val MARKER_KEYS = MarkerKeys("workout_marker")
        val CONSUMED_KEYS = MarkerKeys("workout_marker_consumed")
        val KEY_CONSUMED_SESSION = longPreferencesKey("workout_marker_consumed_session")

        const val CUT_SEPARATOR = ","
        const val MAX_ROTATION_CUTS = 100
    }
}
