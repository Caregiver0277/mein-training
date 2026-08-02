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
    val usesBodyweight: Boolean = false
) {
    /**
     * Das Gewicht, mit dem tatsächlich trainiert wird.
     *
     * Bei Körpergewichtsübungen kommt [bodyweightKg] zur eingetragenen Zusatzlast dazu; ohne
     * hinterlegtes Körpergewicht bleibt es beim eingetragenen Wert.
     */
    fun effectiveWeightKg(bodyweightKg: Double?): Double? = when {
        !usesBodyweight -> weightKg
        bodyweightKg == null -> weightKg
        else -> bodyweightKg + (weightKg ?: 0.0)
    }

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
        usesBodyweight = usesBodyweight
    )
}
