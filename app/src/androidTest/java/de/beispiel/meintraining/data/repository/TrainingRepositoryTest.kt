package de.beispiel.meintraining.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Das Zusammenspiel von Übung, geteiltem Gewicht und Verlauf.
 *
 * Diese Regeln stehen als einzige nicht in einer reinen Funktion, die sich für sich prüfen ließe:
 * Sie ergeben sich erst aus mehreren Tabellen und einer Transaktion. Geprüft wird deshalb gegen
 * eine echte, aber flüchtige Datenbank.
 *
 * Die Einstellungen werden dabei nur *gelesen* – geschrieben wird in ihnen allein, wenn eine im
 * Tracking ausgeblendete Übung umbenannt wird, und die Namen hier hat niemand ausgeblendet. Der
 * Test lässt die Einstellungen des Geräts also unberührt.
 */
@RunWith(AndroidJUnit4::class)
class TrainingRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var repository: TrainingRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = TrainingRepository(
            appContext = context,
            database = database,
            settingsStore = SettingsStore(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Gewicht erhöhen und zurücknehmen ----------------------------------

    /**
     * Der einfache Fall: erhöhen, zurücknehmen, und es ist, als wäre nichts gewesen – auch im
     * Verlauf. Bliebe der Punkt stehen, zeigte der Graph einen Ausschlag nach oben und sofort
     * wieder zurück, den es nie gab.
     */
    @Test
    fun einZurueckgenommenerPunktVerschwindetAusDemVerlauf() = runBlocking {
        anlegen(name = "Bankdrücken", weightKg = 60.0, stepKg = 2.5)

        val change = repository.progressWeight("Bankdrücken")!!
        assertEquals(62.5, change.newKg, 0.0)
        assertEquals(2, verlaufVon("Bankdrücken").size)

        assertTrue(zuruecknehmen(change, "Bankdrücken"))
        assertEquals(60.0, gewichtVon("Bankdrücken")!!, 0.0)
        assertEquals(listOf(60.0), verlaufVon("Bankdrücken"))
    }

    /**
     * Zweimal auf den Pfeil, dann „Rückgängig“ – der Fall, für den die Kennung des
     * Verlaufseintrags überhaupt mitgeführt wird.
     *
     * Zurückgenommen gehört genau die zweite Erhöhung: Gewicht zurück auf den Stand nach der
     * ersten, und aus dem Verlauf verschwindet ihr Punkt – nicht der der ersten.
     */
    @Test
    fun beiZweiErhoehungenTrifftDasZuruecknehmenGenauDieZweite() = runBlocking {
        anlegen(name = "Kniebeuge", weightKg = 100.0, stepKg = 5.0)

        repository.progressWeight("Kniebeuge")!!
        val zweite = repository.progressWeight("Kniebeuge")!!
        assertEquals(110.0, zweite.newKg, 0.0)
        assertEquals(listOf(100.0, 105.0, 110.0), verlaufVon("Kniebeuge"))

        assertTrue(zuruecknehmen(zweite, "Kniebeuge"))
        assertEquals(105.0, gewichtVon("Kniebeuge")!!, 0.0)
        // Der Punkt der ersten Erhöhung bleibt – sie wurde ja nicht zurückgenommen.
        assertEquals(listOf(100.0, 105.0), verlaufVon("Kniebeuge"))
    }

    /**
     * Eine Erhöhung, über die schon die nächste hinweggegangen ist, lässt sich nicht mehr
     * zurücknehmen.
     *
     * Sonst stünde das Gewicht auf dem Stand von vor beiden, während der Punkt der zweiten im
     * Verlauf weiterlebte – Liste und Graph zeigten Verschiedenes. Auf dem Bildschirm kommt es
     * dazu gar nicht erst: Die neue Meldung löst die alte ab.
     */
    @Test
    fun eineUeberholteErhoehungLaesstSichNichtMehrZuruecknehmen() = runBlocking {
        anlegen(name = "Rudern", weightKg = 40.0, stepKg = 2.5)

        val erste = repository.progressWeight("Rudern")!!
        repository.progressWeight("Rudern")!!

        assertFalse(zuruecknehmen(erste, "Rudern"))
        assertEquals(45.0, gewichtVon("Rudern")!!, 0.0)
        assertEquals(listOf(40.0, 42.5, 45.0), verlaufVon("Rudern"))
    }

    // --- Umbenennen --------------------------------------------------------

    /**
     * Wird die letzte Zeile eines Namens umbenannt, zieht der Gewichtsverlauf mit um.
     *
     * Bliebe er stehen, zerfiele die Kurve am Namenswechsel in zwei Stücke: eine, die abbricht,
     * und eine neue mit einem einzigen Punkt.
     */
    @Test
    fun umbenennenNimmtDenVerlaufMit() = runBlocking {
        val id = anlegen(name = "Bankdrücken", weightKg = 60.0, stepKg = 2.5)
        repository.progressWeight("Bankdrücken")!!

        umbenennen(id = id, von = "Bankdrücken", nach = "Bankdrücken KH", weightKg = 62.5)

        assertEquals(emptyList<Double>(), verlaufVon("Bankdrücken"))
        assertEquals(listOf(60.0, 62.5), verlaufVon("Bankdrücken KH"))
        assertNull(database.exerciseDefinitionDao().find("Bankdrücken"))
        assertEquals(62.5, gewichtVon("Bankdrücken KH")!!, 0.0)
    }

    /** Ein reines Umbenennen ist keine Gewichtsänderung und schreibt deshalb keinen Punkt. */
    @Test
    fun umbenennenAlleinSchreibtKeinenNeuenPunkt() = runBlocking {
        val id = anlegen(name = "Dips", weightKg = 20.0, stepKg = 1.25)
        assertEquals(listOf(20.0), verlaufVon("Dips"))

        umbenennen(id = id, von = "Dips", nach = "Barrendips", weightKg = 20.0)

        assertEquals(listOf(20.0), verlaufVon("Barrendips"))
    }

    /**
     * Wer auf den Namen einer *vorhandenen* Übung umbenennt, legt zwei zusammen – dann bleibt
     * der alte Verlauf, wo er ist.
     *
     * Ineinandergeschoben ergäben zwei Verläufe eine Kurve, die zwischen zwei verschiedenen
     * Lasten hin und her springt. So bleibt der alte im Tracking sichtbar und der Schritt
     * umkehrbar.
     */
    @Test
    fun zusammenlegenLaesstDenAltenVerlaufStehen() = runBlocking {
        val id = anlegen(name = "Latzug", weightKg = 50.0, stepKg = 2.5)
        anlegen(name = "Klimmzug", weightKg = 50.0, stepKg = 2.5, dayId = 2)

        umbenennen(id = id, von = "Latzug", nach = "Klimmzug", weightKg = 50.0)

        assertEquals(listOf(50.0), verlaufVon("Latzug"))
        assertEquals(listOf(50.0), verlaufVon("Klimmzug"))
    }

    /** Steht der alte Name noch an einem anderen Tag, ist nichts umzubenennen. */
    @Test
    fun einNameAnMehrerenTagenBehaeltSeinenVerlauf() = runBlocking {
        val id = anlegen(name = "Schulterdrücken", weightKg = 30.0, stepKg = 2.5)
        anlegen(name = "Schulterdrücken", weightKg = 30.0, stepKg = 2.5, dayId = 2)

        umbenennen(id = id, von = "Schulterdrücken", nach = "Nackendrücken", weightKg = 30.0)

        assertEquals(listOf(30.0), verlaufVon("Schulterdrücken"))
        assertEquals(emptyList<Double>(), verlaufVon("Nackendrücken"))
    }

    // --- Hilfen ------------------------------------------------------------

    /** Legt eine Übung an und liefert ihre Kennung. */
    private suspend fun anlegen(
        name: String,
        weightKg: Double,
        stepKg: Double,
        dayId: Int = 1
    ): Long {
        repository.saveExercise(
            id = null,
            dayId = dayId,
            name = name,
            variation = null,
            weightKg = weightKg,
            sets = 3,
            repsMin = 4,
            repsMax = 6,
            progressionStepKg = stepKg
        )
        return database.exerciseDao().listByDay(dayId).first { it.name == name }.id
    }

    /** Speichert dieselbe Zeile unter neuem Namen – so, wie es das Bearbeiten-Sheet tut. */
    private suspend fun umbenennen(id: Long, von: String, nach: String, weightKg: Double) {
        val vorher = database.exerciseDao().findEntityById(id)!!
        assertEquals(von, vorher.name)
        repository.saveExercise(
            id = id,
            dayId = vorher.dayId,
            name = nach,
            variation = vorher.variation,
            // Das Sheet füllt seine Felder aus dem gespeicherten Stand; ein Umbenennen bringt
            // deshalb das unveränderte Gewicht wieder mit.
            weightKg = weightKg,
            sets = vorher.sets,
            repsMin = vorher.repsMin,
            repsMax = vorher.repsMax,
            progressionStepKg = 2.5
        )
    }

    private suspend fun zuruecknehmen(change: WeightChange, name: String): Boolean =
        repository.revertWeight(
            name = name,
            previousKg = change.previousKg,
            increasedToKg = change.newKg,
            logId = change.logId
        )

    private suspend fun gewichtVon(name: String): Double? =
        database.exerciseDefinitionDao().find(name)?.weightKg

    /** Die aufgezeichneten Gewichte einer Übung, ältestes zuerst. */
    private suspend fun verlaufVon(name: String): List<Double> =
        database.weightLogDao().listAll().filter { it.exerciseName == name }.map { it.weightKg }
}
