package de.beispiel.meintraining.ui

import androidx.compose.runtime.Immutable
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.toDecimalString

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
    /** Tage, für die *heute* ein Eintrag steht – unabhängig von der laufenden Runde. */
    val todaysDayIds: Set<Int> = emptySet(),
    val deload: DeloadStatus = DeloadStatus(),
    /** Selbst vergebene Überschrift; leer heißt: Vorgabe aus den Textressourcen. */
    val appTitle: String = ""
) {
    /** Ist der angezeigte Tag in dieser Runde schon erledigt? */
    val isSelectedDayCompleted: Boolean get() = selectedDayId in completedDayIds

    /**
     * Gilt das Training des angezeigten Tages als eingetragen?
     *
     * Das ist mehr als [isSelectedDayCompleted], und zwar in genau einem Fall: Mit dem letzten
     * Tag einer Runde beginnt die Zählung von vorn und jeder Haken steht wieder auf offen
     * (siehe `completedDaysInRotation`). Wer gerade das letzte Training der Runde abgehakt hat,
     * ist damit fertig – der Eintrag von heute sagt das unabhängig von der Runde.
     *
     * Die Unterscheidung zählt für alles, was auf das Abhaken *antwortet*: Der Schleier über
     * den Übungen bliebe sonst ausgerechnet nach dem letzten Training der Runde liegen, als
     * wäre der Haken nicht angekommen.
     */
    val isSelectedDayConfirmed: Boolean
        get() = isSelectedDayCompleted || selectedDayId in todaysDayIds

    /**
     * Steht der Pfeil zur nächsten Runde bereit?
     *
     * Nur am letzten Tag der Runde und erst, wenn er eingetragen ist: Vorher gibt es nichts
     * abzuschließen, und an jedem anderen Tag wäre der Pfeil ein Sprung mitten in der Runde.
     * Ist der letzte Tag durch, wartet die App sonst bis Mitternacht – der Pfeil überspringt
     * dieses Warten (siehe [TrainingViewModel.onStartNextCycle]).
     */
    val canStartNextCycle: Boolean
        get() = isSelectedDayConfirmed && selectedDayId == days.lastOrNull()?.id

    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()

    private val selectedExercises: List<ExerciseItem>
        get() = exercises.filter { it.id in selectedIds }

    /** Ein Superset braucht mindestens zwei Übungen. */
    val canCreateSuperset: Boolean get() = selectedIds.size >= MIN_SUPERSET_SIZE

    /** Auflösen geht, sobald mindestens eine markierte Zeile zu einem Superset gehört. */
    val canDissolveSuperset: Boolean get() = selectedExercises.any { it.supersetId != null }
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
    val onExerciseClick: (ExerciseItem) -> Unit = {},
    val onExerciseLongClick: (ExerciseItem) -> Unit = {},
    val onSelectionToggle: (ExerciseItem) -> Unit = {},
    val onSelectionClear: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onCreateSuperset: () -> Unit = {},
    val onDissolveSuperset: () -> Unit = {},
    val onProgressClick: (ExerciseItem) -> Unit = {},
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
    val progressionStep: String = DEFAULT_PROGRESSION_STEP_KG.toDecimalString()
) {
    val isEditMode: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank()
}

/**
 * Einmalige Ereignisse für Snackbars mit „Rückgängig“.
 *
 * Das Abhaken meldet sich hier bewusst nicht: Es nimmt sich selbst zurück, indem man den Haken
 * erneut antippt – siehe [TrainingViewModel.onToggleWorkoutCompleted].
 */
sealed interface TrainingEvent {

    /**
     * Gewicht wurde per Pfeil erhöht – und zwar an allen Tagen, an denen [exerciseName]
     * vorkommt. [previousWeightKg] erlaubt das Zurücksetzen.
     */
    data class WeightIncreased(
        val exerciseName: String,
        val previousWeightKg: Double?,
        val newWeightKg: Double
    ) : TrainingEvent

    /** Übungen wurden gelöscht; die Kopien erlauben das Wiederherstellen. */
    data class ExercisesDeleted(val exercises: List<ExerciseItem>) : TrainingEvent
}
