package de.beispiel.meintraining.data.model

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
    val progressionDown: Boolean = false
) {
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
        progressionDown = progressionDown
    )
}
