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
import de.beispiel.meintraining.util.RotationEntry
import de.beispiel.meintraining.util.canUndoRotationCut
import de.beispiel.meintraining.util.completedDaysInRotation
import de.beispiel.meintraining.util.decreaseWeight
import de.beispiel.meintraining.util.increaseWeight
import de.beispiel.meintraining.util.nextDayId
import de.beispiel.meintraining.util.rotations
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

/**
 * Ergebnis einer Gewichtsänderung – je nach Richtung der Übung eine Erhöhung oder eine Senkung.
 *
 * [previousKg] ist der Stand davor, [logId] der dabei geschriebene Verlaufseintrag – beides
 * zusammen macht die Änderung rücknehmbar, ohne dabei zu raten (siehe [TrainingRepository.revertWeight]).
 */
data class WeightChange(val previousKg: Double, val newKg: Double, val logId: Long)

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
        // Rundenlänge und Rundenschnitte stehen in DataStore und werden deshalb *vor* der
        // Transaktion geholt: Room hat genau einen Transaktions-Thread, und ein Warten auf einen
        // anderen Zufluss mitten drin blockiert ihn – im schlechtesten Fall, bis DataStore
        // seinerseits auf die Platte wartet.
        val dayCount = settingsStore.dayCount.first()
        val cuts = settingsStore.rotationCuts.first()
        return database.withTransaction {
            val latest = sessionDao.latestForDay(dayId)
            if (latest != null && latest.completedAt.toLocalDate() == today) {
                sessionDao.deleteById(latest.id)
                WorkoutToggle(isCompleted = false, completesRotation = false)
            } else {
                completeWorkout(dayId)
                WorkoutToggle(
                    isCompleted = true,
                    completesRotation = isRotationFull(dayCount, today, cuts)
                )
            }
        }
    }

    /**
     * Beginnt die nächste Runde sofort, statt sie zu Ende zu trainieren.
     *
     * Der Weg für eine Woche, in der ein Tag ausfällt: Was steht, bleibt stehen, der Rest fällt
     * weg, und die neue Runde beginnt bei Tag 1. Gelöscht wird dabei nichts – der Verlauf bleibt
     * vollständig, und mit ihm Statistik und Deload-Rechnung. Vermerkt wird nur der Schnitt:
     * Alles, was jetzt schon im Verlauf steht, gehört zur vorigen Runde (siehe [rotations]).
     *
     * Der Schnitt liegt hinter dem jüngsten Eintrag, mindestens aber im Jetzt: Ein Training, das
     * durch Zeitumstellung oder eine eingelesene Sicherung in der Zukunft gelandet ist, würde
     * sonst in der neuen Runde weiterleben und den ersten Tag von vornherein als erledigt zeigen.
     *
     * Liefert `false`, wenn die laufende Runde ohnehin leer ist: Dann gibt es nichts
     * abzuschließen, und ein Schnitt legte nur eine leere Runde zwischen zwei andere.
     */
    suspend fun startNextRotation(today: LocalDate = LocalDate.now()): Boolean {
        val dayCount = settingsStore.dayCount.first()
        val cuts = settingsStore.rotationCuts.first()
        val entries = rotationEntries()
        if (rotations(entries, dayCount, today, cuts).last().isEmpty) return false
        val latest = entries.last().completedAt
        settingsStore.setRotationCuts(cuts + maxOf(System.currentTimeMillis(), latest))
        return true
    }

    /**
     * Nimmt den jüngsten Schnitt zurück – die vorige Runde steht wieder da, wo sie aufgehört hat.
     *
     * Der Weg zurück aus einem Fehlgriff auf den Pfeil. Möglich, solange in der neuen Runde noch
     * nicht trainiert wurde (siehe [canUndoRotationCut]); liefert sonst `false`.
     */
    suspend fun returnToPreviousRotation(): Boolean {
        val cuts = settingsStore.rotationCuts.first()
        if (!canUndoRotationCut(rotationEntries(), cuts)) return false
        settingsStore.setRotationCuts(cuts.dropLast(1))
        return true
    }

    /**
     * Der Tag, der in der laufenden Runde als nächstes dran ist: der auf das jüngste Training
     * folgende. In einer noch leeren Runde ist das der erste Tag.
     */
    suspend fun nextDayInRotation(today: LocalDate = LocalDate.now()): Int {
        val dayCount = settingsStore.dayCount.first()
        val entries = rotationEntries()
        val latest = latestEntryInRotation(entries, dayCount, today) ?: return FIRST_DAY_ID
        return nextDayId(latest.dayId, dayCount)
    }

    /** Der ganze Verlauf als Rundeneinträge, ältester zuerst – so, wie [rotations] ihn braucht. */
    private suspend fun rotationEntries(): List<RotationEntry> = sessionDao.listAll().map { session ->
        RotationEntry(
            dayId = session.dayId,
            date = session.completedAt.toLocalDate(),
            completedAt = session.completedAt
        )
    }

    /** Das jüngste Training der laufenden Runde; `null`, solange sie leer ist. */
    private suspend fun latestEntryInRotation(
        entries: List<RotationEntry>,
        dayCount: Int,
        today: LocalDate
    ): RotationEntry? {
        val cuts = settingsStore.rotationCuts.first()
        val current = rotations(entries, dayCount, today, cuts).last()
        return current.entryIndices.lastOrNull()?.let(entries::get)
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
        cuts: List<Long>
    ): Boolean {
        if (dayCount <= 0) return false
        return completedDaysInRotation(rotationEntries(), dayCount, today, cuts).size >= dayCount
    }

    suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)

    /** Im Tracking ausgeblendete Übungen. */
    val hiddenTrackingNames: Flow<Set<String>> = settingsStore.hiddenTrackingNames

    /** An den Trainingstagen ausgeblendete Übungen – siehe [SettingsStore.hiddenExerciseNames]. */
    val hiddenExerciseNames: Flow<Set<String>> = settingsStore.hiddenExerciseNames

    /**
     * Blendet eine Übung an allen Trainingstagen aus oder wieder ein.
     *
     * Gelöscht wird dabei nichts: Zeile, geteilte Werte und Gewichtsverlauf bleiben, wo sie sind
     * – und stehen beim Einblenden unverändert wieder da, samt Position im Tag.
     */
    suspend fun setExerciseHidden(name: String, hidden: Boolean) {
        val current = settingsStore.hiddenExerciseNames.first()
        val updated = if (hidden) current + name else current - name
        if (updated != current) settingsStore.setHiddenExerciseNames(updated)
    }

    /** Die von Hand gezogenen Rundenschnitte – siehe [startNextRotation]. */
    val rotationCuts: Flow<List<Long>> = settingsStore.rotationCuts

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
     * Löscht Übungen restlos: aus allen Trainingstagen, aus der Übungsdatenbank und samt
     * Gewichtsverlauf. Das lässt sich nicht rückgängig machen.
     *
     * Alles in einer Transaktion, damit nicht die halbe Auswahl verschwindet, wenn etwas
     * dazwischenkommt.
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
        // Sonst blieben die Namen ausgeblendet und später neu angelegte Übungen gleichen
        // Namens wären von Anfang an unsichtbar – im Graphen wie an ihrem Trainingstag.
        val gone = names.toSet()
        dropNames(settingsStore.hiddenTrackingNames.first(), gone, settingsStore::setHiddenTrackingNames)
        dropNames(settingsStore.hiddenExerciseNames.first(), gone, settingsStore::setHiddenExerciseNames)
    }

    /** Nimmt die gelöschten Namen aus einer Ausblendliste; schreibt nur, wenn welche darin standen. */
    private suspend fun dropNames(
        hidden: Set<String>,
        gone: Set<String>,
        write: suspend (Set<String>) -> Unit
    ) {
        val remaining = hidden - gone
        if (remaining.size != hidden.size) write(remaining)
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
     * Gezählt wird dabei nur innerhalb der *laufenden* Runde. Über deren Grenze hinweg wäre es
     * falsch: Wer die Runde am letzten Tag übersprungen hat, säße am nächsten Morgen wieder auf
     * genau dem Tag, den er gerade weggeklickt hat. Eine frisch begonnene Runde beginnt bei Tag 1.
     *
     * Läuft höchstens einmal pro Tag, damit eine Auswahl von Hand nicht wieder umspringt.
     */
    suspend fun advanceDayIfNewDate(today: LocalDate = LocalDate.now()) = advanceLock.withLock {
        val epochDay = today.toEpochDay()
        if (settingsStore.lastDayAdvance() >= epochDay) return@withLock

        val entries = rotationEntries()
        // Ohne einen einzigen Eintrag gibt es nichts weiterzuschalten – die Auswahl bleibt, wo
        // sie ist, statt beim ersten Start auf Tag 1 zu springen.
        if (entries.isNotEmpty()) {
            val dayCount = settingsStore.dayCount.first()
            val latest = latestEntryInRotation(entries, dayCount, today)
            when {
                // Eine neue Runde beginnt bei Tag 1.
                latest == null -> selectDay(FIRST_DAY_ID)
                // Am Tag des Trainings selbst bleibt die Ansicht stehen.
                latest.date.isBefore(today) -> selectDay(nextDayId(latest.dayId, dayCount))
            }
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
     * [weightKg], [progressionStepKg] und [progressionDown] landen in der gemeinsamen Definition
     * und gelten damit an *allen* Tagen, an denen [name] vorkommt. Sätze, Wiederholungen und
     * [variation] bleiben bei dieser einen Zeile. Ein geändertes Gewicht wandert zusätzlich in
     * den Verlauf.
     *
     * Ein leeres Gewichtsfeld – [weightKg] ist dann `null` – lässt den geteilten Wert stehen,
     * statt ihn zu löschen: Er gilt an allen Tagen, an denen die Übung vorkommt, und wäre sonst
     * mit einem versehentlich geleerten Feld überall weg. Der Verlauf erführe davon nicht
     * einmal etwas, weil sich nur gesetzte Gewichte aufzeichnen lassen – Liste und Graph
     * zeigten anschließend Verschiedenes. Wer die Übung samt Gewicht loswerden will, löscht sie.
     *
     * Wird die letzte Zeile eines Namens auf einen noch unbekannten umbenannt, zieht der
     * Gewichtsverlauf mit um – siehe [renameHistory].
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
        progressionStepKg: Double,
        progressionDown: Boolean
    ) {
        val renamedFrom = database.withTransaction {
            // Zuerst prüfen, ob es die zu ändernde Zeile überhaupt noch gibt – sonst bliebe
            // beim Abbruch eine schon geschriebene Definition samt Verlaufseintrag zurück.
            val existing = id?.let { exerciseDao.findEntityById(it) }
            if (id != null && existing == null) return@withTransaction null

            val oldName = existing?.name?.takeIf { it != name }
            val underNewName = definitionDao.find(name)
            // Beim Umbenennen zählt der Stand unter dem alten Namen als Vorgänger. Sonst wäre
            // jeder Namenswechsel für sich schon eine Gewichtsänderung und schriebe einen Punkt
            // in den Verlauf, obwohl auf der Stange dasselbe liegt wie vorher.
            val previous = underNewName ?: oldName?.let { definitionDao.find(it) }
            val effectiveWeight = weightKg ?: previous?.weightKg
            definitionDao.upsert(
                ExerciseDefinition(
                    name = name,
                    weightKg = effectiveWeight,
                    progressionStepKg = progressionStepKg,
                    progressionDown = progressionDown
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
            renameHistory(oldName = oldName, newName = name, wasKnown = underNewName != null)
        }
        // Außerhalb der Transaktion, weil die Einstellungen in einer eigenen Datei liegen: Eine
        // ausgeblendete Übung bleibt auch unter ihrem neuen Namen ausgeblendet, und der alte Name
        // verschwindet – sonst wäre eine später neu angelegte Übung gleichen Namens von Anfang
        // an unsichtbar. Das gilt für beide Ausblendlisten, im Graphen wie am Trainingstag.
        if (renamedFrom != null) {
            renameName(
                settingsStore.hiddenTrackingNames.first(),
                renamedFrom,
                name,
                settingsStore::setHiddenTrackingNames
            )
            renameName(
                settingsStore.hiddenExerciseNames.first(),
                renamedFrom,
                name,
                settingsStore::setHiddenExerciseNames
            )
        }
    }

    /** Zieht einen umbenannten Namen in einer Ausblendliste mit; schreibt nur, wenn er darin stand. */
    private suspend fun renameName(
        hidden: Set<String>,
        from: String,
        to: String,
        write: suspend (Set<String>) -> Unit
    ) {
        if (from in hidden) write(hidden - from + to)
    }

    /**
     * Schreibt den Gewichtsverlauf auf den neuen Namen um, wenn aus einer Übung schlicht eine
     * anders heißende geworden ist. Liefert den alten Namen, falls das passiert ist.
     *
     * Bedingung ist, dass unter dem alten Namen nichts mehr steht *und* der neue vorher
     * unbekannt war. Beides zusammen heißt: Es ist dieselbe Übung, sie heißt nur anders – und
     * ihre Kurve gehört zusammen, statt am Namenswechsel in zwei Stücke zu zerfallen.
     *
     * Ausdrücklich nicht umgeschrieben wird, wenn der neue Name schon eine Übung war
     * ([wasKnown]): Dann werden zwei Übungen zusammengelegt, und deren Verläufe ineinander zu
     * schieben ergäbe eine Kurve, die zwischen zwei verschiedenen Lasten hin und her springt.
     * Der alte Verlauf bleibt dann unter seinem Namen stehen und ist im Tracking weiter zu
     * sehen – rückgängig zu machen durch erneutes Umbenennen.
     */
    private suspend fun renameHistory(oldName: String?, newName: String, wasKnown: Boolean): String? {
        val orphaned = oldName?.takeIf { !wasKnown && exerciseDao.countByName(it) == 0 }
        orphaned?.let { weightLogDao.renameExercise(oldName = it, newName = newName) }
        // Ein umbenannter letzter Eintrag lässt die alte Definition verwaist zurück.
        definitionDao.deleteOrphans()
        return orphaned
    }

    /**
     * Verschiebt die Last der Übung um ihren Progressionsschritt – an jedem Tag, an dem sie
     * vorkommt – und hält die Änderung im Verlauf fest.
     *
     * In welche Richtung, sagt die Übung selbst
     * ([ExerciseDefinition.progressionDown][de.beispiel.meintraining.data.model.ExerciseDefinition.progressionDown]):
     * Beim Aufbau geht es nach oben, bei allem, was sich abtrainiert – etwa Unterstützung an der
     * Klimmzugmaschine –, nach unten.
     *
     * Gerechnet wird auf dem gespeicherten Stand, nicht auf einem von der Oberfläche
     * mitgegebenen Wert: Zwei schnelle Drücke auf den Pfeil lesen sonst beide dieselbe noch
     * nicht nachgezogene Anzeige, rechnen zweimal dasselbe Ergebnis aus und schreiben zwei
     * gleiche Punkte in den Verlauf – der zweite Druck bliebe wirkungslos, der Graph bekäme
     * trotzdem einen Ausreißer. Innerhalb der Transaktion sieht der zweite Druck das Ergebnis
     * des ersten.
     *
     * Liefert `null`, wenn die Übung kein Gewicht hat oder sich nichts ändern würde – letzteres
     * bei 0 kg und einem Pfeil nach unten, denn tiefer geht es nicht (siehe [decreaseWeight]).
     * Ein Verlaufspunkt, der denselben Wert noch einmal festhält, entsteht so nicht.
     */
    suspend fun progressWeight(name: String): WeightChange? = database.withTransaction {
        val definition = definitionDao.find(name) ?: return@withTransaction null
        val current = definition.weightKg ?: return@withTransaction null
        val next = if (definition.progressionDown) {
            decreaseWeight(current, definition.progressionStepKg)
        } else {
            increaseWeight(current, definition.progressionStepKg)
        }
        if (next == current) return@withTransaction null
        definitionDao.updateWeight(name, next)
        WeightChange(previousKg = current, newKg = next, logId = logWeight(name, next))
    }

    /**
     * Nimmt eine Änderung zurück: Das Gewicht geht auf den alten Wert und der dabei
     * geschriebene Verlaufseintrag verschwindet wieder – sonst zeigte der Graph
     * einen Ausschlag und sofort wieder zurück.
     *
     * Zurückgenommen wird genau *diese* Änderung, erkennbar an [WeightChange.logId], und nur
     * solange sie noch der aktuelle Stand ist. Beides ist nötig, weil zwischen Änderung und
     * „Rückgängig“ die nächste liegen kann – der Pfeil ist schneller angetippt, als die Meldung
     * am unteren Rand verschwindet:
     *
     * Ohne die Kennung träfe es „den jüngsten Eintrag dieser Übung“ und damit den der *zweiten*
     * Änderung; zurück bliebe ein Punkt im Graphen für eine Änderung, die zurückgenommen wurde.
     * Ohne die Prüfung auf den aktuellen Stand setzte das Zurücknehmen der ersten Änderung das
     * Gewicht auf den Stand von vor beiden – und die zweite stünde als Punkt ohne Gewicht da.
     *
     * Liefert `false`, wenn seither etwas anderes passiert ist; dann gibt es hier nichts mehr
     * zurückzunehmen. Verglichen wird exakt: Der Wert kommt unverändert aus derselben Spalte
     * zurück, in die er geschrieben wurde.
     */
    suspend fun revertWeight(
        name: String,
        previousKg: Double,
        changedToKg: Double,
        logId: Long
    ): Boolean = database.withTransaction {
        if (definitionDao.find(name)?.weightKg != changedToKg) return@withTransaction false
        definitionDao.updateWeight(name, previousKg)
        weightLogDao.deleteById(logId)
        true
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
     *
     * Ausgeblendete Übungen kommen dabei nicht mit – sie stehen ja in keiner Liste, die sich
     * schieben ließe. Sie behalten ihren Platz *zwischen* den sichtbaren: Durchnummeriert wird der
     * ganze Tag, und die Plätze der sichtbaren Zeilen bekommen die neue Reihenfolge. Nur die
     * sichtbaren neu zu nummerieren wäre nicht genug – die Ausgeblendeten behielten ihre alten
     * Nummern, läge damit doppelt und rutschten beim Einblenden irgendwohin.
     */
    suspend fun reorderExercises(dayId: Int, orderedIds: List<Long>) = database.withTransaction {
        val existing = exerciseDao.listByDay(dayId).map { it.id }
        val moved = orderedIds.filter { it in existing }
        val nextMoved = moved.iterator()
        val complete = existing.map { id -> if (id in moved) nextMoved.next() else id }
        complete.forEachIndexed { index, id -> exerciseDao.updatePosition(id, index) }
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

    /** Schreibt einen Punkt in den Verlauf und liefert seine Kennung. */
    private suspend fun logWeight(name: String, weightKg: Double): Long = weightLogDao.insert(
        WeightLog(
            exerciseName = name,
            weightKg = weightKg,
            recordedAt = System.currentTimeMillis()
        )
    )

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
