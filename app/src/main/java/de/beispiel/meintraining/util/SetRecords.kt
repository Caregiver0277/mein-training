package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.SetLog
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bis zu so vielen Wiederholungen taugt ein Satz für das geschätzte 1RM. Darüber schätzt Epley
 * zunehmend zu hoch – ein Satz mit 20 Wiederholungen sagt mehr über Ausdauer als über Maximalkraft.
 */
const val EPLEY_MAX_REPS = 12

/**
 * Das geschätzte Maximalgewicht für eine Wiederholung nach Epley: Gewicht × (1 + Wdh. / 30).
 *
 * Ein Satz mit einer Wiederholung *ist* das 1RM – Epley ergäbe dort 3 % mehr, als bewegt wurde.
 * `null` für Sätze, die nicht taugen: ohne Gewicht, ohne Wiederholung oder über [EPLEY_MAX_REPS].
 */
fun estimatedOneRepMax(weightKg: Double?, reps: Int): Double? {
    if (weightKg == null || weightKg <= 0.0 || reps !in 1..EPLEY_MAX_REPS) return null
    return if (reps == 1) weightKg else weightKg * (1 + reps / EPLEY_DIVISOR)
}

private const val EPLEY_DIVISOR = 30.0

/** Das geschätzte 1RM dieses Satzes; siehe [estimatedOneRepMax]. */
val SetLog.oneRepMax: Double? get() = estimatedOneRepMax(weightKg, reps)

/** Ein Punkt im Verlauf des geschätzten 1RM: der beste Satz eines Trainingstags im Kalender. */
data class OneRepMaxPoint(val date: LocalDate, val performedAt: Long, val kg: Double)

/**
 * Der Verlauf des geschätzten 1RM, ein Punkt je Kalendertag mit einem tauglichen Satz, älteste
 * zuerst. Je Tag zählt der beste Satz – ein schwächerer Back-off-Satz danach ist kein Rückschritt.
 */
fun oneRepMaxHistory(
    setsOldestFirst: List<SetLog>,
    zone: ZoneId = ZoneId.systemDefault()
): List<OneRepMaxPoint> = setsOldestFirst
    .mapNotNull { set -> set.oneRepMax?.let { set to it } }
    .groupBy { (set, _) -> set.performedAt.toLocalDate(zone) }
    .map { (date, entries) ->
        val (set, kg) = entries.maxBy { it.second }
        OneRepMaxPoint(date, set.performedAt, kg)
    }
    .sortedBy { it.date }

/**
 * Der beste Satz: der mit dem höchsten geschätzten 1RM – so lassen sich 100 kg × 3 und 85 kg × 10
 * vergleichen. Bei Gleichstand der frühere, er hat es zuerst geschafft.
 *
 * Taugt kein Satz dafür (nur Sätze über [EPLEY_MAX_REPS]), ist es der schwerste, bei gleichem
 * Gewicht der mit mehr Wiederholungen. Ohne einen Satz mit Gewicht `null`.
 */
fun bestSet(sets: List<SetLog>): SetLog? {
    val weighted = sets.filter { (it.weightKg ?: 0.0) > 0.0 && it.reps > 0 }
    // maxWith liefert bei Gleichstand das erste Maximum – nach Zeit sortiert also das früheste.
    val byTime = weighted.sortedWith(compareBy({ it.performedAt }, { it.id }))
    return byTime.filter { it.oneRepMax != null }
        .maxWithOrNull(compareBy { it.oneRepMax!! })
        ?: byTime.maxWithOrNull(compareBy({ it.weightKg!! }, { it.reps }))
}

/**
 * Ein Rekord: das schwerste Gewicht, das für [reps] Wiederholungen bewegt wurde, und wann zum
 * ersten Mal.
 */
data class RepRecord(val reps: Int, val weightKg: Double, val performedAt: Long)

/**
 * Die Rekorde je Wiederholungszahl, aufsteigend nach Wiederholungen – die Tabelle „Rekorde“.
 *
 * Nur echte Rekorde bleiben stehen: Wer 80 kg × 8 geschafft hat, hat damit auch 80 kg für 6
 * geschafft; stünde dort 75 kg × 6, wäre das kein Rekord, sondern ein älterer Satz. Eine Zeile
 * fällt deshalb weg, wenn eine höhere Wiederholungszahl mindestens dasselbe Gewicht hat.
 */
fun repRecords(sets: List<SetLog>): List<RepRecord> {
    val best = sets
        .filter { (it.weightKg ?: 0.0) > 0.0 && it.reps > 0 }
        .sortedWith(compareBy({ it.performedAt }, { it.id }))
        .groupBy { it.reps }
        .map { (reps, ofReps) ->
            // maxBy nimmt bei Gleichstand das erste – den Tag, an dem das Gewicht erstmals ging.
            val top = ofReps.maxBy { it.weightKg!! }
            RepRecord(reps, top.weightKg!!, top.performedAt)
        }
        .sortedBy { it.reps }
    return best.filterIndexed { index, record ->
        best.drop(index + 1).none { it.weightKg >= record.weightKg }
    }
}

/** Das Volumen einer Woche: die Summe aus Wiederholungen × Gewicht. */
data class WeekVolume(val weekStart: LocalDate, val volumeKg: Double)

/**
 * Das Volumen je Woche für die letzten [weeks] Wochen, älteste zuerst, die laufende zuletzt;
 * Wochen ohne Satz stehen mit 0 darin. Ein Satz ohne Gewicht trägt nichts bei.
 */
fun weeklyVolume(
    sets: List<SetLog>,
    today: LocalDate,
    weeks: Int = GOAL_WEEKS,
    zone: ZoneId = ZoneId.systemDefault()
): List<WeekVolume> {
    val byWeek = sets.groupBy { it.performedAt.toLocalDate(zone).weekStart() }
    val current = today.weekStart()
    return (weeks - 1 downTo 0).map { back ->
        val start = current.minusWeeks(back.toLong())
        WeekVolume(start, byWeek[start].orEmpty().sumOf { it.reps * (it.weightKg ?: 0.0) })
    }
}
