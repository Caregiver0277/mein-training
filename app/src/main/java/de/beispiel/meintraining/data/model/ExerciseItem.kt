package de.beispiel.meintraining.data.model

import androidx.room.Embedded

/**
 * Eine Übung eines Tages zusammen mit ihren geteilten Werten – das Modell, mit dem
 * Oberfläche und ViewModel arbeiten. Entsteht aus [Exercise] und [ExerciseDefinition].
 */
data class ExerciseItem(
    val id: Long,
    val dayId: Int,
    val name: String,
    val variation: String?,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val position: Int,
    val supersetId: Long?,
    val weightKg: Double?,
    val progressionStepKg: Double,
    /** Senkt der Pfeil das Gewicht, statt es zu erhöhen? Siehe [ExerciseDefinition]. */
    val progressionDown: Boolean = false,
    /** Die Notiz zur Übung; siehe [ExerciseDefinition.note]. */
    val note: String? = null,
    /** Siehe [ExerciseDefinition.logSets]. */
    val logSets: Boolean = false,
    /** Siehe [ExerciseDefinition.kind]. */
    val kind: ExerciseKind = ExerciseKind.STRENGTH,
    /** Siehe [ExerciseDefinition.cardio]. */
    @Embedded(prefix = CARDIO_COLUMN_PREFIX)
    val cardio: CardioTargets = CardioTargets()
) {
    val isCardio: Boolean get() = kind == ExerciseKind.CARDIO

    fun toExercise() = Exercise(
        id = id,
        dayId = dayId,
        name = name,
        variation = variation,
        sets = sets,
        repsMin = repsMin,
        repsMax = repsMax,
        position = position,
        supersetId = supersetId
    )

    fun toDefinition() = ExerciseDefinition(
        name = name,
        weightKg = weightKg,
        progressionStepKg = progressionStepKg,
        progressionDown = progressionDown,
        note = note,
        logSets = logSets,
        kind = kind,
        cardio = cardio
    )
}
