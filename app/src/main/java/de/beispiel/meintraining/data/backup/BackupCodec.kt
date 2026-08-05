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
            throw BackupFormatException(BackupProblem.Invalid, throwable)
        }
        if (backup.version > BACKUP_VERSION) {
            throw BackupFormatException(
                BackupProblem.FutureVersion(backup.version, BACKUP_VERSION)
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
            .ifNotEmpty { throw BackupFormatException(BackupProblem.DuplicateDays(it.list())) }
        duplicatesOf(backup.exercises.map { it.id })
            .ifNotEmpty { throw BackupFormatException(BackupProblem.DuplicateExercises(it.list())) }
        duplicatesOf(backup.definitions.map { it.name })
            .ifNotEmpty {
                throw BackupFormatException(BackupProblem.DuplicateDefinitions(it.list()))
            }

        val knownDays = backup.days.map { it.id }.toSet()
        backup.exercises.filterNot { it.dayId in knownDays }
            .map { "${it.name} an Tag ${it.dayId}" }
            .ifNotEmpty { throw BackupFormatException(BackupProblem.UnknownDays(it.list())) }
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

/**
 * Was an einer Sicherung nicht stimmt.
 *
 * Ein Grund statt eines fertigen Satzes: Hier unten ist bekannt, *was* schiefging, aber nicht,
 * in welchen Worten es auf dem Bildschirm stehen soll – die stehen bei den übrigen Texten in
 * `strings.xml`. So bleibt [BackupCodec] das, was er sein soll: reines Kotlin, ohne Android und
 * ohne Gerät zu prüfen.
 */
sealed interface BackupProblem {

    /** Die Datei ließ sich nicht öffnen oder nicht bis zum Ende lesen. */
    data object NotReadable : BackupProblem

    /** Die Datei ließ sich nicht zum Schreiben öffnen. */
    data object NotWritable : BackupProblem

    /** Größer als jede echte Sicherung – vermutlich hat der Dialog etwas anderes geliefert. */
    data class TooLarge(val megabytes: Int) : BackupProblem

    /** Kein JSON, oder JSON ohne die Form einer Sicherung. */
    data object Invalid : BackupProblem

    /** Aus einer neueren Fassung der App; hier wäre nur die Hälfte zu verstehen. */
    data class FutureVersion(val fileVersion: Int, val supported: Int) : BackupProblem

    /** Doppelte Kennungen – beim Einspielen fiele der zweite Eintrag auf den ersten. */
    data class DuplicateDays(val listed: String) : BackupProblem
    data class DuplicateExercises(val listed: String) : BackupProblem
    data class DuplicateDefinitions(val listed: String) : BackupProblem

    /** Übungen an Trainingstagen, die die Datei gar nicht mitbringt. */
    data class UnknownDays(val listed: String) : BackupProblem

    /** Geschrieben, aber nicht wieder einlesbar – siehe [BackupRepository.writeTo]. */
    data object NotReadBack : BackupProblem

    /** Wieder eingelesen, aber nicht dasselbe wie das Geschriebene. */
    data object Incomplete : BackupProblem
}

/** Die Sicherungsdatei passt nicht – mit einem Grund, der sich anzeigen lässt. */
class BackupFormatException(val problem: BackupProblem, cause: Throwable? = null) :
    Exception(problem.toString(), cause)
