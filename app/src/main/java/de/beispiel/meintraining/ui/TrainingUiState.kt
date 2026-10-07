package de.beispiel.meintraining.ui

import androidx.compose.runtime.Immutable
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.data.model.SetLog
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.repository.CardioChange
import de.beispiel.meintraining.data.repository.ExerciseTransfer
import de.beispiel.meintraining.ui.components.SetsProgress
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.LastCardioEntry
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.SetUnit
import de.beispiel.meintraining.util.defaultCardioStep
import de.beispiel.meintraining.util.formatCardioStepInput
import de.beispiel.meintraining.util.formatDurationValue
import de.beispiel.meintraining.util.isValidCardioDurationInput
import de.beispiel.meintraining.util.parseCardioDuration
import de.beispiel.meintraining.util.parseCardioStep
import de.beispiel.meintraining.util.parseOptionalDecimal
import de.beispiel.meintraining.util.toDecimalString
import java.time.LocalDate

/** Kompletter Zustand des Hauptscreens. */
@Immutable
data class TrainingUiState(
    val days: List<TrainingDay> = emptyList(),
    val selectedDayId: Int = FIRST_DAY_ID,
    val exercises: List<ExerciseItem> = emptyList(),
    /** Alle bereits angelegten Übungsnamen – Vorschläge für das Namensfeld. */
    val knownExerciseNames: List<String> = emptyList(),
    /** Im Auswahlmodus markierte Zeilen; leer heißt: kein Auswahlmodus. */
    val selectedIds: Set<Long> = emptySet(),
    /** Tage, die in der laufenden Runde schon abgehakt sind. */
    val completedDayIds: Set<Int> = emptySet(),
    /** Lässt sich der zuletzt von Hand gezogene Rundenschnitt noch zurücknehmen? */
    val canReturnToPreviousCycle: Boolean = false,
    /** Tage, für die *heute* ein Eintrag steht – unabhängig von der laufenden Runde. */
    val todaysDayIds: Set<Int> = emptySet(),
    val deload: DeloadStatus = DeloadStatus(),
    /** Selbst vergebene Überschrift; leer heißt: Vorgabe aus den Textressourcen. */
    val appTitle: String = "",
    /**
     * Stand des Satz-Protokolls je Zeile, nach Kennung – nur für Zeilen, deren Sätze sich
     * protokollieren lassen (Schalter an und eine Sätze-Zahl).
     */
    val setLogRows: Map<Long, SetLogRowState> = emptyMap(),
    /**
     * Cardio-Zeilen, für die heute an ihrem Trainingstag eine Einheit eingetragen ist – ihr Chip
     * trägt den Haken.
     */
    val cardioLoggedIds: Set<Long> = emptySet()
) {
    /** Ist der angezeigte Tag in dieser Runde schon erledigt? */
    val isSelectedDayCompleted: Boolean get() = selectedDayId in completedDayIds

    /**
     * Gilt das Training des angezeigten Tages als eingetragen?
     *
     * Das ist mehr als [isSelectedDayCompleted], und zwar in genau einem Fall: Wer heute
     * trainiert und danach mit dem Pfeil die nächste Runde begonnen hat, findet in der neuen
     * Runde jeden Haken wieder offen – auch den von heute (siehe `startNextRotation`). Fertig ist
     * er trotzdem; der Eintrag von heute sagt das unabhängig von der Runde. Eine *volle* Runde
     * braucht diesen Umweg nicht mehr: Sie bleibt bis Mitternacht stehen (siehe `rotations`).
     *
     * Die Unterscheidung zählt für alles, was auf das Abhaken *antwortet* – etwa ob der Pfeil in
     * die nächste Runde grün steht: Ein heute schon erledigter Tag sähe sonst nach dem
     * Weiterschalten aus, als wäre der Haken nicht angekommen.
     */
    val isSelectedDayConfirmed: Boolean
        get() = isSelectedDayCompleted || selectedDayId in todaysDayIds

    /**
     * Steht der Pfeil zur nächsten Runde bereit?
     *
     * Am letzten Tag der Runde immer, auch wenn dessen Training noch aussteht. Ist er abgehakt,
     * überspringt der Pfeil das Warten auf Mitternacht; steht er noch aus, schließt er die Runde
     * mit dem, was da ist – für die Woche, in der ein Tag ausfällt. An jedem anderen Tag wäre der
     * Pfeil dagegen ein Sprung mitten in der Runde (siehe [TrainingViewModel.onStartNextCycle]).
     */
    val canStartNextCycle: Boolean
        get() = selectedDayId == days.lastOrNull()?.id

    /**
     * Steht der Pfeil zurück in die vorige Runde bereit?
     *
     * Am ersten Tag, denn dort landet, wer die Runde eben weitergeschaltet hat – und nur, solange
     * in der neuen Runde noch nichts abgehakt ist. Damit steht der Weg zurück genau dort, wo ein
     * Fehlgriff auffällt, und verschwindet, sobald die neue Runde begonnen hat.
     */
    val canReturnToPreviousCycleHere: Boolean
        get() = canReturnToPreviousCycle && selectedDayId == days.firstOrNull()?.id

    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()

    private val selectedExercises: List<ExerciseItem>
        get() = exercises.filter { it.id in selectedIds }

    /** Ein Superset braucht mindestens zwei Übungen. */
    val canCreateSuperset: Boolean get() = selectedIds.size >= MIN_SUPERSET_SIZE

    /** Auflösen geht, sobald mindestens eine markierte Zeile zu einem Superset gehört. */
    val canDissolveSuperset: Boolean get() = selectedExercises.any { it.supersetId != null }

    /** Wohin sich die Auswahl kopieren oder verschieben lässt: jeder sichtbare Tag außer diesem. */
    val transferTargetDays: List<TrainingDay> get() = days.filter { it.id != selectedDayId }
}

