package de.beispiel.meintraining.data.repository

import android.content.Context
import androidx.room.withTransaction
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.AppDatabase
import de.beispiel.meintraining.data.local.ExerciseDao
import de.beispiel.meintraining.data.local.ExerciseDefinitionDao
import de.beispiel.meintraining.data.local.SettingsStore
import de.beispiel.meintraining.data.local.TrainingDayDao
import de.beispiel.meintraining.data.local.WeightLogDao
import de.beispiel.meintraining.data.local.WorkoutSessionDao
import de.beispiel.meintraining.data.model.Exercise
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.NO_ROTATION_CUT
import de.beispiel.meintraining.util.RotationEntry
import de.beispiel.meintraining.util.completedDaysInRotation
import de.beispiel.meintraining.util.increaseWeight
import de.beispiel.meintraining.util.nextDayId
import de.beispiel.meintraining.util.survivingSupersetMembers
import de.beispiel.meintraining.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Ergebnis einer Gewichtserhöhung – [previousKg] erlaubt das Zurücknehmen. */
data class WeightChange(val previousKg: Double, val newKg: Double)

/** Ergebnis eines Tippens auf den Haken. */
data class WorkoutToggle(
    /** Steht das Training jetzt im Verlauf? `false` heißt: Der Eintrag wurde zurückgenommen. */
    val isCompleted: Boolean,
    /**
     * Mit diesem Eintrag ist die Runde voll geworden.
     *
     * Entschieden wird das hier und nicht in der Oberfläche: Der angezeigte Zustand wird erst
     * ein paar Bilder später nachgezogen, und aus ihm allein ließe sich das Voll*werden* nicht
     * von einer schon vollen Runde unterscheiden – die bleibt seit Neuestem bis Mitternacht
     * stehen (siehe [completedDaysInRotation]). Der Applaus käme dann bei jedem Start der App
     * noch einmal.
     */
    val completesRotation: Boolean
)

/**
 * Einzige Datenquelle für die ViewModels; kapselt Room und die Einstellungen.
 *
 * Die DAOs kommen aus der [database] statt einzeln von außen: Sie gehören ohnehin zu genau
 * dieser Datenbank, und eine Liste von acht Parametern lädt nur dazu ein, sie irgendwann
 * durcheinanderzubringen.
 */
