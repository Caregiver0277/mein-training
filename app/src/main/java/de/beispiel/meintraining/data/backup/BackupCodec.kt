package de.beispiel.meintraining.data.backup

import kotlinx.serialization.json.Json

/**
 * Das Dateiformat für sich – ohne Datenbank und ohne Android.
 *
 * Bewusst getrennt vom [BackupRepository]: Ob eine Sicherung korrekt geschrieben und wieder
 * gelesen wird, lässt sich so ohne Gerät prüfen. Genau daran hängt, ob ein Verlauf im Ernstfall
 * zurückkommt.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        // Unbekannte Felder überspringen: Eine Datei aus einer älteren Fassung, die inzwischen
        // entfernte Felder enthält, soll sich trotzdem einlesen lassen.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /**
     * Liest eine Sicherung. Wirft [BackupFormatException], wenn die Datei nicht passt – lieber
     * ein klarer Abbruch als ein halb eingespielter Bestand.
     */
    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (throwable: Exception) {
            throw BackupFormatException("Die Datei ist keine gültige Sicherung.", throwable)
        }
        if (backup.version > BACKUP_VERSION) {
            throw BackupFormatException(
                "Die Sicherung stammt aus einer neueren Version der App " +
                    "(Format ${backup.version}, hier: $BACKUP_VERSION)."
            )
        }
        validate(backup)
        return backup
    }

    /**
     * Prüft, ob die Sicherung in sich stimmig ist.
     *
     * Eine Datei kann fehlerfrei aussehen und beim Einspielen trotzdem still Daten
     * verschlucken: Doppelte Kennungen fallen beim Einfügen zusammen, weil der letzte
     * Eintrag den vorherigen ersetzt, und eine Übung an einem Tag, den es nicht gibt, taucht
     * in der App nie wieder auf. Beides fiele erst auf, wenn der bisherige Bestand längst
     * ersetzt ist – deshalb hier abbrechen, solange noch nichts geschrieben wurde.
     *
     * Der Gewichtsverlauf wird bewusst nicht geprüft: Er bleibt auch für gelöschte Übungen
     * erhalten, ein Eintrag ohne passende Übung ist also gewollt und kein Schaden.
     */
    private fun validate(backup: BackupFile) {
        duplicatesOf(backup.days.map { it.id })
            .ifNotEmpty { throw BackupFormatException("Trainingstage doppelt: ${it.list()}.") }
        duplicatesOf(backup.exercises.map { it.id })
            .ifNotEmpty { throw BackupFormatException("Übungen doppelt: ${it.list()}.") }
        duplicatesOf(backup.definitions.map { it.name })
            .ifNotEmpty { throw BackupFormatException("Übungsdaten doppelt: ${it.list()}.") }

        val knownDays = backup.days.map { it.id }.toSet()
        backup.exercises.filterNot { it.dayId in knownDays }
            .map { "${it.name} an Tag ${it.dayId}" }
            .ifNotEmpty {
                throw BackupFormatException("Übungen an Tagen, die es nicht gibt: ${it.list()}.")
            }
    }

    private fun <T> duplicatesOf(values: List<T>): List<T> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList()

    private inline fun <T> List<T>.ifNotEmpty(block: (List<T>) -> Unit) {
        if (isNotEmpty()) block(this)
    }

    /** Nur die ersten paar Einträge – eine Fehlermeldung, die niemand liest, hilft nicht. */
    private fun <T> List<T>.list(): String {
        val shown = take(MAX_LISTED_PROBLEMS).joinToString()
        return if (size > MAX_LISTED_PROBLEMS) "$shown … (${size} insgesamt)" else shown
    }

    private const val MAX_LISTED_PROBLEMS = 3
}

/** Die Sicherungsdatei passt nicht – mit einem Grund, der sich anzeigen lässt. */
class BackupFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