/**
 * Das Satz-Protokoll einer Zeile, wie die Liste es zeigt: der heutige Stand im Sätze-Chip.
 *
 * Bezogen auf *diese* Zeile – diese Übung samt Variation an diesem Trainingstag mit ihrer
 * Vorgabe –, nicht auf den Namen: Dieselbe Übung kann an einem anderen Tag andere Sätze haben.
 */
@Immutable
data class SetLogRowState(
    val progress: SetsProgress,
    /** Oberes Ende erreicht: Der Pfeil der Zeile steht grün da (siehe `isTopOfRangeReached`). */
    val isTopReached: Boolean = false
)

/**
 * Alles, was das Sheet „Satz-Protokoll“ zu einer Zeile zeigt.
 *
 * [plannedSets] sind die Sätze, die diese Woche gelten (in der Deload-Woche halbiert),
 * [todaysSets] die heute an diesem Trainingstag gespeicherten, nach Nummer. [lastUnit] ist
 * „Letztes Mal“ – siehe [de.beispiel.meintraining.util.lastUnit].
 */
@Immutable
data class SetLogSheetState(
    val exercise: ExerciseItem,
    val plannedSets: Int,
    val isDeloadWeek: Boolean,
    val todaysSets: List<SetLog>,
    val lastUnit: SetUnit?,
    val today: LocalDate,
    /**
     * Steht „Oberes Ende erreicht – Gewicht erhöhen?“ (oder „senken?“ bei einem Pfeil nach
     * unten) da, und wohin der Knopf das Gewicht verschiebt; `null`: kein Hinweis.
     */
    val suggestedWeightKg: Double? = null
)

/**
 * Alles, was der Dialog „Cardio eintragen“ zu einer Zeile zeigt.
 *
 * [todaysLog] ist die heute an diesem Trainingstag schon eingetragene Einheit: Dann füllt sie die
 * Felder, „Speichern“ korrigiert sie, und „Löschen“ steht bereit. [lastEntry] ist „Letztes Mal“
 * – die jüngste Einheit außer dieser (siehe [de.beispiel.meintraining.util.lastCardioEntryBefore]).
 */
