package de.beispiel.meintraining.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sampleBackup() = BackupFile(
    createdAt = 1_754_000_000_000L,
    days = listOf(BackupDay(1, "Push"), BackupDay(2, "Pull")),
    exercises = listOf(
        BackupExercise(
            id = 7,
            dayId = 1,
            name = "Bankdrücken",
            variation = "Kurzhantel",
            sets = 3,
            repsMin = 4,
            repsMax = 6,
            position = 0,
            supersetId = 3
        ),
        BackupExercise(id = 8, dayId = 1, name = "Dehnen", position = 1)
    ),
    definitions = listOf(
        BackupDefinition(
            "Bankdrücken",
            60.0,
            2.5,
            note = "Bank Stufe 3\nSchulterblätter zusammen",
            logSets = true
        ),
        BackupDefinition("Klimmzüge", null, 1.25)
    ),
    weightLogs = listOf(BackupWeightLog("Bankdrücken", 57.5, 1_750_000_000_000L)),
    sessions = listOf(
        BackupSession(dayId = 2, completedAt = 1_752_000_000_000L),
        BackupSession(dayId = 1, completedAt = 1_752_100_000_000L, startedAt = 1_752_096_000_000L)
    ),
    setLogs = listOf(
        BackupSetLog("Bankdrücken", "Kurzhantel", 1, 1_752_097_000_000L, setNumber = 1, reps = 6, weightKg = 60.0),
        BackupSetLog("Klimmzüge", null, 2, 1_752_098_000_000L, setNumber = 1, reps = 8)
    ),
    settings = BackupSettings(
        appTitle = "PPL",
        deloadCycleWeeks = 6,
        dayCount = 2,
        selectedDayId = 2,
        hiddenTrackingNames = listOf("Dehnen")
    )
)

class BackupCodecTest {