class TrainingRepository(
    private val appContext: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore
) {

    private val dayDao: TrainingDayDao = database.trainingDayDao()
    private val exerciseDao: ExerciseDao = database.exerciseDao()
    private val definitionDao: ExerciseDefinitionDao = database.exerciseDefinitionDao()
    private val weightLogDao: WeightLogDao = database.weightLogDao()
    private val sessionDao: WorkoutSessionDao = database.workoutSessionDao()

    /**
     * Der zuletzt von Hand oder automatisch gesetzte Tag, noch bevor DataStore ihn kennt.
     * `null` heißt: In dieser Sitzung wurde noch nichts gesetzt, es gilt der gespeicherte Wert.
     */
    private val selectedDayOverride = MutableStateFlow<Int?>(null)

    /**
     * Der ausgewählte Trainingstag.
     *
     * Eine Auswahl gilt sofort; nach DataStore geschrieben wird sie nur nebenher. Läge der
     * gespeicherte Wert auf dem kritischen Pfad, hinge das Umschalten des Tages an einem
     * Schreibvorgang samt `fsync`: Der angetippte Reiter leuchtete erst auf, wenn die Platte
     * durch ist, und die Übungsliste käme noch eine Datenbankabfrage später. Bei belegter
     * Platte – etwa während der automatischen Sicherung – ist das deutlich zu spüren.
     *
     * Damit das aufgeht, müssen *alle* Schreibvorgänge über [selectDay] laufen; wer an
     * [SettingsStore.setSelectedDayId] vorbeischreibt, wird von der Vormerkung überstimmt.
     */
    val selectedDayId: Flow<Int> = combine(
        settingsStore.selectedDayId,
        selectedDayOverride
    ) { stored, override -> override ?: stored }.distinctUntilChanged()

    /**
     * Übernimmt den Tag sofort in der Anzeige, ohne auf das Speichern zu warten.
     *
     * Getrennt von [selectDay], damit die Oberfläche den Wechsel noch im selben Frame anstoßen
     * kann; das Nachschreiben besorgt anschließend [selectDay] in einer Coroutine.
     */
    fun selectDayNow(dayId: Int) {
        selectedDayOverride.value = dayId
    }

    fun observeDays(): Flow<List<TrainingDay>> = dayDao.observeAll()

    /**
     * Alle Übungen aller Tage.
     *
     * Es gibt bewusst keine Abfrage je Tag mehr: Der ganze Bestand ist klein genug, dass ein
     * einziges Abonnement billiger ist als das ständige Auf- und Abbauen einer Abfrage beim
     * Umschalten – geschnitten wird im ViewModel.
     */
    fun observeAllExercises(): Flow<List<ExerciseItem>> = exerciseDao.observeAll()

    /** Alle bekannten Übungen – Grundlage für die Vorschläge im Namensfeld. */
    fun observeDefinitions(): Flow<List<ExerciseDefinition>> = definitionDao.observeAll()

    /** Der komplette Gewichtsverlauf – Grundlage für den Tracking-Graphen. */
    fun observeWeightLogs(): Flow<List<WeightLog>> = weightLogDao.observeAll()

    /** Alle abgehakten Trainings, das jüngste zuerst. */
    fun observeSessions(): Flow<List<WorkoutSession>> = sessionDao.observeAll()

    /** Hakt das Training eines Tages ab und liefert die Kennung des Eintrags. */
    suspend fun completeWorkout(dayId: Int): Long = sessionDao.insert(
        WorkoutSession(dayId = dayId, completedAt = System.currentTimeMillis())
    )

    /**
     * Trägt ein vergessenes Training nach – mit frei gewähltem Tag und Zeitpunkt.
     *
     * Geprüft wird hier nur, was die Datenbank selbst nicht abfängt: ein Trainingstag, den es
     * gar nicht gibt. Der Zeitpunkt kommt aus dem Verlauf schon geprüft an; ein Eintrag in der
     * Zukunft würde Streak, Deload-Rechnung und Runde durcheinanderbringen, deshalb steht die
     * Grenze auch hier noch einmal.
     *
     * Liefert `false`, wenn nichts eingetragen wurde.
     */
    suspend fun addSession(dayId: Int, completedAt: Long): Boolean {
        if (completedAt > System.currentTimeMillis()) return false
        if (dayDao.findById(dayId) == null) return false
        sessionDao.insert(WorkoutSession(dayId = dayId, completedAt = completedAt))
        return true
    }

    /**
     * Hakt das Training eines Tages ab oder nimmt das Abhaken zurück; liefert den neuen Stand.
     *
     * Zurückgenommen wird nur ein Eintrag von *heute*. Das ist der Fall, für den es das
     * Zurücknehmen gibt: danebengetippt, falscher Tag, doch nicht trainiert. Ein Eintrag von
     * vorgestern gehört dagegen zu einem Training, das stattgefunden hat – wer denselben Tag
     * heute erneut abhakt, hat ihn erneut trainiert und bekommt einen zweiten Eintrag.
     *
     * Der Umweg über den Eintrag statt über die angezeigte Runde ist nötig, weil die Runde
     * genau dann leer ist, wenn sie voll war: Nach dem letzten Tag beginnt sie von vorn und
     * jeder Haken steht wieder auf offen. Ein Zurücknehmen, das sich daran hält, legte
     * ausgerechnet nach dem letzten Training der Runde einen zweiten Eintrag an – und ein
     * Zurücknehmen, das die alten Einträge trotzdem sieht, löschte beim ersten Training der
     * neuen Runde das Training der alten.
     *
     * Entschieden wird im Repository und nicht in der Oberfläche: Der angezeigte Zustand wird
     * erst nachgezogen, wenn Room die Änderung gemeldet hat, und das dauert mehrere Bilder.
     * Zwei schnelle Tipps läsen beide noch den Stand von vorher und legten zwei Einträge für
     * denselben Tag an. Innerhalb der Transaktion sieht der zweite Tipp das Ergebnis des
     * ersten: Ohne WAL hat die Datenbank genau einen Schreiber, Transaktionen laufen also
     * nacheinander.
     */
    suspend fun toggleWorkout(dayId: Int, today: LocalDate = LocalDate.now()): WorkoutToggle {
        // Rundenlänge und Rundenschnitt stehen in DataStore und werden deshalb *vor* der
        // Transaktion geholt: Room hat genau einen Transaktions-Thread, und ein Warten auf einen
        // anderen Zufluss mitten drin blockiert ihn – im schlechtesten Fall, bis DataStore
        // seinerseits auf die Platte wartet.
        val dayCount = settingsStore.dayCount.first()
        val startAfter = settingsStore.rotationStartAfter.first()
        return database.withTransaction {
            val latest = sessionDao.latestForDay(dayId)
            if (latest != null && latest.completedAt.toLocalDate() == today) {
                sessionDao.deleteById(latest.id)
                WorkoutToggle(isCompleted = false, completesRotation = false)
            } else {
                completeWorkout(dayId)
                WorkoutToggle(
                    isCompleted = true,
                    completesRotation = isRotationFull(dayCount, today, startAfter)
                )
            }
        }
    }

    /**
     * Beginnt die nächste Runde sofort, statt auf den nächsten Kalendertag zu warten.
     *
     * Gelöscht wird dabei nichts: Der Verlauf bleibt vollständig, und mit ihm Statistik und
     * Deload-Rechnung. Vermerkt wird nur, ab wann die neue Runde zählt – alles, was jetzt schon
     * im Verlauf steht, gehört zur vorigen (siehe [completedDaysInRotation]).
     *
     * Der Schnitt liegt hinter dem jüngsten Eintrag, mindestens aber im Jetzt: Ein Training, das
     * durch Zeitumstellung oder eine eingelesene Sicherung in der Zukunft gelandet ist, würde
     * sonst in der neuen Runde weiterleben und den ersten Tag von vornherein als erledigt zeigen.
     */
    suspend fun startNextRotation() {
        val latest = sessionDao.latest()?.completedAt ?: NO_ROTATION_CUT
        settingsStore.setRotationStartAfter(maxOf(System.currentTimeMillis(), latest))
    }

    /**
     * Ist die Runde mit dem eben geschriebenen Eintrag voll?
     *
     * Gerechnet wird auf dem gespeicherten Verlauf und innerhalb derselben Transaktion, aus
     * demselben Grund wie beim Abhaken selbst: Zwei schnelle Tipps sähen sonst beide denselben
     * Stand. Der Verlauf ist dabei klein – ein Eintrag je Training –, ein Durchlauf kostet
     * nichts Nennenswertes und passiert nur beim Abhaken.
     */
    private suspend fun isRotationFull(
        dayCount: Int,
        today: LocalDate,
        startAfter: Long
    ): Boolean {
        if (dayCount <= 0) return false
        val entries = sessionDao.listAll().map { session ->
            RotationEntry(
                dayId = session.dayId,
                date = session.completedAt.toLocalDate(),
                completedAt = session.completedAt
            )
        }
        return completedDaysInRotation(entries, dayCount, today, startAfter).size >= dayCount
    }

    suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)

    /** Im Tracking ausgeblendete Übungen. */
    val hiddenTrackingNames: Flow<Set<String>> = settingsStore.hiddenTrackingNames

    /** Ab wann die laufende Runde zählt – siehe [startNextRotation]. */
    val rotationStartAfter: Flow<Long> = settingsStore.rotationStartAfter

    // --- Einstellungen -----------------------------------------------------

    val deloadCycleWeeks: Flow<Int> = settingsStore.deloadCycleWeeks
    val appTitle: Flow<String> = settingsStore.appTitle

    /** Anzahl der Trainingstage in einer Runde. */
    val dayCount: Flow<Int> = settingsStore.dayCount

    suspend fun setDeloadCycleWeeks(weeks: Int) = settingsStore.setDeloadCycleWeeks(weeks)

    suspend fun setAppTitle(title: String) = settingsStore.setAppTitle(title)

    /**
     * Ändert die Anzahl der Trainingstage.
     *
     * Beim Verkleinern werden die überzähligen Tage nur ausgeblendet, nicht gelöscht – ihre
     * Übungen stehen unverändert wieder da, sobald die Runde wieder länger wird. Wer sie
     * wirklich loswerden will, leert sie von Hand.
     *
     * Zeigt die Auswahl auf einen Tag, den es nicht mehr gibt, springt sie auf den ersten.
     */
    suspend fun setDayCount(count: Int) {
        settingsStore.setDayCount(count)
        val effective = settingsStore.dayCount.first()
        ensureDaysExist(effective)
        if (selectedDayId.first() > effective) {
            selectDay(FIRST_DAY_ID)
        }
    }

    /** Legt fehlende Trainingstage an; vorhandene bleiben samt Namen unangetastet. */
    private suspend fun ensureDaysExist(count: Int) {
        val existing = dayDao.listAll().map { it.id }.toSet()
        val missing = (FIRST_DAY_ID..count).filterNot { it in existing }
        if (missing.isEmpty()) return
        dayDao.insertAll(
            missing.map { id -> TrainingDay(id = id, name = appContext.getString(R.string.day_name, id)) }
        )
    }

    suspend fun renameDay(dayId: Int, name: String) = dayDao.updateName(dayId, name)

    /**
     * Entfernt einen einzelnen Punkt aus dem Gewichtsverlauf.
     *
     * Nur der Verlauf ändert sich, nicht das eingetragene Gewicht der Übung: Der Punkt ist
     * eine Aufzeichnung von damals, das Gewicht der Stand von heute. Wer das aktuelle Gewicht
     * ändern will, tut das in der Trainingsliste.
     */
    suspend fun deleteWeightLog(id: Long) = weightLogDao.deleteById(id)

    suspend fun setHiddenTrackingNames(names: Set<String>) =
        settingsStore.setHiddenTrackingNames(names)

    /**
     * Löscht eine Übung restlos: aus allen Trainingstagen, aus der Übungsdatenbank und
     * samt Gewichtsverlauf. Das lässt sich nicht rückgängig machen.
     */
    suspend fun deleteExerciseEverywhere(name: String) = deleteExercisesEverywhere(listOf(name))

    /**
     * Dasselbe für mehrere Übungen auf einmal – alles in einer Transaktion, damit nicht die
     * halbe Auswahl verschwindet, wenn etwas dazwischenkommt.
     */
    suspend fun deleteExercisesEverywhere(names: Collection<String>) {
        if (names.isEmpty()) return
        database.withTransaction {
            // Alle Namen in einem Rutsch statt vier Abfragen je Übung – aus der Verwaltung
            // kommt hier gern ein Dutzend Namen auf einmal an.
            val affectedDays = exerciseDao.listDayIdsForNames(names)
            exerciseDao.deleteByNames(names)
            definitionDao.deleteByNames(names)
            weightLogDao.deleteByNames(names)
            affectedDays.distinct().forEach { normalizeSupersets(it) }
        }
        // Sonst blieben die Namen im Tracking ausgeblendet und später neu angelegte
        // Übungen gleichen Namens wären von Anfang an unsichtbar.
        val hidden = settingsStore.hiddenTrackingNames.first()
        val remaining = hidden - names.toSet()
        if (remaining.size != hidden.size) settingsStore.setHiddenTrackingNames(remaining)
    }

    /**
     * Der Start ruft das Weiterschalten zweimal an: einmal beim Anlegen des ViewModels, einmal
     * beim ersten `ON_RESUME` unmittelbar danach. Ohne Sperre lesen beide den Vermerk, bevor
     * einer ihn schreibt – die Prüfung „höchstens einmal pro Kalendertag“ liefe ins Leere.
     */
    private val advanceLock = Mutex()

    /**
     * Schaltet beim ersten Start an einem neuen Kalendertag auf den Tag nach dem zuletzt
     * abgehakten weiter – wer gestern Tag 1 gemacht hat, sieht heute Tag 2.
     *
     * Läuft höchstens einmal pro Tag, damit eine Auswahl von Hand nicht wieder umspringt.
     */
    suspend fun advanceDayIfNewDate(today: LocalDate = LocalDate.now()) = advanceLock.withLock {
        val epochDay = today.toEpochDay()
        if (settingsStore.lastDayAdvance() >= epochDay) return@withLock

        val latest = sessionDao.latest()
        // Am Tag des Trainings selbst bleibt die Ansicht stehen.
        if (latest != null && latest.completedAt.toLocalDate().isBefore(today)) {
            selectDay(nextDayId(latest.dayId, settingsStore.dayCount.first()))
        }
        // Erst hinterher vermerken: Bricht der Vorgang vorher ab – Prozess beendet, Coroutine
        // abgebrochen –, wäre der Tag sonst als erledigt markiert, ohne dass etwas geschah,
        // und die App bliebe bis morgen auf dem falschen Trainingstag stehen.
        settingsStore.setLastDayAdvance(epochDay)
    }

    /** Wählt den Tag aus und schreibt ihn nach DataStore. */
    suspend fun selectDay(dayId: Int) {
        selectDayNow(dayId)
        settingsStore.setSelectedDayId(dayId)
    }

    /**
     * Der aktuell ausgewählte Tag.
     *
     * Für Aktionen, die ihn sofort brauchen – abhaken, sortieren, Superset bilden. Der Umweg
     * über den angezeigten Zustand wäre falsch: Der wird erst eine Runde später nachgezogen,
     * und direkt nach einem Tageswechsel steht dort noch der vorige Tag.
     *
     * Liegt eine Auswahl aus dieser Sitzung vor, kommt sie ohne Umweg aus dem Speicher; nur
     * beim allerersten Zugriff wird überhaupt in den Einstellungen nachgesehen.
     */
    suspend fun currentSelectedDay(): Int =
        selectedDayOverride.value ?: settingsStore.selectedDayId.first()

    suspend fun findExercise(id: Long): ExerciseItem? = exerciseDao.findById(id)

    /**
     * Legt eine Übung an oder aktualisiert sie.
     *
     * [weightKg] und [progressionStepKg] landen in der gemeinsamen Definition und gelten damit
     * an *allen* Tagen, an denen [name] vorkommt. Sätze, Wiederholungen und [variation] bleiben
     * bei dieser einen Zeile. Ein geändertes Gewicht wandert zusätzlich in den Verlauf.
     *
     * Ein leeres Gewichtsfeld – [weightKg] ist dann `null` – lässt den geteilten Wert stehen,
     * statt ihn zu löschen: Er gilt an allen Tagen, an denen die Übung vorkommt, und wäre sonst
     * mit einem versehentlich geleerten Feld überall weg. Der Verlauf erführe davon nicht
     * einmal etwas, weil sich nur gesetzte Gewichte aufzeichnen lassen – Liste und Graph
     * zeigten anschließend Verschiedenes. Wer die Übung samt Gewicht loswerden will, löscht sie.
     */
    suspend fun saveExercise(
        id: Long?,
        dayId: Int,
        name: String,
        variation: String?,
        weightKg: Double?,
        sets: Int?,
        repsMin: Int?,
        repsMax: Int?,
        progressionStepKg: Double
    ) {
        database.withTransaction {
            // Zuerst prüfen, ob es die zu ändernde Zeile überhaupt noch gibt – sonst bliebe
            // beim Abbruch eine schon geschriebene Definition samt Verlaufseintrag zurück.
            val existing = id?.let { exerciseDao.findEntityById(it) }
            if (id != null && existing == null) return@withTransaction

            val previous = definitionDao.find(name)
            val effectiveWeight = weightKg ?: previous?.weightKg
            definitionDao.upsert(
                ExerciseDefinition(
                    name = name,
                    weightKg = effectiveWeight,
                    progressionStepKg = progressionStepKg
                )
            )
            if (effectiveWeight != null && effectiveWeight != previous?.weightKg) {
                logWeight(name, effectiveWeight)
            }

            if (existing == null) {
                exerciseDao.insert(
                    Exercise(
                        dayId = dayId,
                        name = name,
                        variation = variation,
                        sets = sets,
                        repsMin = repsMin,
                        repsMax = repsMax,
                        position = exerciseDao.nextPosition(dayId)
                    )
                )
            } else {
                exerciseDao.update(
                    existing.copy(
                        name = name,
                        variation = variation,
                        sets = sets,
                        repsMin = repsMin,
                        repsMax = repsMax
                    )
                )
            }
            // Ein umbenannter letzter Eintrag kann die alte Definition verwaist zurücklassen.
            definitionDao.deleteOrphans()
        }
    }

    /**
     * Erhöht die Last der Übung um ihren Progressionsschritt – an jedem Tag, an dem sie
     * vorkommt – und hält die Änderung im Verlauf fest.
     *
     * Gerechnet wird auf dem gespeicherten Stand, nicht auf einem von der Oberfläche
     * mitgegebenen Wert: Zwei schnelle Drücke auf den Pfeil lesen sonst beide dieselbe noch
     * nicht nachgezogene Anzeige, erhöhen zweimal auf dasselbe Ergebnis und schreiben zwei
     * gleiche Punkte in den Verlauf – der zweite Druck bliebe wirkungslos, der Graph bekäme
     * trotzdem einen Ausreißer. Innerhalb der Transaktion sieht der zweite Druck das Ergebnis
     * des ersten.
     *
     * Liefert `null`, wenn die Übung kein Gewicht hat; dann gibt es nichts zu erhöhen.
     */
    suspend fun progressWeight(name: String): WeightChange? = database.withTransaction {
        val definition = definitionDao.find(name) ?: return@withTransaction null
        val current = definition.weightKg ?: return@withTransaction null
        val next = increaseWeight(current, definition.progressionStepKg)
        definitionDao.updateWeight(name, next)
        logWeight(name, next)
        WeightChange(previousKg = current, newKg = next)
    }

    /**
     * Nimmt eine Erhöhung zurück: Das Gewicht geht auf den alten Wert und der eben
     * geschriebene Verlaufseintrag verschwindet wieder – sonst zeigte der Graph
     * einen Ausschlag nach oben und sofort wieder zurück.
     */
    suspend fun revertWeight(name: String, weightKg: Double?) = database.withTransaction {
        definitionDao.updateWeight(name, weightKg)
        weightLogDao.deleteLatest(name)
    }

    /**
     * Löscht die Zeilen. War es die letzte Zeile mit einem Namen, verschwindet die Übung
     * auch aus der Datenbank und damit aus den Vorschlägen. Der Gewichtsverlauf bleibt
     * erhalten – er ist die wertvollste Information und wäre sonst unwiederbringlich weg.
     */
    suspend fun deleteExercises(items: List<ExerciseItem>) = database.withTransaction {
        exerciseDao.deleteByIds(items.map { it.id })
        definitionDao.deleteOrphans()
        items.map { it.dayId }.distinct().forEach { normalizeSupersets(it) }
    }

    /**
     * Stellt gelöschte Übungen mit ihrer ursprünglichen id, Position und Gewicht wieder her.
     *
     * Die Definition wird nur angelegt, wenn sie beim Löschen als verwaist mit verschwunden
     * ist. Stand die Übung noch an einem anderen Tag, lebt ihre Definition weiter – und ein
     * Überschreiben nähme dort eine inzwischen erfolgte Gewichtserhöhung zurück, ohne den
     * zugehörigen Verlaufseintrag mitzunehmen. Liste und Graph zeigten dann Verschiedenes.
     */
    suspend fun restoreExercises(items: List<ExerciseItem>) = database.withTransaction {
        items.forEach { item ->
            definitionDao.insertIfAbsent(item.toDefinition())
            exerciseDao.insert(item.toExercise())
        }
        items.map { it.dayId }.distinct().forEach { normalizeSupersets(it) }
    }

    /**
     * Schreibt die Reihenfolge nach dem Umsortieren zurück: [orderedIds] enthält die Übungen
     * eines Tages in der gewünschten Reihenfolge, die Positionen werden auf 0..n-1 normalisiert.
     * Alles in einer Transaktion, damit die Liste nie in einem halb sortierten Zustand auftaucht.
     */
    suspend fun reorderExercises(dayId: Int, orderedIds: List<Long>) = database.withTransaction {
        orderedIds.forEachIndexed { index, id -> exerciseDao.updatePosition(id, index) }
        normalizeSupersets(dayId)
    }

    /**
     * Fasst die ausgewählten Übungen zu einem Superset zusammen. Sie rücken dafür an die
     * Position der obersten Ausgewählten zusammen, denn ein Superset ist nur als
     * zusammenhängender Block sinnvoll.
     */
    suspend fun createSuperset(dayId: Int, ids: Set<Long>) = database.withTransaction {
        val ordered = exerciseDao.listByDay(dayId)
        val selected = ordered.filter { it.id in ids }
        if (selected.size < MIN_SUPERSET_SIZE) return@withTransaction

        val supersetId = exerciseDao.nextSupersetId()
        // Alle Einträge vor der ersten Ausgewählten sind nicht ausgewählt, deshalb passt
        // dieser Index auch in der Liste ohne die ausgewählten Einträge.
        val insertAt = ordered.indexOfFirst { it.id in ids }
        val rearranged = ordered.filterNot { it.id in ids }.toMutableList()
        rearranged.addAll(insertAt, selected)

        rearranged.forEachIndexed { index, exercise -> exerciseDao.updatePosition(exercise.id, index) }
        selected.forEach { exerciseDao.updateSuperset(it.id, supersetId) }
        normalizeSupersets(dayId)
    }

    /** Hebt die Superset-Zugehörigkeit der ausgewählten Übungen auf. */
    suspend fun dissolveSuperset(dayId: Int, ids: Set<Long>) = database.withTransaction {
        ids.forEach { exerciseDao.updateSuperset(it, null) }
        normalizeSupersets(dayId)
    }

    /**
     * Räumt Supersets auf, nachdem sich die Reihenfolge geändert hat. Welche Mitglieder
     * zusammenbleiben, entscheidet [survivingSupersetMembers]; hier wird das Ergebnis nur noch
     * in die Datenbank geschrieben.
     */
    private suspend fun normalizeSupersets(dayId: Int) {
        val ordered = exerciseDao.listByDay(dayId)
        val surviving = survivingSupersetMembers(
            orderedIds = ordered.map { it.id },
            supersetIds = ordered.map { it.supersetId }
        )
        ordered.filter { it.supersetId != null && it.id !in surviving }
            .forEach { exerciseDao.updateSuperset(it.id, null) }
    }

    private suspend fun logWeight(name: String, weightKg: Double) {
        weightLogDao.insert(
            WeightLog(
                exerciseName = name,
                weightKg = weightKg,
                recordedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Setzt die App auf den Zustand direkt nach der Installation zurück.
     *
     * Weg sind: der Verlauf abgehakter Trainings, der komplette Gewichtsverlauf, alle Übungen
     * samt ihrer geteilten Werte, die Namen der Trainingstage und sämtliche Einstellungen –
     * also alles, was die App je über das Training gesammelt hat.
     *
     * Übrig bleiben die leeren Trainingstage, genau wie nach der Installation. Das lässt sich
     * nicht rückgängig machen.
     */
    suspend fun deleteAllData() {
        database.withTransaction {
            weightLogDao.deleteAll()
            sessionDao.deleteAll()
            exerciseDao.deleteAll()
            definitionDao.deleteAll()
            dayDao.deleteAll()
        }
        settingsStore.clear()
        // Sonst überstimmte die Vormerkung aus dieser Sitzung die geleerten Einstellungen und
        // die App bliebe auf einem Tag stehen, den das Zurücksetzen gerade verworfen hat.
        selectedDayOverride.value = null
        ensureSeeded()
    }

    /**
     * Legt die Trainingstage an, falls sie fehlen.
     *
     * Mehr passiert beim ersten Start bewusst nicht: Die App beginnt leer. Ein mitgelieferter
     * Plan wäre fremdes Training – die Übungen trägt jeder selbst ein.
     */
    suspend fun ensureSeeded() {
        ensureDaysExist(settingsStore.dayCount.first())
    }
}