@Immutable
data class CardioLogDialogState(
    val exercise: ExerciseItem,
    val todaysLog: CardioLog? = null,
    val lastEntry: LastCardioEntry? = null
) {
    /** Was beim Öffnen in den Feldern steht: die Einheit von heute, sonst die Zielwerte. */
    fun initialForm(): CardioEntryForm = CardioEntryForm.of(
        values = todaysLog?.values ?: exercise.cardio.values,
        fallbackUnit = exercise.cardio.intensityUnit
    )
}

/**
 * Die Felder von „Cardio eintragen“ – Text wie im Bearbeiten-Sheet, damit Teileingaben wie „7:“
 * stehen bleiben. Die Einheit des Tempos kommt mit der Übung oder der korrigierten Einheit und
 * wird hier nicht umgestellt: Im Studio steht man an genau einem Gerät.
 */
data class CardioEntryForm(
    val duration: String = "",
    val distance: String = "",
    val intensity: String = "",
    val intensityUnit: IntensityUnit = IntensityUnit.KMH,
    val incline: String = ""
) {
    val isDurationValid: Boolean get() = isValidCardioDurationInput(duration)

    /** Die eingetragenen Werte; ein leeres Feld heißt: diesen Wert gab es nicht. */
    fun toValues(): CardioValues {
        val intensityValue = parseOptionalDecimal(intensity)
        return CardioValues(
            durationMin = parseCardioDuration(duration),
            distanceKm = parseOptionalDecimal(distance),
            intensity = intensityValue,
            intensityUnit = intensityUnit.takeIf { intensityValue != null },
            inclinePercent = parseOptionalDecimal(incline)
        )
    }

    /**
     * Speichern geht mit einer lesbaren Dauer und mindestens einem Wert – eine Einheit ganz ohne
     * Werte gibt es nicht (siehe `TrainingRepository.logCardio`).
     */
    val canSave: Boolean get() = isDurationValid && !toValues().isEmpty

    companion object {
        /** Die Felder mit [values] gefüllt, in derselben Schreibweise, die das Einlesen versteht. */
        fun of(values: CardioValues, fallbackUnit: IntensityUnit): CardioEntryForm = CardioEntryForm(
            duration = values.durationMin?.let(::formatDurationValue).orEmpty(),
            distance = values.distanceKm?.toDecimalString().orEmpty(),
            intensity = values.intensity?.toDecimalString().orEmpty(),
            intensityUnit = values.intensityUnit ?: fallbackUnit,
            incline = values.inclinePercent?.toDecimalString().orEmpty()
        )
    }
}

/**
 * Alle Aktionen des Hauptscreens in einem Bündel.
 *
 * Einzeln durchgereicht waren es zwanzig Rückrufe: Jede neue Aktion musste an vier Stellen
 * nachgetragen werden, und die Vorschauen bestanden zur Hälfte aus leeren Lambdas. Die
 * Vorgabewerte tun genau das jetzt von allein – eine Vorschau kommt mit `TrainingActions()` aus.
 *
 * Gebundene Methodenreferenzen sind untereinander gleich, solange das ViewModel dasselbe ist;
 * am Aufrufort einmal `remember`-t, bleibt das Bündel damit über Recompositions stabil.
 */