    @Test
    fun eineSicherungUeberstehtDenWegDurchDieDatei() {
        val original = sampleBackup()
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun leereWerteBleibenLeer() {
        val original = BackupFile(createdAt = 0L)
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(emptyList<BackupExercise>(), restored.exercises)
        assertNull(restored.settings.dayCount)
        assertEquals("", restored.settings.appTitle)
    }

    @Test
    fun unbekannteFelderStoerenNicht() {
        // So sähe eine Datei aus, die ein späteres Feld kennt, das es hier nicht gibt.
        val text = """{"version":1,"createdAt":5,"zukunft":"egal","days":[{"id":1,"name":"Tag 1"}]}"""
        val restored = BackupCodec.decode(text)
        assertEquals(1, restored.days.size)
        assertEquals("Tag 1", restored.days.first().name)
    }

    /**
     * Eine Datei der Version 1 – so, wie sie die App vor Notiz, Trainingsdauer und Satz-Protokoll
     * geschrieben hat. Sie muss sich weiter einlesen lassen, und alles Neue kommt leer an.
     */
    @Test
    fun eineDateiDerVersion1BleibtEinlesbar() {
        val text = """
            {
              "version": 1,
              "createdAt": 5,
              "days": [{"id": 1, "name": "Tag 1"}],
              "exercises": [{"id": 3, "dayId": 1, "name": "Kniebeuge", "sets": 3, "position": 0}],
              "definitions": [{"name": "Kniebeuge", "weightKg": 100.0, "progressionStepKg": 5.0, "progressionDown": false}],
              "weightLogs": [{"exerciseName": "Kniebeuge", "weightKg": 100.0, "recordedAt": 4}],
              "sessions": [{"dayId": 1, "completedAt": 4}],
              "settings": {"appTitle": "", "dayCount": 1}
            }
        """.trimIndent()
        val restored = BackupCodec.decode(text)
        assertEquals(1, restored.version)
        val definition = restored.definitions.single()
        assertNull(definition.note)
        assertFalse(definition.logSets)
        assertNull(restored.sessions.single().startedAt)
        assertEquals(emptyList<BackupSetLog>(), restored.setLogs)
        assertTrue(restored.hasContent)
    }

    @Test
    fun einSatzProtokollAlleinIstInhalt() {
        // Wer nur protokolliert und sonst alles gelöscht hat, darf nicht überschrieben werden.
        val onlyLogs = BackupFile(createdAt = 0L, setLogs = sampleBackup().setLogs)
        assertTrue(onlyLogs.hasContent)
    }

    @Test
    fun eineNeuereFassungWirdAbgelehnt() {
        val text = """{"version":${BACKUP_VERSION + 1},"createdAt":5}"""
        assertRejected<BackupProblem.FutureVersion>(text)
    }

    @Test
    fun dieAbgelehnteFassungNenntBeideNummern() {
        // Sie stehen in der Meldung; eine Verwechslung machte sie unverständlich.
        val text = """{"version":${BACKUP_VERSION + 1},"createdAt":5}"""
        val error = runCatching { BackupCodec.decode(text) }.exceptionOrNull()
        val problem = (error as BackupFormatException).problem as BackupProblem.FutureVersion
        assertEquals(BACKUP_VERSION + 1, problem.fileVersion)
        assertEquals(BACKUP_VERSION, problem.supported)
    }

    @Test
    fun unsinnWirdAbgelehnt() {
        assertRejected<BackupProblem.Invalid>("kein json")
    }

    @Test
    fun doppelteTageWerdenAbgelehnt() {
        val text = BackupCodec.encode(
            sampleBackup().copy(days = listOf(BackupDay(1, "Push"), BackupDay(1, "Pull")))
        )
        assertRejected<BackupProblem.DuplicateDays>(text)
    }

    @Test
    fun doppelteUebungskennungenWerdenAbgelehnt() {
        // Beim Einspielen ersetzt die zweite Zeile die erste – die Übung wäre still weg.
        val duplicate = sampleBackup().exercises.first().copy(name = "Rudern", position = 1)
        assertRejected<BackupProblem.DuplicateExercises>(BackupCodec.encode(sampleBackup().let {
            it.copy(exercises = it.exercises + duplicate)
        }))
    }

    @Test
    fun doppelteUebungsdatenWerdenAbgelehnt() {
        val duplicate = BackupDefinition("Bankdrücken", 40.0, 2.5)
        assertRejected<BackupProblem.DuplicateDefinitions>(BackupCodec.encode(sampleBackup().let {
            it.copy(definitions = it.definitions + duplicate)
        }))
    }

    @Test
    fun eineUebungOhneIhrenTagWirdAbgelehnt() {
        // Sonst läge sie nach dem Einspielen in der Datenbank, ohne je sichtbar zu werden.
        val orphan = BackupExercise(id = 99, dayId = 7, name = "Nirgendwo")
        assertRejected<BackupProblem.UnknownDays>(BackupCodec.encode(sampleBackup().let {
            it.copy(exercises = it.exercises + orphan)
        }))
    }

    @Test
    fun einVerlaufOhnePassendeUebungBleibtErlaubt() {
        // Gewichtsverlauf und Satz-Protokoll überleben das Löschen einer Übung – so gewollt.
        val backup = sampleBackup().let {
            it.copy(
                weightLogs = it.weightLogs + BackupWeightLog("Längst gelöscht", 40.0, 1L),
                setLogs = it.setLogs + BackupSetLog("Längst gelöscht", null, 9, 1L, 1, 10, 40.0)
            )
        }
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun eineSicherungOhneInhaltErkenntSichSelbst() {
        // Genau daran entscheidet die automatische Sicherung, ob sie überschreiben darf.
        assertFalse(BackupFile(createdAt = 0L).hasContent)
        assertFalse(BackupFile(createdAt = 0L, days = listOf(BackupDay(1, "Tag 1"))).hasContent)
        assertTrue(sampleBackup().hasContent)
    }

    /**
     * Abgelehnt *und* mit dem passenden Grund: Der Grund wird angezeigt, und ein falscher
     * schickt bei der Fehlersuche in die verkehrte Richtung.
     */
    private inline fun <reified T : BackupProblem> assertRejected(text: String) {
        val error = runCatching { BackupCodec.decode(text) }.exceptionOrNull()
        assertTrue("Erwartet wurde eine Ablehnung, bekam: $error", error is BackupFormatException)
        val problem = (error as BackupFormatException).problem
        assertTrue(
            "Erwartet wurde ${T::class.simpleName}, bekam: $problem",
            problem is T
        )
    }

    @Test
    fun einAbgeschnittenerDateiRestWirdAbgelehnt() {
        // Genau der Fall, der beim Überschreiben einer längeren Datei entstünde.
        val complete = BackupCodec.encode(sampleBackup())
        assertRejected<BackupProblem.Invalid>(complete.take(complete.length / 2))
    }
}
