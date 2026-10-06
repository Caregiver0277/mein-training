package de.beispiel.meintraining.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.model.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Das Zusammenspiel von Übung, geteiltem Gewicht und Verlauf.
 *
 * Diese Regeln stehen als einzige nicht in einer reinen Funktion, die sich für sich prüfen ließe:
 * Sie ergeben sich erst aus mehreren Tabellen und einer Transaktion. Geprüft wird deshalb gegen
 * eine echte, aber flüchtige Datenbank.
 *
 * Anders als die Datenbank sind die Einstellungen nicht flüchtig: Sie liegen als Datei neben der
 * App, und das Ausblenden schreibt hinein. Die Ausblendliste wird deshalb vor und nach jedem Test
 * geleert – sonst trüge ein Test seine Namen in den nächsten.
 */
@RunWith(AndroidJUnit4::class)
class TrainingRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: TrainingRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        settingsStore = SettingsStore(context)
        repository = TrainingRepository(
            appContext = context,
            database = database,
            settingsStore = settingsStore
        )
        runBlocking { settingsStore.setHiddenExerciseNames(emptySet()) }
    }

    @After
    fun tearDown() {
        runBlocking { settingsStore.setHiddenExerciseNames(emptySet()) }
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

    // --- Gewicht senken ----------------------------------------------------

    /**
     * Zeigt der Pfeil nach unten, senkt er das Gewicht um den Schritt – und der Verlauf hält es
     * genauso fest wie eine Erhöhung. Das ist der Fall für alles, was sich abtrainiert: die
     * Unterstützung an der Klimmzugmaschine etwa.
     */
    @Test
    fun einePfeilRichtungNachUntenSenktDasGewicht() = runBlocking {
        anlegen(name = "Klimmzugmaschine", weightKg = 30.0, stepKg = 2.5, progressionDown = true)

        val change = repository.progressWeight("Klimmzugmaschine")!!
        assertEquals(30.0, change.previousKg, 0.0)
        assertEquals(27.5, change.newKg, 0.0)
        assertEquals(listOf(30.0, 27.5), verlaufVon("Klimmzugmaschine"))

        // Zurücknehmen läuft über denselben Weg wie bei einer Erhöhung.
        assertTrue(zuruecknehmen(change, "Klimmzugmaschine"))
        assertEquals(30.0, gewichtVon("Klimmzugmaschine")!!, 0.0)
        assertEquals(listOf(30.0), verlaufVon("Klimmzugmaschine"))
    }

    /**
     * Bei 0 kg ist unten Schluss: Der Druck auf den Pfeil bewirkt dann nichts, statt ein
     * negatives Gewicht zu schreiben oder denselben Wert ein zweites Mal in den Verlauf zu legen.
     */
    @Test
    fun untenIstBeiNullSchluss() = runBlocking {
        anlegen(name = "Bandunterstützung", weightKg = 2.0, stepKg = 2.5, progressionDown = true)

        assertEquals(0.0, repository.progressWeight("Bandunterstützung")!!.newKg, 0.0)
        assertNull(repository.progressWeight("Bandunterstützung"))
        assertEquals(listOf(2.0, 0.0), verlaufVon("Bandunterstützung"))
    }

    // --- Ein Schritt zurück (langer Druck auf den Pfeil) -------------------

    /**
     * Der lange Druck geht einen Schritt gegen die Pfeilrichtung, schreibt dafür einen eigenen
     * Verlaufspunkt und lässt sich genauso zurücknehmen wie ein Tippen. Die Richtung der Übung
     * bleibt dabei unverändert.
     */
    @Test
    fun einSchrittZurueckGehtGegenDenPfeilUndLaesstSichZuruecknehmen() = runBlocking {
        anlegen(name = "Schulterdrücken", weightKg = 60.0, stepKg = 2.5)

        repository.progressWeight("Schulterdrücken")!!
        val zurueck = repository.progressWeight("Schulterdrücken", reverse = true)!!
        assertEquals(62.5, zurueck.previousKg, 0.0)
        assertEquals(60.0, zurueck.newKg, 0.0)
        assertEquals(listOf(60.0, 62.5, 60.0), verlaufVon("Schulterdrücken"))

        assertTrue(zuruecknehmen(zurueck, "Schulterdrücken"))
        assertEquals(62.5, gewichtVon("Schulterdrücken")!!, 0.0)
        assertEquals(listOf(60.0, 62.5), verlaufVon("Schulterdrücken"))
    }

    /** Zeigt der Pfeil nach unten, erhöht der lange Druck – auch von 0 kg aus. */
    @Test
    fun beiPfeilNachUntenErhoehtEinSchrittZurueck() = runBlocking {
        anlegen(name = "Dip-Maschine", weightKg = 0.0, stepKg = 5.0, progressionDown = true)

        assertEquals(5.0, repository.progressWeight("Dip-Maschine", reverse = true)!!.newKg, 0.0)
        assertEquals(listOf(0.0, 5.0), verlaufVon("Dip-Maschine"))
    }

    /** Bei 0 kg und Pfeil nach oben bewirkt der lange Druck nichts – kein doppelter Punkt. */
    @Test
    fun einSchrittZurueckBleibtBeiNullStehen() = runBlocking {
        anlegen(name = "Bauchpresse", weightKg = 0.0, stepKg = 2.5)

        assertNull(repository.progressWeight("Bauchpresse", reverse = true))
        assertEquals(listOf(0.0), verlaufVon("Bauchpresse"))
    }

    // --- Ausblenden --------------------------------------------------------

    /**
     * Ausblenden nimmt nichts weg: Die Zeile, ihre geteilten Werte und der Verlauf bleiben
     * stehen, es merkt sich nur den Namen – und gibt ihn wieder her.
     */
    @Test
    fun ausblendenLaesstZeileUndVerlaufStehen() = runBlocking {
        anlegen(name = "Beinpresse", weightKg = 80.0, stepKg = 5.0)

        repository.setExerciseHidden("Beinpresse", hidden = true)
        assertEquals(setOf("Beinpresse"), repository.hiddenExerciseNames.first())
        assertEquals(80.0, gewichtVon("Beinpresse")!!, 0.0)
        assertEquals(1, database.exerciseDao().listByDay(1).count { it.name == "Beinpresse" })

        repository.setExerciseHidden("Beinpresse", hidden = false)
        assertEquals(emptySet<String>(), repository.hiddenExerciseNames.first())
    }

    /**
     * Eine gelöschte Übung darf nicht als ausgeblendeter Name zurückbleiben – sonst wäre eine
     * später neu angelegte Übung gleichen Namens von Anfang an unsichtbar.
     */
    @Test
    fun einGeloeschterNameBleibtNichtAusgeblendet() = runBlocking {
        anlegen(name = "Wadenheben", weightKg = 40.0, stepKg = 2.5)
        repository.setExerciseHidden("Wadenheben", hidden = true)

        repository.deleteExercisesEverywhere(setOf("Wadenheben"))

        assertEquals(emptySet<String>(), repository.hiddenExerciseNames.first())
    }

    /**
     * Umsortieren, während eine Übung ausgeblendet ist: Die sichtbaren nehmen die neue
     * Reihenfolge an, die ausgeblendete bleibt an ihrem Platz zwischen ihnen.
     *
     * Die Oberfläche kennt die ausgeblendete Zeile gar nicht und schickt sie deshalb nicht mit –
     * ohne diese Regel behielte sie ihre alte Nummer und läge damit doppelt.
     */
    @Test
    fun umsortierenLaesstAusgeblendeteAnIhremPlatz() = runBlocking {
        val erste = anlegen(name = "Rudern KH", weightKg = 20.0, stepKg = 2.5)
        val versteckt = anlegen(name = "Face Pull", weightKg = 15.0, stepKg = 1.25)
        val dritte = anlegen(name = "Reverse Fly", weightKg = 10.0, stepKg = 1.25)
        repository.setExerciseHidden("Face Pull", hidden = true)

        // Die Oberfläche schickt nur die sichtbaren Zeilen – in umgekehrter Reihenfolge.
        repository.reorderExercises(dayId = 1, orderedIds = listOf(dritte, erste))

        assertEquals(
            listOf("Reverse Fly", "Face Pull", "Rudern KH"),
            database.exerciseDao().listByDay(1).map { it.name }
        )
        assertEquals(listOf(0, 1, 2), database.exerciseDao().listByDay(1).map { it.position })
        assertEquals(versteckt, database.exerciseDao().listByDay(1)[1].id)
    }

    /**
     * Umsortieren, während hinter einem Superset eine ausgeblendete Übung steht: Wer eine andere
     * Übung über das Superset schiebt, darf es dabei nicht verlieren.
     *
     * Die ausgeblendete Zeile behält ihren Platz, und der fiele hier mitten in den Block. Ohne
     * Ausnahme dafür löste das Aufräumen das Superset auf, obwohl auf dem Bildschirm alle
     * Mitglieder beieinanderstanden.
     */
    @Test
    fun umsortierenErhaeltEinSupersetTrotzAusgeblendeterUebung() = runBlocking {
        val bizeps = anlegen(name = "Bizeps", weightKg = 15.0, stepKg = 1.25)
        val trizeps = anlegen(name = "Trizeps", weightKg = 20.0, stepKg = 1.25)
        val versteckt = anlegen(name = "Unterarme", weightKg = 10.0, stepKg = 1.25)
        val seitheben = anlegen(name = "Seitheben", weightKg = 8.0, stepKg = 1.0)
        repository.createSuperset(dayId = 1, ids = setOf(bizeps, trizeps))
        repository.setExerciseHidden("Unterarme", hidden = true)

        // Sichtbar: [Bizeps Trizeps] Seitheben – Seitheben wird ganz nach oben geschoben.
        repository.reorderExercises(dayId = 1, orderedIds = listOf(seitheben, bizeps, trizeps))

        val tag = database.exerciseDao().listByDay(1)
        assertEquals(listOf(seitheben, bizeps, trizeps, versteckt), tag.map { it.id })
        assertTrue(tag.filter { it.id in setOf(bizeps, trizeps) }.all { it.supersetId != null })
    }

    /** Umbenennen zieht die Ausblendung mit, statt sie am alten Namen hängen zu lassen. */
    @Test
    fun umbenennenNimmtDieAusblendungMit() = runBlocking {
        val id = anlegen(name = "Butterfly", weightKg = 25.0, stepKg = 2.5)
        repository.setExerciseHidden("Butterfly", hidden = true)

        umbenennen(id = id, von = "Butterfly", nach = "Brustmaschine", weightKg = 25.0)

        assertEquals(setOf("Brustmaschine"), repository.hiddenExerciseNames.first())
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

    // --- Notiz und Satz-Protokoll -----------------------------------------

    /**
     * Notiz und Protokoll-Schalter hängen am Namen: an einem Tag gespeichert, an jedem anderen
     * Tag derselben Übung zu sehen. Wer den Schalter nicht mitgibt, lässt ihn stehen.
     */
    @Test
    fun notizUndSchalterGeltenAnAllenTagen() = runBlocking {
        val id = anlegen(name = "Beinpresse", weightKg = 120.0, stepKg = 5.0)
        anlegen(name = "Beinpresse", weightKg = 120.0, stepKg = 5.0, dayId = 2)
        val zeile = repository.findExercise(id)!!
        repository.saveExercise(
            id = id, dayId = 1, name = zeile.name, variation = null, weightKg = 120.0,
            sets = 3, repsMin = 8, repsMax = 12, progressionStepKg = 5.0, progressionDown = false,
            note = "Sitz 4, Polster 2", logSets = true
        )
        repository.saveExercise(
            id = id, dayId = 1, name = zeile.name, variation = null, weightKg = 120.0,
            sets = 3, repsMin = 8, repsMax = 12, progressionStepKg = 5.0, progressionDown = false,
            note = "Sitz 5"
        )

        val tag2 = repository.observeAllExercises().first().single { it.dayId == 2 }
        assertEquals("Sitz 5", tag2.note)
        assertTrue(tag2.logSets)
    }

    /** Ein leeres Notizfeld heißt: keine Notiz – nicht eine leere Zeile unter dem Namen. */
    @Test
    fun eineLeereNotizIstKeine() = runBlocking {
        val id = anlegen(name = "Plank", weightKg = 0.0, stepKg = 1.0)
        repository.saveExercise(
            id = id, dayId = 1, name = "Plank", variation = null, weightKg = 0.0,
            sets = null, repsMin = null, repsMax = null, progressionStepKg = 1.0,
            progressionDown = false, note = "  "
        )
        assertNull(repository.findExercise(id)!!.note)
    }

    /**
     * Ein Satz lässt sich speichern, korrigieren und löschen. Variationen desselben Namens
     * führen getrennte Protokolle, auch am selben Tag.
     */
    @Test
    fun saetzeLassenSichKorrigierenUndLoeschenUndVariationenBleibenGetrennt() = runBlocking {
        val seil = repository.logSet("Trizeps", "Seil", dayId = 1, setNumber = 1, reps = 12, weightKg = 20.0)
        repository.logSet("Trizeps", "Stange", dayId = 1, setNumber = 1, reps = 8, weightKg = 30.0)
        repository.logSet("Trizeps", null, dayId = 1, setNumber = 1, reps = 10, weightKg = 25.0)

        assertEquals(listOf(12), repository.observeSetLogs("Trizeps", "Seil").first().map { it.reps })
        assertEquals(listOf(10), repository.observeSetLogs("Trizeps", null).first().map { it.reps })

        assertTrue(repository.updateSetLog(seil, reps = 11, weightKg = 22.5))
        val korrigiert = repository.observeSetLogs("Trizeps", "Seil").first().single()
        assertEquals(11, korrigiert.reps)
        assertEquals(22.5, korrigiert.weightKg!!, 0.0)

        repository.deleteSetLog(seil)
        assertTrue(repository.observeSetLogs("Trizeps", "Seil").first().isEmpty())
        assertFalse(repository.updateSetLog(seil, reps = 5, weightKg = null))
        assertEquals(2, repository.observeSetLogs().first().size)
    }

    /** Wie der Gewichtsverlauf zieht das Protokoll beim Umbenennen der letzten Zeile mit. */
    @Test
    fun umbenennenNimmtDasSatzProtokollMit() = runBlocking {
        val id = anlegen(name = "Rudern", weightKg = 40.0, stepKg = 2.5)
        repository.logSet("Rudern", null, dayId = 1, setNumber = 1, reps = 10, weightKg = 40.0)

        umbenennen(id = id, von = "Rudern", nach = "Rudern Kabel", weightKg = 40.0)

        assertTrue(repository.observeSetLogs("Rudern", null).first().isEmpty())
        assertEquals(1, repository.observeSetLogs("Rudern Kabel", null).first().size)
    }

    /** „Überall löschen“ nimmt das Protokoll mit; das Löschen einer Zeile lässt es stehen. */
    @Test
    fun ueberallLoeschenEntferntDasProtokollEinzelnLoeschenNicht() = runBlocking {
        val id = anlegen(name = "Dips", weightKg = 10.0, stepKg = 2.5)
        anlegen(name = "Curls", weightKg = 12.0, stepKg = 1.0)
        repository.logSet("Dips", null, dayId = 1, setNumber = 1, reps = 8, weightKg = 10.0)
        repository.logSet("Curls", null, dayId = 1, setNumber = 1, reps = 12, weightKg = 12.0)

        repository.deleteExercises(listOf(repository.findExercise(id)!!))
        assertEquals(1, repository.observeSetLogs("Dips", null).first().size)

        repository.deleteExercisesEverywhere(listOf("Dips"))
        assertTrue(repository.observeSetLogs("Dips", null).first().isEmpty())
        assertEquals(1, repository.observeSetLogs("Curls", null).first().size)
    }

    /** „Alle Daten löschen“ lässt vom Protokoll nichts übrig. */
    @Test
    fun allesLoeschenEntferntDasProtokoll() = runBlocking {
        repository.logSet("Kniebeuge", null, dayId = 1, setNumber = 1, reps = 5, weightKg = 100.0)

        repository.deleteAllData()

        assertTrue(repository.observeSetLogs().first().isEmpty())
    }

    // --- Weiterschalten am neuen Tag ---------------------------------------

    /**
     * Tag 3 ausgelassen, Tag 4 gemacht: Am nächsten Morgen ist Tag 3 dran und nicht der in dieser
     * Runde schon erledigte Tag 1.
     */
    @Test
    fun amNeuenTagIstDerUebersprungeneTagDran() = runBlocking {
        val today = LocalDate.now()
        settingsStore.setDayCount(4)
        settingsStore.setRotationCuts(emptyList())
        settingsStore.setLastDayAdvance(0L)
        listOf(1, 2, 4).forEachIndexed { index, dayId ->
            val completedAt = today.minusDays(1).atTime(10 + index, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            database.workoutSessionDao().insert(WorkoutSession(dayId = dayId, completedAt = completedAt))
        }

        repository.advanceDayIfNewDate(today)

        assertEquals(3, repository.currentSelectedDay())
        assertEquals(3, repository.nextDayInRotation(today))
        settingsStore.setLastDayAdvance(0L)
    }

    // --- Hilfen ------------------------------------------------------------

    /** Legt eine Übung an und liefert ihre Kennung. */
    private suspend fun anlegen(
        name: String,
        weightKg: Double,
        stepKg: Double,
        dayId: Int = 1,
        progressionDown: Boolean = false
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
            progressionStepKg = stepKg,
            progressionDown = progressionDown
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
            progressionStepKg = 2.5,
            progressionDown = false
        )
    }

    private suspend fun zuruecknehmen(change: WeightChange, name: String): Boolean =
        repository.revertWeight(
            name = name,
            previousKg = change.previousKg,
            changedToKg = change.newKg,
            logId = change.logId
        )

    private suspend fun gewichtVon(name: String): Double? =
        database.exerciseDefinitionDao().find(name)?.weightKg

    /** Die aufgezeichneten Gewichte einer Übung, ältestes zuerst. */
    private suspend fun verlaufVon(name: String): List<Double> =
        database.weightLogDao().listAll().filter { it.exerciseName == name }.map { it.weightKg }

    // --- Kopieren und Verschieben ------------------------------------------

    /**
     * Kopieren hängt die Übungen in ihrer Reihenfolge ans Ende des Ziel-Tages; der Ausgangstag
     * bleibt, wie er war. Ein vollständig mitgenommenes Superset bleibt im Ziel eines, mit neuer
     * Kennung – das Original behält seine.
     */
    @Test
    fun kopierenHaengtAnsEndeUndBehaeltVollstaendigeSupersets() = runBlocking {
        val ziel = anlegen(name = "Kniebeuge", weightKg = 100.0, stepKg = 5.0, dayId = 2)
        val bizeps = anlegen(name = "Bizeps", weightKg = 15.0, stepKg = 1.25)
        val trizeps = anlegen(name = "Trizeps", weightKg = 20.0, stepKg = 1.25)
        anlegen(name = "Seitheben", weightKg = 8.0, stepKg = 1.0)
        repository.createSuperset(dayId = 1, ids = setOf(bizeps, trizeps))

        val transfer = repository.transferExercises(1, setOf(trizeps, bizeps), toDayId = 2, move = false)!!

        val tag2 = database.exerciseDao().listByDay(2)
        assertEquals(listOf("Kniebeuge", "Bizeps", "Trizeps"), tag2.map { it.name })
        assertEquals(ziel, tag2.first().id)
        val kopien = tag2.drop(1)
        assertTrue(kopien.all { it.supersetId != null && it.supersetId == kopien.first().supersetId })
        val original = database.exerciseDao().listByDay(1)
        assertEquals(listOf("Bizeps", "Trizeps", "Seitheben"), original.map { it.name })
        assertTrue(kopien.first().supersetId != original.first().supersetId)
        // Gewicht hängt am Namen: Die Kopie zeigt dasselbe, ohne neuen Verlaufspunkt.
        assertEquals(listOf(15.0), verlaufVon("Bizeps"))

        repository.undoTransfer(transfer)
        assertEquals(listOf("Kniebeuge"), database.exerciseDao().listByDay(2).map { it.name })
        assertEquals(3, database.exerciseDao().listByDay(1).size)
    }

    /**
     * Verschieben nimmt nur einen Teil eines Supersets mit: Im Ziel löst er sich auf, und am
     * Ausgangstag bleibt ein einzelnes Mitglied übrig, dessen Superset ebenfalls aufgeräumt wird.
     * „Rückgängig“ stellt beides wieder her – auch das Superset, das es am Ausgangstag nur durch
     * das Aufräumen verloren hatte.
     */
    @Test
    fun verschiebenLoestHalbeSupersetsAufUndLaesstSichZuruecknehmen() = runBlocking {
        val bizeps = anlegen(name = "Bizeps", weightKg = 15.0, stepKg = 1.25)
        val trizeps = anlegen(name = "Trizeps", weightKg = 20.0, stepKg = 1.25)
        val seitheben = anlegen(name = "Seitheben", weightKg = 8.0, stepKg = 1.0)
        repository.createSuperset(dayId = 1, ids = setOf(bizeps, trizeps))
        val vorher = database.exerciseDao().listByDay(1)

        val transfer = repository.transferExercises(1, setOf(trizeps, seitheben), toDayId = 3, move = true)!!

        val tag3 = database.exerciseDao().listByDay(3)
        assertEquals(listOf(trizeps, seitheben), tag3.map { it.id })
        assertTrue(tag3.all { it.supersetId == null })
        val tag1 = database.exerciseDao().listByDay(1)
        assertEquals(listOf(bizeps), tag1.map { it.id })
        assertNull(tag1.single().supersetId)

        repository.undoTransfer(transfer)
        assertEquals(vorher, database.exerciseDao().listByDay(1))
        assertTrue(database.exerciseDao().listByDay(3).isEmpty())
    }

    /** Derselbe Tag als Ziel oder eine leere Auswahl: Es passiert nichts. */
    @Test
    fun ohneZielOderAuswahlPassiertNichts() = runBlocking {
        val bizeps = anlegen(name = "Bizeps", weightKg = 15.0, stepKg = 1.25)
        assertNull(repository.transferExercises(1, setOf(bizeps), toDayId = 1, move = true))
        assertNull(repository.transferExercises(1, emptySet(), toDayId = 2, move = false))
        assertEquals(1, database.exerciseDao().listByDay(1).size)
    }
}