@Immutable
data class TrainingActions(
    val onDaySelected: (Int) -> Unit = {},
    val onAddClick: () -> Unit = {},
    val onToggleWorkoutCompleted: () -> Unit = {},
    val onStartNextCycle: () -> Unit = {},
    val onReturnToPreviousCycle: () -> Unit = {},
    val onExerciseClick: (ExerciseItem) -> Unit = {},
    val onExerciseLongClick: (ExerciseItem) -> Unit = {},
    val onSelectionToggle: (ExerciseItem) -> Unit = {},
    val onSelectionClear: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onCreateSuperset: () -> Unit = {},
    val onDissolveSuperset: () -> Unit = {},
    val onCopySelected: (Int) -> Unit = {},
    val onMoveSelected: (Int) -> Unit = {},
    val onProgressClick: (ExerciseItem) -> Unit = {},
    val onProgressLongClick: (ExerciseItem) -> Unit = {},
    val onSetsClick: (ExerciseItem) -> Unit = {},
    val onLogSet: (ExerciseItem, Int, Int, Double?) -> Unit = { _, _, _, _ -> },
    val onUpdateSet: (Long, Int, Double?) -> Unit = { _, _, _ -> },
    val onDeleteSet: (Long) -> Unit = {},
    val onSetLogDismiss: () -> Unit = {},
    val onCardioClick: (ExerciseItem) -> Unit = {},
    val onCardioLogSave: (CardioValues) -> Unit = {},
    val onCardioLogDelete: () -> Unit = {},
    val onCardioLogDismiss: () -> Unit = {},
    val onReorder: (List<Long>) -> Unit = {},
    val onFormChange: (ExerciseForm) -> Unit = {},
    val onVariationToggle: () -> Unit = {},
    val onFormSave: () -> Unit = {},
    val onFormDelete: () -> Unit = {},
    val onFormDismiss: () -> Unit = {},
    val onUndo: (TrainingEvent) -> Unit = {}
)

/**
 * Formularzustand des Bearbeiten-Sheets. Alle Felder sind Text, damit Teileingaben
 * wie „22,“ nicht verloren gehen; umgewandelt wird erst beim Speichern.
 */
