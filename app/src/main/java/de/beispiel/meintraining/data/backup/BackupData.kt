package de.beispiel.meintraining.data.backup

import kotlinx.serialization.Serializable

/**
 * Format der Sicherungsdatei.
 *
 * Die Version steht ganz vorn: Eine Datei aus einer künftigen Fassung der App wird abgelehnt,
 * statt halb eingelesen zu werden. Die Felder heißen wie in der Datenbank, damit die Datei auch
 * von Hand lesbar bleibt – eine Sicherung, die man nicht anschauen kann, ist wenig wert.
 */
@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    /** Zeitpunkt der Sicherung in Millisekunden seit 1970 – nur zur Information. */
    val createdAt: Long,
    val days: List<BackupDay> = emptyList(),
    val exercises: List<BackupExercise> = emptyList(),
    val definitions: List<BackupDefinition> = emptyList(),
    val weightLogs: List<BackupWeightLog> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val settings: BackupSettings = BackupSettings()
) {
    /**
     * Steht in der Sicherung überhaupt etwas, das der Rede wert ist?
     *
     * Die Trainingstage zählen nicht mit: Die legt die App bei jedem Start von selbst wieder
     * an, sie stehen also auch in einer Sicherung, die sonst nichts enthält.
     */
    val hasContent: Boolean
        get() = exercises.isNotEmpty() || definitions.isNotEmpty() ||
            weightLogs.isNotEmpty() || sessions.isNotEmpty()
}

@Serializable
data class BackupDay(val id: Int, val name: String)

@Serializable
data class BackupExercise(
    val id: Long,
    val dayId: Int,
    val name: String,
    val variation: String? = null,
    val sets: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val position: Int = 0,
    val supersetId: Long? = null
)

@Serializable
data class BackupDefinition(
    val name: String,
    val weightKg: Double? = null,
    val progressionStepKg: Double,
    /** Fehlt in Dateien aus älteren Fassungen; dort erhöhte der Pfeil immer. */
    val progressionDown: Boolean = false
)

@Serializable
data class BackupWeightLog(
    val exerciseName: String,
    val weightKg: Double,
    val recordedAt: Long
)

@Serializable
data class BackupSession(val dayId: Int, val completedAt: Long)

/** Die Einstellungen; alles optional, damit ältere Dateien weiterhin passen. */
@Serializable
data class BackupSettings(
    val appTitle: String = "",
    val deloadCycleWeeks: Int? = null,
    val dayCount: Int? = null,
    val selectedDayId: Int? = null,
    val hiddenTrackingNames: List<String> = emptyList(),
    val hiddenExerciseNames: List<String> = emptyList()
)

/** Aktuelle Fassung des Dateiformats. */
const val BACKUP_VERSION = 1

/** Vorgabe und Grenzen für den Abstand der automatischen Sicherung, in Tagen. */
const val DEFAULT_BACKUP_INTERVAL_DAYS = 7
const val MIN_BACKUP_INTERVAL_DAYS = 1
const val MAX_BACKUP_INTERVAL_DAYS = 30
