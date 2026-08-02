package de.beispiel.meintraining.util

/** Mindestzahl an Übungen für ein Superset. */
const val MIN_SUPERSET_SIZE = 2

/**
 * Bestimmt, welche Superset-Mitglieder nach einer Umsortierung zusammenbleiben.
 *
 * Ein Superset ist nur als zusammenhängender Block sinnvoll. Deshalb behält jede Gruppe nur
 * ihren längsten durchgehenden Lauf; wer herausgezogen wurde, ist draußen, und ein Rest von
 * weniger als [minSize] Übungen löst sich ganz auf.
 *
 * [orderedIds] und [supersetIds] beschreiben dieselbe Liste in Anzeigereihenfolge: an Index i
 * steht die Kennung der Übung und die Kennung ihres Supersets (`null` = keins).
 *
 * Zurück kommen die Kennungen der Übungen, die ihr Superset behalten dürfen.
 */
fun survivingSupersetMembers(
    orderedIds: List<Long>,
    supersetIds: List<Long?>,
    minSize: Int = MIN_SUPERSET_SIZE
): Set<Long> {
    require(orderedIds.size == supersetIds.size) {
        "orderedIds und supersetIds müssen gleich lang sein"
    }

    val surviving = mutableSetOf<Long>()
    orderedIds.indices
        .filter { supersetIds[it] != null }
        .groupBy { supersetIds[it] }
        .forEach { (_, positions) ->
            val sorted = positions.sorted()
            var runStart = 0
            var bestStart = 0
            var bestLength = 0
            sorted.indices.forEach { i ->
                if (i > 0 && sorted[i] != sorted[i - 1] + 1) runStart = i
                val length = i - runStart + 1
                if (length > bestLength) {
                    bestLength = length
                    bestStart = runStart
                }
            }
            if (bestLength >= minSize) {
                (bestStart until bestStart + bestLength).forEach { offset ->
                    surviving += orderedIds[sorted[offset]]
                }
            }
        }
    return surviving
}