data class ExerciseForm(
    val id: Long? = null,
    /**
     * Der Trainingstag, zu dem die Übung gehört – beim Öffnen des Sheets festgehalten, damit
     * eine neue Übung auch dann dort landet, wo der Nutzer sie angelegt hat, wenn die Auswahl
     * inzwischen weitergesprungen ist.
     */
    val dayId: Int = FIRST_DAY_ID,
    val name: String = "",
    val variation: String = "",
    /** Das Variationsfeld erscheint erst auf Wunsch – über das „+“ neben dem Namen. */
    val showVariation: Boolean = false,
    val weight: String = "",
    val sets: String = "",
    val repsMin: String = "",
    val repsMax: String = "",
    val progressionStep: String = DEFAULT_PROGRESSION_STEP_KG.toDecimalString(),
    /**
     * Zeigt der Pfeil in der Liste nach unten? Dann senkt er das Gewicht um den Schritt, statt
     * es zu erhöhen – für alles, was sich abtrainiert statt aufbaut.
     *
     * Anders als die übrigen Felder kein Text: Hier gibt es keine Teileingabe, nur zwei
     * Richtungen.
     */
    val progressionDown: Boolean = false,
    /** Notiz zur Übung, mehrzeilig; hängt wie das Gewicht am Namen (siehe [SharedFormValues]). */
    val note: String = "",
    /**
     * Schalter „Sätze protokollieren“; hängt wie das Gewicht am Namen (siehe [SharedFormValues]).
     * Wie die Richtung kein Text: Es gibt nur an oder aus.
     */
    val logSets: Boolean = false,
    /**
     * Kraft oder Cardio – der Umschalter oben im Sheet; hängt wie das Gewicht am Namen (siehe
     * [SharedFormValues]). Er entscheidet nur, welche Felder zu sehen sind: Die der anderen Art
     * bleiben im Formular und werden mitgespeichert, wie sie sind.
     */
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
    /** Die Cardio-Ziele samt Pfeil; hängen ebenfalls am Namen. */
    val cardio: CardioForm = CardioForm(),
    /** Beim Bearbeiten der Name, unter dem die Übung gespeichert ist; beim Anlegen `null`. */
    val originalName: String? = null,
    /**
     * Die bekannte Übung, deren Gewicht, Schritt, Richtung und Notiz gerade in den Feldern stehen;
     * `null`, solange der Name auf keine passt. Beim Bearbeiten ist das anfangs die Übung selbst.
     */
    val matchedName: String? = null,
    /**
     * Was in diesen Feldern stand, bevor die Werte einer *anderen* bekannten Übung sie ersetzt
     * haben – eigene Eingaben oder die der bearbeiteten Übung. `null` heißt: Was dasteht, ist
     * ohnehin das Eigene. Siehe [withChange].
     */
    val ownValues: SharedFormValues? = null
) {
    val isEditMode: Boolean get() = id != null
    val isCardio: Boolean get() = kind == ExerciseKind.CARDIO

    /**
     * Ein Name muss sein; bei Cardio dazu eine lesbare Dauer. Bei den übrigen Feldern heißt eine
     * unlesbare Eingabe „nicht gesetzt“ – bei der Dauer aber ist „7:75“ ein Tippfehler, den das
     * Feld anzeigt, und kein Ziel, das stillschweigend verschwinden darf.
     */
    val canSave: Boolean get() = name.isNotBlank() && (!isCardio || cardio.isDurationValid)

    val sharedValues: SharedFormValues
        get() = SharedFormValues(weight, progressionStep, progressionDown, note, logSets, kind, cardio)

    /**
     * Übernimmt eine Eingabe aus dem Sheet und hält dabei die Werte am Namen – Gewicht, Schritt,
     * Richtung, Notiz, Protokoll-Schalter, Art und Cardio-Ziele – passend zu dem Namen, der
     * gerade dasteht.
     *
     * Passt der Name auf eine bekannte Übung, kommen deren Werte ins Formular – egal ob getippt
     * oder aus der Vorschlagsliste gewählt. Mit ihnen kommt ihre Art: Wer „Laufband“ tippt,
     * sieht die Cardio-Felder, auch wenn das Sheet gerade auf Kraft stand. Sätze und Wiederholungen bleiben unangetastet, die
     * gehören zum jeweiligen Tag.
     *
     * Passt er nicht mehr, kommen die eigenen Werte zurück ([ownValues]). Ohne das blieben die
     * der zuletzt getroffenen Übung stehen: Wer „Rudern eng“ in „Rudern breit“ umbenennt, kommt
     * beim Löschen unterwegs an „Rudern“ vorbei – und speicherte danach dessen Gewicht unter dem
     * neuen Namen, obwohl niemand das Feld angefasst hat.
     *
     * Bleibt es bei derselben Übung – nur anders geschrieben oder ein Leerzeichen mehr –, bleibt
     * auch stehen, was in den Feldern steht, samt Änderungen von Hand. Erst der Wechsel zu einer
     * *anderen* Übung überschreibt sie.
     *
     * Die Schreibweise wird auf die gespeicherte angeglichen, sonst entstünde aus „bankdrücken“
     * eine zweite Übung neben „Bankdrücken“. Leerzeichen am Rand bleiben dabei stehen: Das hinter
     * „Rudern“ ist der Anfang von „Rudern breit“ und darf beim Tippen nicht verschwinden.
     */
    fun withChange(changed: ExerciseForm, known: List<ExerciseDefinition>): ExerciseForm {
        if (changed.name == name) {
            // Kein neuer Name. Wer einen der Werte am Namen anfasst – auch den Umschalter der Art –,
            // macht sie damit zu seinen eigenen; sie bleiben auch stehen, wenn der Name danach
            // nicht mehr passt.
            return if (changed.sharedValues == sharedValues) changed else changed.copy(ownValues = null)
        }
        val match = known.firstOrNull { it.name.equals(changed.name.trim(), ignoreCase = true) }
        val named = match?.let { changed.copy(name = changed.name.withCore(it.name)) } ?: changed

        if (match?.name == matchedName) return named
        if (match == null || match.name == originalName) {
            return named.withShared(ownValues ?: sharedValues)
                .copy(matchedName = match?.name, ownValues = null)
        }
        return named.withShared(match.toSharedFormValues())
            .copy(matchedName = match.name, ownValues = ownValues ?: sharedValues)
    }

    private fun withShared(values: SharedFormValues) = copy(
        weight = values.weight,
        progressionStep = values.progressionStep,
        progressionDown = values.progressionDown,
        note = values.note,
        logSets = values.logSets,
        kind = values.kind,
        cardio = values.cardio
    )
}

