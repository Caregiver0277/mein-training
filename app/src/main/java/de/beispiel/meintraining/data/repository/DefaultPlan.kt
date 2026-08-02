package de.beispiel.meintraining.data.repository

/**
 * Eine Zeile des Trainingsplans.
 *
 * [supersetGroup] fasst innerhalb eines Tages zusammen: Gleiche Nummer heißt gleiches
 * Superset, `null` heißt einzeln. Der Progressionsschritt fehlt bewusst – er bleibt auf
 * dem Standardwert und wird pro Übung von Hand eingestellt.
 */
data class PlanExercise(
    val name: String,
    val variation: String? = null,
    val weightKg: Double? = null,
    val sets: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val supersetGroup: Int? = null
)

data class PlanDay(val dayId: Int, val exercises: List<PlanExercise>)

/**
 * Der Trainingsplan, mit dem die App startet.
 *
 * Gleiche Namen teilen sich das Gewicht – deshalb steht z. B. „Triceps“ an Tag 2 und Tag 4
 * unter demselben Namen und unterscheidet sich nur durch die Variation. Wo Gewichte in der
 * Vorlage addiert notiert waren (etwa „57,5+3,75“), steht hier die Summe.
 */
val DEFAULT_PLAN = listOf(
    PlanDay(
        dayId = 1,
        exercises = listOf(
            PlanExercise("hex bar deadlift", weightKg = 20.0, sets = 3, repsMin = 4, repsMax = 6),
            PlanExercise("ATG Split Squat", sets = 3, repsMin = 6, repsMax = 10),
            PlanExercise("Patrick Steps", sets = 3, repsMin = 6, repsMax = 10),
            PlanExercise("leg curls", weightKg = 61.25, sets = 3, repsMin = 8, repsMax = 12),
            PlanExercise("hyperextension", weightKg = 5.0, sets = 3, repsMin = 8, repsMax = 15),
            PlanExercise("Tibialis Raise", weightKg = 7.5, sets = 3, repsMin = 15, repsMax = 20),
            PlanExercise("Dead Bug", sets = 3, repsMin = 8, repsMax = 8),
            PlanExercise("Hüfte dehn routine"),
            PlanExercise("couch stretch")
        )
    ),
    PlanDay(
        dayId = 2,
        exercises = listOf(
            PlanExercise("pull ups", variation = "schwarz", sets = 3, repsMin = 5, repsMax = 9),
            PlanExercise("pushup", sets = 4, repsMin = 10, repsMax = 20),
            PlanExercise("Chest supported row", weightKg = 50.0, sets = 3, repsMin = 8, repsMax = 12),
            PlanExercise("bench press machine", weightKg = 15.0, sets = 3, repsMin = 8, repsMax = 12),
            PlanExercise(
                "Triceps", weightKg = 21.25, sets = 3, repsMin = 10, repsMax = 15,
                supersetGroup = 1
            ),
            PlanExercise(
                "Cable lateral raise", weightKg = 3.75, sets = 3, repsMin = 10, repsMax = 15,
                supersetGroup = 1
            ),
            PlanExercise(
                "Face pulls", variation = "Seil", weightKg = 6.25, sets = 3, repsMin = 10,
                repsMax = 15, supersetGroup = 1
            ),
            PlanExercise("Bizep curls", weightKg = 27.5, sets = 3, repsMin = 10, repsMax = 15),
            PlanExercise("sitzendes Wadenheben", weightKg = 7.5, sets = 3, repsMin = 12, repsMax = 16)
        )
    ),
    PlanDay(
        dayId = 3,
        exercises = listOf(
            PlanExercise("Bulgarian split squat", weightKg = 6.0, sets = 3, repsMin = 6, repsMax = 10),
            PlanExercise("hip thrust", weightKg = 20.0, sets = 3, repsMin = 8, repsMax = 12),
            PlanExercise("Nordic curl", sets = 2, repsMin = 4, repsMax = 6),
            PlanExercise(
                "side lying Hyperextension", weightKg = 5.0, sets = 3, repsMin = 8, repsMax = 12
            ),
            PlanExercise("Adductor/Abductor", weightKg = 85.0),
            PlanExercise("Dead Bug", sets = 3, repsMin = 8, repsMax = 8, supersetGroup = 1),
            PlanExercise(
                "Side plank leg raises", sets = 3, repsMin = 10, repsMax = 10, supersetGroup = 1
            ),
            PlanExercise(
                "knöchel innen und außen mit band", sets = 3, repsMin = 15, repsMax = 15,
                supersetGroup = 1
            ),
            PlanExercise("Hüfte dehn routine"),
            PlanExercise("couch stretch")
        )
    ),
    PlanDay(
        dayId = 4,
        exercises = listOf(
            PlanExercise("pushup", sets = 4, repsMin = 10, repsMax = 20),
            PlanExercise("pull ups", variation = "schwarz", sets = 3, repsMin = 5, repsMax = 9),
            PlanExercise("Chest supported row", weightKg = 50.0, sets = 3, repsMin = 8, repsMax = 12),
            PlanExercise("Landmine press", weightKg = 10.0, sets = 4, repsMin = 8, repsMax = 12),
            PlanExercise(
                "Face pulls", variation = "Seil", weightKg = 6.25, sets = 2, repsMin = 10,
                repsMax = 15, supersetGroup = 1
            ),
            PlanExercise(
                "Cable lateral raise", weightKg = 3.75, sets = 3, repsMin = 10, repsMax = 15,
                supersetGroup = 1
            ),
            PlanExercise(
                "Triceps", variation = "overhead", weightKg = 21.25, sets = 3, repsMin = 10,
                repsMax = 15, supersetGroup = 1
            ),
            PlanExercise("Bizep curls", weightKg = 27.5, sets = 3, repsMin = 10, repsMax = 15),
            PlanExercise("Carries", weightKg = 16.0, sets = 3, repsMin = 40, repsMax = 40),
            PlanExercise("Wadenheben", variation = "einbeinig", sets = 3, repsMin = 10, repsMax = 14)
        )
    )
)
