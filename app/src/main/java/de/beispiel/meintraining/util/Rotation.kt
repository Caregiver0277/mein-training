package de.beispiel.meintraining.util

import java.time.LocalDate

/** Ein abgehaktes Training, auf das eingedampft, was die Runde davon braucht. */
data class RotationEntry(
    val dayId: Int,
    val date: LocalDate,
    /**
     * Zeitstempel des Eintrags – gebraucht wird er für die Rundenschnitte (siehe [rotations]).
     * Er steht neben dem Datum und nicht an dessen Stelle, weil aus einem Zeitstempel ein Datum
     * zu machen der teuerste Schritt der ganzen Rechnung ist und genau einmal passieren soll.
     */
    val completedAt: Long = NO_ROTATION_CUT
)

/**
 * Kein Rundenschnitt: Es zählt der ganze Verlauf.
 *
 * Zugleich der Vorgabewert für [RotationEntry.completedAt] – ein Eintrag ohne Zeitstempel liegt
 * damit nie hinter einem Schnitt.
 */
const val NO_ROTATION_CUT = 0L

/**
 * Eine Runde: der Abschnitt des Verlaufs, in dem jeder Trainingstag einmal drankommt.
 *
 * [entryIndices] verweist auf die Stellen in der Liste, aus der die Runde entstanden ist –
 * so kommt jeder, der mehr über einen Eintrag weiß als [RotationEntry], wieder an seine Daten.
 */
data class Rotation(
    /** Stellen der zugehörigen Einträge in der übergebenen Liste, älteste zuerst. */
    val entryIndices: List<Int> = emptyList(),
    /** Die in dieser Runde abgehakten Trainingstage. */
    val completedDayIds: Set<Int> = emptySet(),
    /** War jeder Tag der Runde einmal dran? */
    val isFull: Boolean = false,
    /** Datum des jüngsten Eintrags; `null`, solange die Runde leer ist. */
    val lastDate: LocalDate? = null
) {
    val isEmpty: Boolean get() = entryIndices.isEmpty()
}

/**
 * Zerlegt den Verlauf in Runden – die laufende steht am Ende.
 *
 * Eine Runde ist durch, sobald jeder Tag einmal dran war; der nächste Eintrag beginnt die
 * folgende. Damit ergeben sich die Rundengrenzen aus dem Verlauf selbst: Wer ein vergessenes
 * Training nachträgt, bekommt es in genau der Runde gutgeschrieben, in die sein Zeitpunkt fällt,
 * und nicht in der laufenden.
 *
 * [cuts] sind die von Hand gezogenen Schnitte (aufsteigend, siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.startNextRotation]): Wer die
 * nächste Runde beginnt, ohne alle Tage geschafft zu haben, schließt die laufende hier ab. Alles
 * bis einschließlich des Schnitts gehört zur vorigen Runde.
 *
 * Die letzte zurückgegebene Runde ist immer die *laufende* – auch wenn sie leer ist. Leer ist sie
 * in zwei Fällen: hinter dem jüngsten Eintrag steht ein Schnitt, oder die vorige Runde ist voll
 * und ihr letztes Training liegt vor [today]. Denn eine volle Runde bleibt bis Mitternacht
 * stehen: Wer das letzte Training abhakt, hat etwas geschafft und soll das für den Rest des Tages
 * auch sehen.
 *
 * [entriesOldestFirst] muss nach [RotationEntry.completedAt] aufsteigend sortiert sein.
 * Einträge auf Tagen jenseits von [dayCount] – etwa nach einer verkürzten Runde – bleiben in
 * ihrer Runde stehen, zählen aber nicht mit: Sonst machten drei verborgene Tage eine Runde voll,
 * die auf dem Bildschirm noch bei null steht.
 */