/**
 * Gewicht, Progressionsschritt, Richtung, Notiz, Protokoll-Schalter, Art und Cardio-Ziele – die
 * Felder des Formulars, die nicht an der Zeile hängen, sondern am Namen: Sie gelten für jede
 * gleichnamige Übung (siehe [ExerciseDefinition]).
 */
data class SharedFormValues(
    val weight: String = "",
    val progressionStep: String = DEFAULT_PROGRESSION_STEP_KG.toDecimalString(),
    val progressionDown: Boolean = false,
    val note: String = "",
    val logSets: Boolean = false,
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
    val cardio: CardioForm = CardioForm()
)

private fun ExerciseDefinition.toSharedFormValues() = SharedFormValues(
    weight = weightKg?.toDecimalString().orEmpty(),
    progressionStep = progressionStepKg.toDecimalString(),
    progressionDown = progressionDown,
    note = note.orEmpty(),
    logSets = logSets,
    kind = kind,
    cardio = cardio.toForm()
)

/**
 * Die Cardio-Felder des Formulars. Text wie die übrigen, damit Teileingaben wie „7:“ oder „6,“
 * beim Tippen stehen bleiben; eingelesen wird erst beim Speichern ([toTargets]).
 */
data class CardioForm(
    /** Minuten wie „20“ oder „7,5“, oder mm:ss wie „7:30“ (siehe [parseCardioDuration]). */
    val duration: String = "",
    val distance: String = "",
    val intensity: String = "",
    val intensityUnit: IntensityUnit = IntensityUnit.KMH,
    val incline: String = "",
    /** Welchen Wert der Pfeil verschiebt; `null`: keinen. */
    val arrowValue: CardioValue? = null,
    /** Der Schritt des Pfeils; leer, solange [arrowValue] fehlt. */
    val arrowStep: String = "",
    val arrowDown: Boolean = false
) {
    val isDurationValid: Boolean get() = isValidCardioDurationInput(duration)

    /**
     * Wählt den Wert für den Pfeil. Der Schritt beginnt dabei mit der Vorgabe des neuen Werts:
     * Ein Schritt von 2 Minuten hieße auf der Distanz 2 km – der Pfeil spränge in einer Einheit
     * von 5 auf 7 km.
     */
    fun withArrowValue(value: CardioValue?): CardioForm {
        if (value == arrowValue) return this
        return copy(arrowValue = value, arrowStep = value?.let { defaultStepText(it, intensityUnit) }.orEmpty())
    }

    /**
     * Wechselt zwischen km/h und Stufe. Steuert der Pfeil das Tempo, beginnt sein Schritt aus
     * demselben Grund neu wie bei [withArrowValue]: 0,1 Stufen gibt es an keinem Gerät.
     */
    fun withIntensityUnit(unit: IntensityUnit): CardioForm {
        if (unit == intensityUnit) return this
        val step = if (arrowValue == CardioValue.INTENSITY) defaultStepText(arrowValue, unit) else arrowStep
        return copy(intensityUnit = unit, arrowStep = step)
    }

    /**
     * Die Ziele, wie sie gespeichert werden. Ein leeres Feld heißt: kein Ziel für diesen Wert –
     * anders als beim Gewicht, das ein leeres Feld stehen lässt, sind die Cardio-Ziele allesamt
     * freiwillig. Ohne Wert für den Pfeil gibt es auch keinen Schritt.
     */
    fun toTargets(): CardioTargets = CardioTargets(
        durationMin = parseCardioDuration(duration),
        distanceKm = parseOptionalDecimal(distance),
        intensity = parseOptionalDecimal(intensity),
        intensityUnit = intensityUnit,
        inclinePercent = parseOptionalDecimal(incline),
        arrowValue = arrowValue,
        arrowStep = arrowValue?.let { parseCardioStep(arrowStep, it, intensityUnit) },
        arrowDown = arrowDown
    )
}

private fun defaultStepText(value: CardioValue, unit: IntensityUnit): String =
    formatCardioStepInput(defaultCardioStep(value, unit), value)

