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

/**
 * Die Superset-Kennungen der Übungen, die an einen anderen Tag kopiert oder verschoben werden.
 *
 * Ein Superset, das *vollständig* mitwandert, bleibt im Ziel eines – mit neuer Kennung, damit es
 * sich nicht mit dem Original (beim Kopieren) oder einem Superset des Zieltages vermischt. Ein
 * Superset, von dem nur ein Teil mitkommt, löst sich im Ziel auf: Die Hälfte eines Paars ist dort
 * kein Superset mehr, und zwei Mitglieder von dreien sollen nicht stillschweigend ein neues bilden.
 *
 * [movedSupersetIds] sind die Kennungen der mitwandernden Zeilen in Anzeigereihenfolge,
 * [sourceSupersetIds] die *aller* Zeilen des Ausgangstages – nur so lässt sich sagen, ob ein
 * Superset vollständig dabei ist. Neue Kennungen werden ab [firstNewId] vergeben, in der
 * Reihenfolge, in der die Supersets auftauchen.
 *
 * Zurück kommt für jede mitwandernde Zeile ihre Kennung im Ziel (`null` = kein Superset).
 */
fun supersetsAfterTransfer(
    movedSupersetIds: List<Long?>,
    sourceSupersetIds: List<Long?>,
    firstNewId: Long
): List<Long?> {
    val sourceSizes = sourceSupersetIds.filterNotNull().groupingBy { it }.eachCount()
    val movedSizes = movedSupersetIds.filterNotNull().groupingBy { it }.eachCount()
    val complete = movedSizes.filter { (id, count) -> count == sourceSizes[id] }.keys
    val renamed = movedSupersetIds.filterNotNull().distinct().filter { it in complete }
        .withIndex().associate { (index, id) -> id to firstNewId + index }
    return movedSupersetIds.map { id -> id?.let { renamed[it] } }
}

/**
 * Rückt Zeilen, die beim Umsortieren mitten in einem Superset gelandet sind, hinter dessen Block.
 *
 * Gemeint sind die [pinned] Zeilen: die ausgeblendeten, die beim Umsortieren ihren Platz zwischen
 * den sichtbaren behalten (siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.reorderExercises]). Fällt dieser
 * Platz zwischen zwei Mitglieder desselben Supersets, trennte die unsichtbare Zeile den Block –
 * und [survivingSupersetMembers] löste ihn danach auf, obwohl auf dem Bildschirm alle Mitglieder
 * beieinanderstanden. Wer eine Übung über ein Superset schiebt, verlöre es so, ohne es angefasst
 * zu haben.
 *
 * Ausgeblendete Mitglieder desselben Supersets bleiben, wo sie sind: Sie gehören in den Block.
 * Alle übrigen Zeilen behalten ihre Reihenfolge.
 *
 * [orderedIds] und [supersetIds] beschreiben wie bei [survivingSupersetMembers] dieselbe Liste.
 */
fun keepSupersetBlocksTogether(
    orderedIds: List<Long>,
    supersetIds: List<Long?>,
    pinned: Set<Long>
): List<Long> {
    require(orderedIds.size == supersetIds.size) {
        "orderedIds und supersetIds müssen gleich lang sein"
    }

    val result = ArrayList<Long>(orderedIds.size)
    // Zurückgestellt, bis der Block zu Ende ist, in dem sie gelandet sind.
    val waiting = mutableListOf<Long>()
    // Das Superset der zuletzt gesetzten beweglichen Zeile; `null` heißt: kein Block offen.
    var block: Long? = null
    orderedIds.forEachIndexed { index, id ->
        val superset = supersetIds[index]
        if (id in pinned) {
            if (block != null && superset != block) waiting += id else result += id
        } else {
            if (superset != block) {
                result += waiting
                waiting.clear()
            }
            block = superset
            result += id
        }
    }
    result += waiting
    return result
}
