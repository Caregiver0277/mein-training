package de.beispiel.meintraining.data.backup

/** Was beim Start der App mit der automatischen Sicherung zu tun ist – siehe [autoBackupCheck]. */
enum class AutoBackupCheck {
    /** Ausgeschaltet: nichts zu tun. */
    OFF,

    /** Eingeschaltet und die Datei erreichbar: den Auftrag sicherstellen. */
    SCHEDULE,

    /** Eingeschaltet, aber ohne Zugriff auf die Datei: als Fehler vermerken. */
    ACCESS_LOST
}

/**
 * Prüft, ob die automatische Sicherung laufen kann – siehe
 * [BackupRepository.ensureAutoBackup].
 *
 * [accessibleTargets] sind die Adressen, für die die App noch eine dauerhafte Schreibberechtigung
 * hält. Einen eingeschalteten Schalter ohne Ziel kann es eigentlich nicht geben; er läuft genauso
 * auf [AutoBackupCheck.ACCESS_LOST] hinaus, denn auch dann muss die Datei neu ausgewählt werden.
 */
fun autoBackupCheck(
    enabled: Boolean,
    target: String?,
    accessibleTargets: Set<String>
): AutoBackupCheck = when {
    !enabled -> AutoBackupCheck.OFF
    target != null && target in accessibleTargets -> AutoBackupCheck.SCHEDULE
    else -> AutoBackupCheck.ACCESS_LOST
}