/** Die gespeicherten Ziele als Formularfelder – in derselben Schreibweise, die das Einlesen versteht. */
fun CardioTargets.toForm(): CardioForm = CardioForm(
    duration = durationMin?.let(::formatDurationValue).orEmpty(),
    distance = distanceKm?.toDecimalString().orEmpty(),
    intensity = intensity?.toDecimalString().orEmpty(),
    intensityUnit = intensityUnit,
    incline = inclinePercent?.toDecimalString().orEmpty(),
    arrowValue = arrowValue,
    arrowStep = arrowValue?.let { value ->
        formatCardioStepInput(arrowStep ?: defaultCardioStep(value, intensityUnit), value)
    }.orEmpty(),
    arrowDown = arrowDown
)

/** Ersetzt den Text zwischen den Leerzeichen am Rand: `" rudern "` mit `"Rudern"` → `" Rudern "`. */
private fun String.withCore(core: String): String {
    val start = indexOfFirst { !it.isWhitespace() }
    if (start < 0) return core
    return replaceRange(start, indexOfLast { !it.isWhitespace() } + 1, core)
}

/**
 * Einmalige Ereignisse für Snackbars mit „Rückgängig“.
 *
 * Das Abhaken meldet sich hier bewusst nicht: Es nimmt sich selbst zurück, indem man den Haken
 * erneut antippt – siehe [TrainingViewModel.onToggleWorkoutCompleted].
 */
sealed interface TrainingEvent {

    /**
     * Gewicht wurde per Pfeil verschoben – und zwar an allen Tagen, an denen [exerciseName]
     * vorkommt.
     *
     * Ob nach oben oder nach unten, steht nicht als eigenes Feld dabei: Es ergibt sich aus den
     * beiden Gewichten (siehe [isDecrease]), und ein zweiter Weg, dasselbe zu sagen, könnte ihm
     * widersprechen.
     *
     * [previousWeightKg] und [logId] beschreiben zusammen genau diese eine Änderung: wohin
     * zurück und welcher Verlaufspunkt dabei wieder verschwindet. Beides ist nötig, weil vor
     * dem „Rückgängig“ schon die nächste Änderung stehen kann – siehe
     * [de.beispiel.meintraining.data.repository.TrainingRepository.revertWeight].
     */
    data class WeightChanged(
        val exerciseName: String,
        val previousWeightKg: Double,
        val newWeightKg: Double,
        val logId: Long
    ) : TrainingEvent {
        val isDecrease: Boolean get() = newWeightKg < previousWeightKg
    }

    /**
     * Der Pfeil einer Cardio-Übung hat ihren gewählten Wert verschoben – an allen Tagen mit
     * [exerciseName]. [change] trägt alles, was „Rückgängig“ braucht (siehe
     * [de.beispiel.meintraining.data.repository.TrainingRepository.revertCardio]).
     */
    data class CardioChanged(
        val exerciseName: String,
        val change: CardioChange
    ) : TrainingEvent {
        val isDecrease: Boolean get() = change.new < change.previous
    }

    /** Übungen wurden gelöscht; die Kopien erlauben das Wiederherstellen. */
    data class ExercisesDeleted(val exercises: List<ExerciseItem>) : TrainingEvent

    /**
     * Übungen wurden an einen anderen Tag kopiert oder verschoben.
     *
     * [targetDayName] ist der gespeicherte Name des Zieltages, so wie er beim Auslösen hieß;
     * leer heißt wie überall „Tag N“.
     */
    data class ExercisesTransferred(
        val transfer: ExerciseTransfer,
        val targetDayName: String
    ) : TrainingEvent

    /**
     * Eine neue Runde wurde von Hand begonnen.
     *
     * Anders als beim Abhaken lohnt die Meldung hier: Auf dem Bildschirm passiert nur, dass die
     * Haken verschwinden und die Auswahl auf Tag 1 springt – ein Fehlgriff sieht damit fast aus
     * wie ein Fehler. „Rückgängig“ stellt die vorige Runde wieder her.
     */
    data object CycleStarted : TrainingEvent
}