fun rotations(
    entriesOldestFirst: List<RotationEntry>,
    dayCount: Int,
    today: LocalDate,
    cuts: List<Long> = emptyList()
): List<Rotation> {
    val result = mutableListOf<Rotation>()
    var current = RotationBuilder(dayCount)
    var cutIndex = 0

    entriesOldestFirst.forEachIndexed { index, entry ->
        // Alle Schnitte vor diesem Eintrag aufbrauchen; mehrere hintereinander ergeben nur
        // dann Runden, wenn zwischen ihnen auch trainiert wurde.
        var isBehindCut = false
        while (cutIndex < cuts.size && cuts[cutIndex] < entry.completedAt) {
            cutIndex++
            isBehindCut = true
        }
        if (!current.isEmpty && (isBehindCut || current.isFull)) {
            result += current.build()
            current = RotationBuilder(dayCount)
        }
        current.add(index, entry)
    }
    if (!current.isEmpty) {
        result += current.build()
        current = RotationBuilder(dayCount)
    }

    val last = result.lastOrNull()
    val startsFresh = last == null ||
        // Ein Schnitt hinter dem jüngsten Eintrag: Die laufende Runde hat noch nichts gesehen.
        cutIndex < cuts.size ||
        // Nur was vor heute liegt, ist abgelaufen. Ein Eintrag von heute – oder ein durch
        // Zeitumstellung oder eingelesene Sicherung in der Zukunft gelandeter – bleibt stehen.
        (last.isFull && last.lastDate?.isBefore(today) == true)
    if (startsFresh) result += current.build()
    return result
}

/**
 * Die Trainingstage, die in der laufenden Runde schon abgehakt sind.
 *
 * Diese Menge sagt, was *angezeigt* wird, und ist bewusst keine Grundlage dafür, ob ein Tippen
 * auf den Haken einträgt oder zurücknimmt – siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.toggleWorkout].
 */
fun completedDaysInRotation(
    entriesOldestFirst: List<RotationEntry>,
    dayCount: Int,
    today: LocalDate,
    cuts: List<Long> = emptyList()
): Set<Int> = rotations(entriesOldestFirst, dayCount, today, cuts).last().completedDayIds

/**
 * Lässt sich der jüngste Schnitt noch zurücknehmen?
 *
 * Genau dann, wenn seit ihm nicht trainiert wurde – der Fall, für den es das Zurücknehmen gibt:
 * danebengetippt, zu früh weitergeschaltet. Sobald das erste Training der neuen Runde steht, ist
 * die Runde angebrochen und der Weg führt vorwärts; wer trotzdem zurückwill, nimmt den Eintrag im
 * Verlauf heraus.
 *
 * Automatisch entstandene Grenzen – volle Runden – lassen sich nicht zurücknehmen: Dort ist
 * nichts übersprungen worden, es war schlicht alles dran.
 */
fun canUndoRotationCut(entriesOldestFirst: List<RotationEntry>, cuts: List<Long>): Boolean {
    val cut = cuts.lastOrNull() ?: return false
    val last = entriesOldestFirst.lastOrNull() ?: return false
    return cut >= last.completedAt
}

/** Der auf [dayId] folgende Trainingstag; nach dem letzten geht es wieder bei 1 los. */
fun nextDayId(dayId: Int, dayCount: Int): Int = if (dayId >= dayCount) 1 else dayId + 1

/** Sammelt eine Runde ein, während [rotations] den Verlauf durchgeht. */
private class RotationBuilder(private val dayCount: Int) {

    private val entryIndices = mutableListOf<Int>()
    // Die Reihenfolge bleibt erhalten: Beim Ansehen einer Runde interessiert, in welcher
    // Reihenfolge trainiert wurde, nicht die Sortierung einer Hash-Menge.
    private val completedDayIds = LinkedHashSet<Int>()
    private var lastDate: LocalDate? = null

    val isEmpty: Boolean get() = entryIndices.isEmpty()

    /** Ohne Trainingstage gibt es keine volle Runde – sonst gälte jede sofort als voll. */
    val isFull: Boolean get() = dayCount > 0 && completedDayIds.size >= dayCount

    fun add(index: Int, entry: RotationEntry) {
        entryIndices += index
        if (entry.dayId in 1..dayCount) completedDayIds += entry.dayId
        lastDate = entry.date
    }

    fun build() = Rotation(
        entryIndices = entryIndices.toList(),
        completedDayIds = LinkedHashSet(completedDayIds),
        isFull = isFull,
        lastDate = lastDate
    )
}
