package de.beispiel.meintraining.ui

import androidx.compose.runtime.Immutable
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.FIRST_DAY_ID
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.MIN_SUPERSET_SIZE
import de.beispiel.meintraining.util.toDecimalString
import java.time.LocalDate

/** Kompletter Zustand des Hauptscreens. */
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
    val deload: DeloadStatus = DeloadStatus(),
    /** Selbst vergebene Überschrift; leer heißt: Vorgabe aus den Textressourcen. */
    val appTitle: String = "",
    /**
     * Das heutige Datum. Steht hier, statt in den Screens einzeln geholt zu werden, damit eine
     * über Nacht offen gebliebene App nicht bei gestern stehen bleibt: Das ViewModel schreibt
     * den Wert beim Zurückkehren in den Vordergrund fort.
     */
    val today: LocalDate = LocalDate.now()
) {
    val isLoading: Boolean get() = days.isEmpty()

    /** Ist der angezeigte Tag in dieser Runde schon erledigt? */
    val isSelectedDayCompleted: Boolean get() = selectedDayId in completedDayIds

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
    val onCompleteWorkout: () -> Unit = {},
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

/** Einmalige Ereignisse für Snackbars mit „Rückgängig“. */
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

    /** Training wurde abgehakt; [sessionId] erlaubt das Zurücknehmen. */
    data class WorkoutCompleted(val sessionId: Long) : TrainingEvent
}
