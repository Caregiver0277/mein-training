package de.beispiel.meintraining.util

/**
 * Die Last, mit der tatsächlich trainiert wird.
 *
 * Bei einer Körpergewichtsübung ist [weightKg] nur die Zusatzlast – bei Klimmzügen mit Gürtel
 * etwa die Scheiben –, dazu kommt das eigene Körpergewicht. Ohne hinterlegtes Körpergewicht
 * bleibt es beim eingetragenen Wert, denn raten hilft niemandem.
 *
 * Diese Rechnung gehört ins Tracking und in die Statistiken. Die Trainingsliste zeigt bewusst
 * nur die Zusatzlast: Beim Trainieren interessiert, was auf die Stange kommt.
 */
fun effectiveWeightKg(
    weightKg: Double?,
    usesBodyweight: Boolean,
    bodyweightKg: Double?
): Double? = when {
    !usesBodyweight -> weightKg
    bodyweightKg == null -> weightKg
    else -> bodyweightKg + (weightKg ?: 0.0)
}
