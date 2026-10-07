package de.beispiel.meintraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.repository.TrainingRepository
import de.beispiel.meintraining.ui.components.SetsProgress
import de.beispiel.meintraining.util.CurrentDate
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.LastCardioEntry
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.RotationEntry
import de.beispiel.meintraining.util.SetLogKey
import de.beispiel.meintraining.util.SetUnit
import de.beispiel.meintraining.util.WeightHistory
import de.beispiel.meintraining.util.canUndoRotationCut
import de.beispiel.meintraining.util.cardioLogsByExercise
import de.beispiel.meintraining.util.completedDaysInRotation
import de.beispiel.meintraining.util.deloadStatus
import de.beispiel.meintraining.util.isTopOfRangeReached
import de.beispiel.meintraining.util.lastCardioEntry
import de.beispiel.meintraining.util.lastCardioEntryBefore
import de.beispiel.meintraining.util.lastUnit
import de.beispiel.meintraining.util.Milestone
import de.beispiel.meintraining.util.milestones
import de.beispiel.meintraining.util.milestonesToCelebrate
import de.beispiel.meintraining.util.parseOptionalDecimal
import de.beispiel.meintraining.util.parseOptionalInt
import de.beispiel.meintraining.util.parseProgressionStep
import de.beispiel.meintraining.util.setUnitsByExercise
import de.beispiel.meintraining.util.setsThisWeek
import de.beispiel.meintraining.util.stepWeight
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toLocalDate
import de.beispiel.meintraining.util.todaysCardioLog
import de.beispiel.meintraining.util.todaysUnit
import de.beispiel.meintraining.util.weightHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrainingViewModel(
    private val repository: TrainingRepository,
    private val currentDate: CurrentDate
) : ViewModel() {

    private val formState = MutableStateFlow<ExerciseForm?>(null)

    /**
     * Das Bearbeiten-Sheet steht bewusst neben [uiState] statt darin.
     *
     * Es ändert sich bei jedem Tastendruck. Läge es im selben `combine`, liefe mit jedem
     * Buchstaben die ganze abgeleitete Rechnung erneut – Deload-Zyklus samt Sortieren aller
     * Trainingstermine, Rotation, Tagesliste – und der Hauptscreen würde dabei jedes Mal neu
     * zusammengesetzt, obwohl sich dort nichts geändert hat.
     */
    val editorForm: StateFlow<ExerciseForm?> = formState.asStateFlow()

    /**
     * Der Gewichtsverlauf zur Übung im offenen Sheet – für die Zeile unter dem Gewichtsfeld.
     *
     * Gemeint ist die Übung, deren Werte gerade in den Feldern stehen: eine andere bekannte,
     * sobald der getippte Name auf sie passt, sonst die bearbeitete selbst – deren Verlauf zieht
     * beim Umbenennen mit. Eine neue Übung ohne Treffer hat noch keinen.
     *
     * Abgefragt wird nur bei einem Wechsel dieser Übung, nicht bei jedem Tastendruck, und nur
     * ihr Verlauf statt des ganzen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weightHistory: StateFlow<WeightHistory?> = formState
        .map { form -> form?.let { it.matchedName ?: it.originalName } }
        .distinctUntilChanged()
        .flatMapLatest { name ->
            if (name == null) {
                flowOf(null)
            } else {
                combine(repository.observeWeightLogs(name), currentDate.flow) { logs, today ->
                    weightHistory(logs, today)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Die letzte eingetragene Einheit zur Cardio-Übung im offenen Sheet – die Zeile, die bei
     * Cardio an der Stelle des Gewichtsverlaufs steht.
     *
     * Gemeint ist dieselbe Übung wie bei [weightHistory], dazu ihre Variation: Die Einheiten
     * halten Variationen auseinander (siehe
     * [CardioLog][de.beispiel.meintraining.data.model.CardioLog]). Nur solange „Cardio“ gewählt ist;
     * abgefragt wird wie dort nur bei einem Wechsel der Übung.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lastCardioEntry: StateFlow<LastCardioEntry?> = formState
        .map { form ->
            form?.takeIf { it.isCardio }?.let { cardioForm ->
                (cardioForm.matchedName ?: cardioForm.originalName)?.let { name ->
                    name to cardioForm.variation.trim().takeIf { cardioForm.showVariation && it.isNotEmpty() }
                }
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(null)
            } else {
                combine(
                    repository.observeCardioLogs(key.first, key.second),
                    currentDate.flow
                ) { logs, today -> lastCardioEntry(logs, today) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Die Zeile, deren Satz-Protokoll offen ist; `null`: keines. */
    private val setLogTargetId = MutableStateFlow<Long?>(null)

    /** Die Cardio-Zeile, deren Dialog „Cardio eintragen“ offen ist; `null`: keiner. */
    private val cardioLogTargetId = MutableStateFlow<Long?>(null)

    private val eventChannel = Channel<TrainingEvent>(Channel.BUFFERED)
    val events: Flow<TrainingEvent> = eventChannel.receiveAsFlow()

    /**
     * Der Applaus für eine volle Runde – ein Signal ohne Inhalt, der Bildschirm weiß selbst,
     * was er damit anfängt.
     *
     * Bewusst kein [TrainingEvent]: Die tragen Meldungen samt „Rückgängig“ am unteren Rand,
     * und genau das soll hier nicht passieren. `CONFLATED` statt gepuffert, weil ein
     * nachgeholter Konfetti-Regen niemandem mehr etwas sagt – lag der Bildschirm währenddessen
     * im Hintergrund, ist der Moment vorbei.
     */
    private val celebrationChannel = Channel<Unit>(Channel.CONFLATED)
    val celebrations: Flow<Unit> = celebrationChannel.receiveAsFlow()

    /**
     * Neu erreichte Meilensteine – Konfetti und eine kurze Meldung oben (siehe [milestones]).
     *
     * Nicht an den einzelnen Stellen ausgelöst, an denen etwas erreicht werden kann – Haken,
     * Pfeil, Sheet, „Cardio eintragen“, ein nachgetragenes Training –, sondern aus dem Bestand
     * selbst: Jede Änderung rechnet die erreichten Meilensteine neu, und was davon noch nicht
     * gemerkt war, wird gemerkt und – wenn es heute erreicht wurde – gefeiert (siehe
     * [milestonesToCelebrate]). So vergisst keine neue Stelle das Feiern.
     *
     * Kalt und ohne eigenes Abonnement: Gerechnet wird nur, solange der Bildschirm sammelt, und
     * der sammelt nur im Vordergrund. Was im Hintergrund erreicht wurde, kommt beim Zurückkehren.
     * Gerechnet wird abseits des Hauptthreads – es geht durch den ganzen Verlauf.
     *
     * Gemerkt wird schon beim Erreichen, nicht erst nach dem Feiern. Nimmt „Rückgängig“ die
     * Erhöhung zurück, bleibt der Meilenstein deshalb gefeiert, und die nächste Erhöhung auf
     * dasselbe Gewicht bekommt kein zweites Konfetti.
     */
    val milestoneCelebrations: Flow<List<Milestone>> =
        combine(repository.observeMilestoneData(), currentDate.flow) { data, today ->
            val reached = milestones(data, today).reached
            val fresh = repository.markMilestonesCelebrated(reached.mapTo(HashSet()) { it.milestone.id })
            milestonesToCelebrate(reached, fresh, today)
        }
            .flowOn(Dispatchers.Default)
            .filter { it.isNotEmpty() }

    /**
     * Soll der Bildschirm anbleiben? Nicht Teil von [uiState]: Das betrifft das Fenster der
     * Activity, nicht den Inhalt – siehe MainActivity.
     */
    val keepScreenOn: Flow<Boolean> = repository.keepScreenOn

    /**
     * Alle Übungen aller Tage, einmal abonniert und im Speicher nach Tag geschnitten.
     *
     * Eine eigene Abfrage je Tag baut bei jedem Umschalten eine neue Room-Abfrage auf und
     * verwirft die alte; hin und zurück kostet das drei Abfragen, jede mit Datenbankzugriff.
     * Bei höchstens sieben Tagen mit einer Handvoll Übungen ist der ganze Bestand so klein,
     * dass ein einziges Abonnement billiger ist – und das Umschalten damit ohne Datenbank
     * auskommt.
     *
     * `shareIn` statt `stateIn` aus demselben Grund wie bei [sessions]: Ein leerer Anfangswert
     * ließe die Liste beim Öffnen für einen Frame leer erscheinen.
     *
     * Geschnitten wird erst unten in [uiState], nicht hier in einem eigenen Zufluss. Hinge der
     * ausgewählte Tag an zwei Zweigen – einem für die Reiter, einem für die Liste –, käme jeder
     * für sich beim Zusammenlegen an: `combine` sendet bei *jeder* Änderung eines Zuflusses.
     * Ein Tippen ergäbe dann zwei Zustände nacheinander, den ersten mit dem neuen Tag und noch
     * der alten Liste. Genau das sieht man als Nachziehen der Übungen unter einem schon
     * umgesprungenen Reiter.
     */
    private val allExercises = repository.observeAllExercises()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /**
     * Die bekannten Übungen samt ihrer geteilten Werte.
     *
     * `WhileSubscribed` wie überall sonst: Das Formular braucht die Werte zwar beim Tippen
     * sofort, aber es gibt das Formular nur, solange der Hauptscreen läuft – und der hält
     * dieses Abonnement über [uiState] ohnehin. Dauerhaft aktiv hinge sonst eine
     * Datenbankabfrage am Prozess, auch wenn die App längst im Hintergrund liegt.
     */
    private val definitions = repository.observeDefinitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /**
     * Einmal abgefragt und geteilt: Verlauf und Deload-Rechnung brauchen dieselben Sitzungen.
     *
     * `shareIn` statt `stateIn`, weil letzteres einen Anfangswert braucht: Mit einer leeren
     * Liste als Start liefe der Zustand einmal ohne Sitzungen durch, und die Haken an den
     * Trainingstagen blitzten beim Öffnen kurz als offen auf.
     */
    private val sessions = repository.observeSessions()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /**
     * Das Satz-Protokoll, nach Übung zerlegt und in Einheiten gefasst – einmal je Änderung und
     * nicht bei jedem Zusammensetzen der Liste, das schon eine umspringende Markierung auslöst.
     *
     * `shareIn` aus demselben Grund wie bei [sessions]: Mit einem leeren Anfangswert stünden die
     * Sätze-Chips beim Öffnen einen Moment lang leer da.
     */
    private val setLogUnits = repository.observeSetLogs()
        .map { setUnitsByExercise(it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /**
     * Die Cardio-Einheiten, nach Übung zerlegt – aus demselben Grund einmal je Änderung wie
     * [setLogUnits], und mit `shareIn`, damit der Haken am Chip beim Öffnen nicht kurz fehlt.
     */
    private val cardioLogs = repository.observeCardioLogs()
        .map { cardioLogsByExercise(it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val dayState = combine(
        repository.observeDays(),
        repository.selectedDayId
    ) { days, id -> DayState(days, id) }

    /**
     * Das Datum steht hier bewusst *nicht* mit drin, obwohl es der Hauptscreen einmal
     * durchreichte: Es hängt schon an [sessionSummary], und `combine` sendet je Zufluss
     * einzeln. Ein Tageswechsel ergäbe dann zwei Zustände nacheinander – den ersten mit dem
     * neuen Datum und noch der alten Deload-Rechnung. Angezeigt wurde das Datum ohnehin
     * nirgends; der Verlauf holt sich sein eigenes.
     */
    private val preferences = combine(
        repository.dayCount,
        repository.appTitle,
        repository.hiddenExerciseNames
    ) { dayCount, title, hiddenExercises -> Preferences(dayCount, title, hiddenExercises) }

    /**
     * Der teure Teil, getrennt gehalten: Er hängt nur an den Sitzungen, der Rundenlänge, der
     * Blocklänge und dem Datum. `combine` merkt sich den letzten Wert eines Zuflusses, deshalb
     * rechnet das hier nicht mit, wenn anderswo nur eine Markierung umspringt.
     */
    private val sessionSummary = combine(
        sessions,
        repository.dayCount,
        repository.deloadCycleWeeks,
        currentDate.flow,
        repository.rotationCuts
    ) { sessionList, dayCount, cycleWeeks, today, rotationCuts ->
        // Die Sitzungen kommen neueste zuerst; die Rotation zählt in Eintragsreihenfolge. Das
        // Datum wird dabei einmal ausgerechnet und weitergereicht: Runde und Deload-Rechnung
        // brauchen dieselben Tage, und aus einem Zeitstempel eines zu machen ist mit Zeitzone
        // und Instant der teuerste Schritt der ganzen Zusammenfassung.
        val entriesOldestFirst = sessionList.asReversed().map { session ->
            RotationEntry(
                dayId = session.dayId,
                date = session.completedAt.toLocalDate(),
                completedAt = session.completedAt
            )
        }
        SessionSummary(
            completedDayIds = completedDaysInRotation(
                entriesOldestFirst = entriesOldestFirst,
                dayCount = dayCount,
                today = today,
                // Nur die Runden hören auf die Schnitte – hier, im Verlauf und in der
                // Runden-Statistik. Serien und Deload-Rechnung gehen weiter über alles – dort ist
                // nichts zu Ende, nur eine Runde.
                cuts = rotationCuts
            ),
            canReturnToPreviousCycle = canUndoRotationCut(entriesOldestFirst, rotationCuts),
            // Neueste zuerst heißt: Was heute eingetragen wurde, steht vorn. `takeWhile` hört
            // beim ersten älteren Eintrag auf und rechnet nicht den ganzen Verlauf durch.
            todaysDayIds = sessionList
                .takeWhile { it.completedAt.toLocalDate() == today }
                .mapTo(mutableSetOf()) { it.dayId },
            deload = deloadStatus(
                sessionDates = entriesOldestFirst.map { it.date },
                today = today,
                cycleWeeks = cycleWeeks
            ),
            today = today
        )
    }

    private data class DayState(
        val days: List<TrainingDay>,
        val selectedDayId: Int
    )

    private data class Preferences(
        val dayCount: Int,
        val title: String,
        /** Übungen, die an ihren Trainingstagen gerade nicht mitlaufen sollen. */
        val hiddenExerciseNames: Set<String>
    )

    private data class SessionSummary(
        val completedDayIds: Set<Int>,
        val canReturnToPreviousCycle: Boolean,
        val todaysDayIds: Set<Int>,
        val deload: DeloadStatus,
        /** Das Datum, mit dem gerechnet wurde – das Satz-Protokoll braucht dasselbe „heute“. */
        val today: LocalDate
    )

    /**
     * Alles, was um die Übungsliste herum steht, in einem Wert.
     *
     * `combine` nimmt höchstens fünf Zuflüsse; ohne diese Zusammenfassung müssten Einstellungen
     * und Sitzungszusammenfassung als geschachtelte `Pair` durchgereicht und in der Kopfzeile
     * der Lambda wieder auseinandergenommen werden – jeder neue Wert hätte die Schachtelung
     * umgebaut.
     */
    private data class Surroundings(
        val dayCount: Int,
        val title: String,
        val hiddenExerciseNames: Set<String>,
        val completedDayIds: Set<Int>,
        val canReturnToPreviousCycle: Boolean,
        val todaysDayIds: Set<Int>,
        val deload: DeloadStatus,
        val today: LocalDate,
        val setLogUnits: Map<SetLogKey, List<SetUnit>>,
        val cardioLogs: Map<SetLogKey, List<CardioLog>>
    )

    private val surroundings = combine(
        preferences,
        sessionSummary,
        setLogUnits,
        cardioLogs
    ) { prefs, summary, units, cardio ->
        Surroundings(
            dayCount = prefs.dayCount,
            title = prefs.title,
            hiddenExerciseNames = prefs.hiddenExerciseNames,
            completedDayIds = summary.completedDayIds,
            canReturnToPreviousCycle = summary.canReturnToPreviousCycle,
            todaysDayIds = summary.todaysDayIds,
            deload = summary.deload,
            today = summary.today,
            setLogUnits = units,
            cardioLogs = cardio
        )
    }

    val uiState = combine(
        dayState,
        allExercises,
        definitions,
        selectedIds,
        surroundings
    ) { day, all, definitionList, selection, around ->
        // Tag und zugehörige Liste entstehen hier gemeinsam aus *einer* Aussendung – nur so
        // springen Reiter und Übungen im selben Frame um.
        // `observeAll` sortiert bereits nach Tag, Position und id; das Filtern erhält das.
        //
        // Ausgeblendete Übungen fallen hier heraus und nicht schon in der Datenbank: Sie stehen
        // dort unverändert samt Position und Gewicht, das Ausblenden bleibt damit eine Frage der
        // Anzeige und ist jederzeit umkehrbar (siehe TrainingRepository.setExerciseHidden).
        val exerciseList = all.filter {
            it.dayId == day.selectedDayId && it.name !in around.hiddenExerciseNames
        }
        // Über die eingestellte Anzahl hinausgehende Tage bleiben in der Datenbank stehen,
        // werden aber nicht angezeigt – so ist eine verkürzte Runde jederzeit umkehrbar.
        val visibleDays = day.days.filter { it.id <= around.dayCount }
        TrainingUiState(
            days = visibleDays,
            selectedDayId = day.selectedDayId,
            exercises = exerciseList,
            knownExerciseNames = definitionList.map { it.name },
            // Zeilen, die inzwischen weg sind, dürfen nicht markiert bleiben. Ohne Auswahl
            // gibt es dafür nichts zu tun – der häufige Fall kostet so keine Zwischenmengen.
            selectedIds = if (selection.isEmpty()) {
                emptySet()
            } else {
                selection intersect exerciseList.mapTo(HashSet()) { it.id }
            },
            completedDayIds = around.completedDayIds,
            canReturnToPreviousCycle = around.canReturnToPreviousCycle,
            todaysDayIds = around.todaysDayIds,
            deload = around.deload,
            appTitle = around.title,
            setLogRows = setLogRows(exerciseList, around),
            cardioLoggedIds = exerciseList.filter { exercise ->
                exercise.isCardio && todaysCardioLog(
                    logsOldestFirst = around.cardioLogs[SetLogKey(exercise.name, exercise.variation)].orEmpty(),
                    dayId = exercise.dayId,
                    today = around.today
                ) != null
            }.mapTo(HashSet()) { it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TrainingUiState()
    )

    /**
     * Der heutige Stand des Satz-Protokolls je Zeile – nur für Übungen mit eingeschaltetem
     * Protokoll und einer Sätze-Zahl; alle anderen sehen aus wie immer.
     *
     * Gezählt wird diese Übung samt Variation an diesem Trainingstag, gegen die Sätze, die diese
     * Woche gelten (siehe [setsThisWeek]).
     */
    private fun setLogRows(
        exercises: List<ExerciseItem>,
        around: Surroundings
    ): Map<Long, SetLogRowState> = exercises.mapNotNull { exercise ->
        if (!exercise.logSets) return@mapNotNull null
        val planned = setsThisWeek(exercise.sets, around.deload.isDeloadWeek)
            ?.takeIf { it >= 1 } ?: return@mapNotNull null
        val units = around.setLogUnits[SetLogKey(exercise.name, exercise.variation)].orEmpty()
        val today = todaysUnit(units, exercise.dayId, around.today)
        exercise.id to SetLogRowState(
            progress = SetsProgress(logged = today?.plannedLogged(planned) ?: 0, planned = planned),
            isTopReached = suggestedWeight(exercise, units, around.deload.isDeloadWeek) != null
        )
    }.toMap()

    /**
     * Wohin der Pfeil das Gewicht verschöbe, wenn die Zeile das obere Ende erreicht hat (siehe
     * [isTopOfRangeReached]); sonst `null`. Ein Pfeil, der nichts mehr bewegt – nach unten bei
     * 0 kg –, bekommt keinen Hinweis.
     */
    private fun suggestedWeight(
        exercise: ExerciseItem,
        units: List<SetUnit>,
        isDeloadWeek: Boolean
    ): Double? {
        val weight = exercise.weightKg ?: return null
        val reached = isTopOfRangeReached(
            units = units,
            dayId = exercise.dayId,
            plannedSets = exercise.sets,
            repsMax = exercise.repsMax,
            currentWeightKg = weight,
            isDeloadWeek = isDeloadWeek
        )
        if (!reached) return null
        return stepWeight(weight, exercise.progressionStepKg, exercise.progressionDown)
            .takeIf { it != weight }
    }

    /**
     * Das offene Sheet „Satz-Protokoll“; `null`, solange keines offen ist.
     *
     * Wie das Bearbeiten-Sheet neben [uiState] statt darin: Es ändert sich mit jedem Satz, und
     * die Liste darunter muss dafür nicht neu zusammengesetzt werden. Verschwindet die Zeile
     * oder ihr Protokoll – gelöscht, Schalter aus, keine Sätze-Zahl mehr –, schließt es sich.
     */
    val setLogSheet: StateFlow<SetLogSheetState?> = combine(
        setLogTargetId,
        allExercises,
        setLogUnits,
        uiState.map { it.deload.isDeloadWeek }.distinctUntilChanged(),
        currentDate.flow
    ) { id, all, units, isDeloadWeek, today ->
        val exercise = id?.let { target -> all.firstOrNull { it.id == target } }
            ?.takeIf { it.logSets } ?: return@combine null
        val planned = setsThisWeek(exercise.sets, isDeloadWeek)
            ?.takeIf { it >= 1 } ?: return@combine null
        val exerciseUnits = units[SetLogKey(exercise.name, exercise.variation)].orEmpty()
        SetLogSheetState(
            exercise = exercise,
            plannedSets = planned,
            isDeloadWeek = isDeloadWeek,
            todaysSets = todaysUnit(exerciseUnits, exercise.dayId, today)?.sets.orEmpty(),
            lastUnit = lastUnit(exerciseUnits, exercise.dayId, today),
            today = today,
            suggestedWeightKg = suggestedWeight(exercise, exerciseUnits, isDeloadWeek)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Der offene Dialog „Cardio eintragen“; `null`, solange keiner offen ist.
     *
     * Neben [uiState] wie das Satz-Protokoll. Verschwindet die Zeile oder ist sie keine
     * Cardio-Übung mehr, schließt er sich.
     */
    val cardioLogDialog: StateFlow<CardioLogDialogState?> = combine(
        cardioLogTargetId,
        allExercises,
        cardioLogs,
        currentDate.flow
    ) { id, all, logs, today ->
        val exercise = id?.let { target -> all.firstOrNull { it.id == target } }
            ?.takeIf { it.isCardio } ?: return@combine null
        val exerciseLogs = logs[SetLogKey(exercise.name, exercise.variation)].orEmpty()
        val todays = todaysCardioLog(exerciseLogs, exercise.dayId, today)
        CardioLogDialogState(
            exercise = exercise,
            todaysLog = todays,
            lastEntry = lastCardioEntryBefore(exerciseLogs, todays, today)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            // Erst ein liegen gebliebenes Training beenden, dann weiterschalten: Wer gestern
            // ohne Haken gegangen ist, findet heute den Tag danach vor.
            repository.finishIdleWorkout()
            repository.advanceDayIfNewDate()
        }
    }

    /**
     * Beim Zurückkehren in den Vordergrund kann ein neuer Tag angebrochen sein – dann stimmen
     * Datumsangaben, Deload-Woche und der vorausgewählte Trainingstag nicht mehr. Und ein
     * Training kann vorbei sein, dessen automatisches Ende Android noch nicht ausgeführt hat.
     */
    fun onResumed() {
        currentDate.refresh()
        viewModelScope.launch {
            repository.finishIdleWorkout()
            repository.advanceDayIfNewDate(currentDate.value)
        }
    }

    // --- Tag-Auswahl -------------------------------------------------------

    /**
     * Der Tag gilt sofort; gespeichert wird er nebenher.
     *
     * Liefe die Auswahl nur über die Coroutine, hinge das Aufleuchten des Reiters an einem
     * Schreibvorgang samt `fsync` – siehe [TrainingRepository.selectedDayId].
     */
    fun onDaySelected(dayId: Int) {
        selectedIds.value = emptySet()
        repository.selectDayNow(dayId)
        viewModelScope.launch { repository.selectDay(dayId) }
    }

    // --- Training abhaken --------------------------------------------------

    /**
     * Hakt das Training des aktuellen Tages ab – und beim zweiten Tippen am selben Tag wieder
     * zurück.
     *
     * Der Haken ist damit sein eigenes „Rückgängig“. Das ersetzt die frühere Meldung am
     * unteren Rand, die sich genau über den Knopf schob und dessen Effekt verdeckte.
     *
     * Was ein Tippen bewirkt, entscheidet das Repository in einer Transaktion und nicht der
     * hier sichtbare Zustand: Der hinkt der Datenbank um mehrere Bilder hinterher – siehe
     * [TrainingRepository.toggleWorkout].
     *
     * War es das letzte offene Training der Runde, meldet das Repository das zurück und der
     * Bildschirm lässt es kurz Konfetti regnen.
     */
    fun onToggleWorkoutCompleted() {
        viewModelScope.launch {
            // Der Tag kommt aus dem Repository, nicht aus dem angezeigten Zustand: Wer den
            // Reiter wechselt und sofort abhakt, träfe sonst noch das vorige Training.
            val result = repository.toggleWorkout(
                dayId = repository.currentSelectedDay(),
                // Dasselbe „heute“ wie überall sonst: Über Nacht offen geblieben, nähme eine
                // frisch abgefragte Uhr den Eintrag von gestern nicht mehr zurück.
                today = currentDate.value
            )
            if (result.completesRotation) celebrationChannel.trySend(Unit)
        }
    }

    /**
     * Beginnt die nächste Runde von Hand – der Pfeil neben dem Haken am letzten Tag.
     *
     * Sonst wartet die App auf den nächsten Kalendertag, bevor sie weiterschaltet, und das ist
     * auch richtig so: Wer am Abend fertig ist, will am selben Abend keinen neuen Trainingstag
     * vorgesetzt bekommen. Wer aber gleich weitermachen will – zwei Einheiten an einem Tag,
     * oder ein Training kurz vor Mitternacht –, kommt hier ohne Umweg in die neue Runde.
     *
     * Der Pfeil steht am letzten Tag auch dann bereit, wenn dessen Training noch aussteht: Fällt
     * ein Tag der Woche aus, wird er hier übersprungen, statt die Runde stehen zu lassen, bis er
     * irgendwann nachgeholt ist. Was übersprungen wurde, bleibt im Verlauf sichtbar – die Runde
     * schließt eben mit drei von vier Tagen.
     *
     * Anders als bei [onDaySelected] wird der Tag hier *nicht* vorab umgeschaltet, sondern erst
     * nach dem Schnitt: Sonst zeigte die Anzeige für ein paar Bilder den ersten Tag mit den
     * Haken der alten Runde, und der eben gelandete Haken flöge sofort wieder in die Bildmitte
     * zurück. Der Schnitt ist ein einzelner Schreibvorgang – das Warten darauf ist kürzer als
     * jede Bewegung auf dem Bildschirm.
     */
    fun onStartNextCycle() {
        val firstDay = uiState.value.days.firstOrNull()?.id ?: return
        selectedIds.value = emptySet()
        viewModelScope.launch {
            val hasCut = repository.startNextRotation(currentDate.value)
            repository.selectDay(firstDay)
            // War die Runde ohnehin leer, gab es nichts abzuschließen – dann steht auch nichts
            // zum Zurücknehmen bereit, und eine Meldung darüber wäre eine Meldung über nichts.
            if (hasCut) eventChannel.send(TrainingEvent.CycleStarted)
        }
    }

    /**
     * Zurück in die vorige Runde – der Pfeil am ersten Tag, solange die neue noch leer ist.
     *
     * Der Weg zurück aus einem Fehlgriff: Die abgehakten Tage der vorigen Runde stehen wieder da,
     * und die Auswahl springt auf den Tag, der dort als nächstes dran gewesen wäre. Sobald in der
     * neuen Runde trainiert wurde, gibt es nichts mehr zurückzunehmen – siehe
     * [de.beispiel.meintraining.util.canUndoRotationCut].
     */
    fun onReturnToPreviousCycle() {
        selectedIds.value = emptySet()
        viewModelScope.launch { returnToPreviousCycle() }
    }

    private suspend fun returnToPreviousCycle() {
        if (!repository.returnToPreviousRotation()) return
        repository.selectDay(repository.nextDayInRotation(currentDate.value))
    }

    // --- Mehrfachauswahl ---------------------------------------------------

    /** Langer Druck startet die Auswahl, im Auswahlmodus schaltet ein Tippen sie um. */
    fun onExerciseLongClick(exercise: ExerciseItem) {
        selectedIds.value = selectedIds.value + exercise.id
    }

    fun onSelectionToggle(exercise: ExerciseItem) {
        val current = selectedIds.value
        selectedIds.value = if (exercise.id in current) current - exercise.id else current + exercise.id
    }

    fun onSelectionClear() {
        selectedIds.value = emptySet()
    }

    fun onDeleteSelected() {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        val items = uiState.value.exercises.filter { it.id in selection }
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.deleteExercises(items)
            eventChannel.send(TrainingEvent.ExercisesDeleted(items))
        }
    }

    // --- Supersets ---------------------------------------------------------

    fun onCreateSuperset() {
        val selection = selectedIds.value
        if (selection.size < MIN_SUPERSET_SIZE) return
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.createSuperset(repository.currentSelectedDay(), selection)
        }
    }

    fun onDissolveSuperset() {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        selectedIds.value = emptySet()
        viewModelScope.launch {
            repository.dissolveSuperset(repository.currentSelectedDay(), selection)
        }
    }

    // --- Kopieren und Verschieben -----------------------------------------

    /** Kopiert die Auswahl ans Ende von Tag [dayId]. */
    fun onCopySelected(dayId: Int) = transferSelected(dayId, move = false)

    /** Verschiebt die Auswahl ans Ende von Tag [dayId]. */
    fun onMoveSelected(dayId: Int) = transferSelected(dayId, move = true)

    /** Meldung samt „Rückgängig“ wie beim Löschen – siehe [TrainingRepository.undoTransfer]. */
    private fun transferSelected(dayId: Int, move: Boolean) {
        val selection = selectedIds.value
        if (selection.isEmpty()) return
        val targetName = uiState.value.days.firstOrNull { it.id == dayId }?.name.orEmpty()
        selectedIds.value = emptySet()
        viewModelScope.launch {
            val transfer = repository.transferExercises(
                fromDayId = repository.currentSelectedDay(),
                ids = selection,
                toDayId = dayId,
                move = move
            ) ?: return@launch
            eventChannel.send(TrainingEvent.ExercisesTransferred(transfer, targetName))
        }
    }

    // --- Bearbeiten-Sheet --------------------------------------------------

    /**
     * Der Tag wird beim Öffnen festgehalten, nicht erst beim Speichern nachgeschlagen: Die
     * Übung landet dort, wo der Nutzer sie angelegt hat – auch wenn die Auswahl inzwischen
     * weitergesprungen ist, etwa weil um Mitternacht ein neuer Tag angebrochen ist.
     */
    fun onAddClick() {
        viewModelScope.launch {
            formState.value = ExerciseForm(dayId = repository.selectedDayId.first())
        }
    }

    fun onExerciseClick(exercise: ExerciseItem) {
        formState.value = exercise.toForm()
    }

    /**
     * Sobald der eingetippte Name auf eine bekannte Übung passt, werden ihre Werte am Namen
     * übernommen – Gewicht, Schritt, Richtung, Notiz, Protokoll-Schalter, Art und Cardio-Ziele;
     * siehe [ExerciseForm.withChange].
     *
     * Ist das Sheet schon zu, kommt nichts mehr an: Ein Tastendruck, der sich mit dem Speichern
     * überschneidet, öffnete es sonst mit dem alten Stand gleich wieder.
     */
    fun onFormChange(form: ExerciseForm) {
        val current = formState.value ?: return
        formState.value = current.withChange(form, definitions.value)
    }

    fun onVariationToggle() {
        val form = formState.value ?: return
        formState.value = if (form.showVariation) {
            form.copy(showVariation = false, variation = "")
        } else {
            form.copy(showVariation = true)
        }
    }

    fun onFormDismiss() {
        formState.value = null
    }

    /**
     * Schließt das Sheet sofort und speichert danach.
     *
     * Nicht umgekehrt: Bis die Datenbank fertig ist, stünde das Sheet sonst noch offen, und ein
     * zweiter Druck auf „Speichern“ – aus Ungeduld oder weil der erste zu kurz geriet – legte
     * dieselbe neue Übung ein zweites Mal an. So findet er kein Formular mehr vor.
     */
    fun onFormSave() {
        val form = formState.value ?: return
        if (!form.canSave) return
        formState.value = null

        viewModelScope.launch {
            val rawMin = parseOptionalInt(form.repsMin)
            val rawMax = parseOptionalInt(form.repsMax)
            // Vertauschte Grenzen still korrigieren
            val repsMin = if (rawMin != null && rawMax != null) minOf(rawMin, rawMax) else rawMin
            val repsMax = if (rawMin != null && rawMax != null) maxOf(rawMin, rawMax) else rawMax

            repository.saveExercise(
                id = form.id,
                dayId = form.dayId,
                name = form.name.trim(),
                variation = form.variation.trim().takeIf { form.showVariation && it.isNotEmpty() },
                weightKg = parseOptionalDecimal(form.weight),
                sets = parseOptionalInt(form.sets),
                repsMin = repsMin,
                repsMax = repsMax,
                progressionStepKg = parseProgressionStep(form.progressionStep),
                progressionDown = form.progressionDown,
                note = form.note.trim(),
                logSets = form.logSets,
                kind = form.kind,
                cardio = form.cardio.toTargets()
            )
        }
    }

    /** Löschen aus dem geöffneten Sheet heraus. */
    fun onFormDelete() {
        val id = formState.value?.id ?: return
        formState.value = null
        viewModelScope.launch {
            val exercise = repository.findExercise(id) ?: return@launch
            val items = listOf(exercise)
            repository.deleteExercises(items)
            eventChannel.send(TrainingEvent.ExercisesDeleted(items))
        }
    }

    // --- Sortieren ---------------------------------------------------------

    /**
     * Übernimmt die per Drag-and-drop entstandene Reihenfolge. [orderedIds] sind die
     * Übungen des aktuellen Tages von oben nach unten; während des Ziehens sortiert die
     * Oberfläche nur ihre eigene Kopie, gespeichert wird erst beim Loslassen.
     */
    fun onReorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderExercises(repository.currentSelectedDay(), orderedIds)
        }
    }

    // --- Progression -------------------------------------------------------

    /**
     * Verschiebt das Gewicht um den bei dieser Übung hinterlegten Schritt – an allen Tagen, an
     * denen sie vorkommt, und in der bei ihr eingestellten Richtung. Ohne gesetztes Gewicht
     * öffnet sich stattdessen das Sheet.
     *
     * Bei einer Cardio-Übung verschiebt der Pfeil ihren gewählten Wert um ihren Schritt, in ihrer
     * eigenen Richtung (siehe [TrainingRepository.progressCardio]) – mit Meldung und „Rückgängig“
     * wie beim Gewicht.
     *
     * Gerechnet wird im Repository auf dem gespeicherten Stand; die Zeile entscheidet hier nur,
     * ob es überhaupt etwas zu verschieben gibt – siehe [TrainingRepository.progressWeight].
     */
    fun onProgressClick(exercise: ExerciseItem) = progress(exercise, reverse = false)

    /**
     * Langer Druck auf den Pfeil: genau ein Schritt gegen seine Richtung – für die Steigerung,
     * die zu viel war. Meldung und „Rückgängig“ wie beim Tippen.
     */
    fun onProgressLongClick(exercise: ExerciseItem) = progress(exercise, reverse = true)

    private fun progress(exercise: ExerciseItem, reverse: Boolean) {
        if (exercise.isCardio) {
            progressCardio(exercise, reverse)
            return
        }
        if (exercise.weightKg == null) {
            onExerciseClick(exercise)
            return
        }
        viewModelScope.launch {
            val change = repository.progressWeight(exercise.name, reverse) ?: return@launch
            eventChannel.send(
                TrainingEvent.WeightChanged(
                    exerciseName = exercise.name,
                    previousWeightKg = change.previousKg,
                    newWeightKg = change.newKg,
                    logId = change.logId
                )
            )
        }
    }

    /** Ohne Wert für den Pfeil gibt es nichts zu verschieben – dann öffnet sich wie oben das Sheet. */
    private fun progressCardio(exercise: ExerciseItem, reverse: Boolean) {
        if (!exercise.cardio.hasArrow) {
            onExerciseClick(exercise)
            return
        }
        viewModelScope.launch {
            val change = repository.progressCardio(exercise.name, reverse) ?: return@launch
            eventChannel.send(TrainingEvent.CardioChanged(exerciseName = exercise.name, change = change))
        }
    }

    // --- Satz-Protokoll -----------------------------------------------------

    /** Tippen auf den Sätze-Chip einer Übung mit Protokoll öffnet das Sheet „Satz-Protokoll“. */
    fun onSetsClick(exercise: ExerciseItem) {
        setLogTargetId.value = exercise.id
    }

    fun onSetLogDismiss() {
        setLogTargetId.value = null
    }

    /**
     * ✓ in einer offenen Zeile: speichert Satz [setNumber] sofort. Das Repository legt ihn nur
     * einmal an und meldet ihn als Aktivität im Training (siehe [TrainingRepository.logSet]).
     */
    fun onLogSet(exercise: ExerciseItem, setNumber: Int, reps: Int, weightKg: Double?) {
        viewModelScope.launch {
            repository.logSet(
                name = exercise.name,
                variation = exercise.variation,
                dayId = exercise.dayId,
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg
            )
        }
    }

    /** Korrigiert einen gespeicherten Satz. */
    fun onUpdateSet(id: Long, reps: Int, weightKg: Double?) {
        viewModelScope.launch { repository.updateSetLog(id, reps, weightKg) }
    }

    fun onDeleteSet(id: Long) {
        viewModelScope.launch { repository.deleteSetLog(id) }
    }

    // --- Cardio eintragen ----------------------------------------------------

    /** Tippen auf den Chip einer Cardio-Zeile öffnet „Cardio eintragen“. */
    fun onCardioClick(exercise: ExerciseItem) {
        cardioLogTargetId.value = exercise.id
    }

    fun onCardioLogDismiss() {
        cardioLogTargetId.value = null
    }

    /**
     * „Speichern“: trägt die Einheit ein – oder korrigiert die von heute, wenn der Dialog mit ihr
     * geöffnet wurde. Wie beim Bearbeiten-Sheet schließt er sofort und gespeichert wird danach;
     * ein zweiter Druck findet so keinen Dialog mehr vor.
     *
     * Das Eintragen meldet sich als Aktivität im Training (siehe [TrainingRepository.logCardio]),
     * das Korrigieren nicht – wie beim Satz-Protokoll.
     */
    fun onCardioLogSave(values: CardioValues) {
        val state = cardioLogDialog.value ?: return
        cardioLogTargetId.value = null
        viewModelScope.launch {
            val todays = state.todaysLog
            if (todays != null) {
                repository.updateCardioLog(todays.id, values)
            } else {
                repository.logCardio(
                    name = state.exercise.name,
                    variation = state.exercise.variation,
                    dayId = state.exercise.dayId,
                    values = values
                )
            }
        }
    }

    /** „Löschen“: entfernt die Einheit von heute, die der Dialog zeigt. */
    fun onCardioLogDelete() {
        val log = cardioLogDialog.value?.todaysLog ?: return
        cardioLogTargetId.value = null
        viewModelScope.launch { repository.deleteCardioLog(log.id) }
    }

    // --- Rückgängig --------------------------------------------------------

    fun onUndo(event: TrainingEvent) {
        viewModelScope.launch {
            when (event) {
                is TrainingEvent.WeightChanged -> repository.revertWeight(
                    name = event.exerciseName,
                    previousKg = event.previousWeightKg,
                    changedToKg = event.newWeightKg,
                    logId = event.logId
                )
                is TrainingEvent.CardioChanged ->
                    repository.revertCardio(event.exerciseName, event.change)
                is TrainingEvent.ExercisesDeleted ->
                    repository.restoreExercises(event.exercises)
                is TrainingEvent.ExercisesTransferred ->
                    repository.undoTransfer(event.transfer)
                TrainingEvent.CycleStarted -> returnToPreviousCycle()
            }
        }
    }

    private fun ExerciseItem.toForm() = ExerciseForm(
        id = id,
        dayId = dayId,
        name = name,
        variation = variation.orEmpty(),
        showVariation = !variation.isNullOrBlank(),
        weight = weightKg?.toDecimalString().orEmpty(),
        sets = sets?.toString().orEmpty(),
        repsMin = repsMin?.toString().orEmpty(),
        repsMax = repsMax?.toString().orEmpty(),
        progressionStep = progressionStepKg.toDecimalString(),
        progressionDown = progressionDown,
        note = note.orEmpty(),
        logSets = logSets,
        kind = kind,
        cardio = cardio.toForm(),
        // Die Werte im Formular sind die der Übung selbst – ein Wechsel zurück auf ihren Namen
        // holt deshalb nichts aus der Datenbank, sondern lässt stehen, was dasteht.
        originalName = name,
        matchedName = name
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                TrainingViewModel(app.repository, app.currentDate)
            }
        }
    }
}
