package de.beispiel.meintraining.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Vorgabe für die Blocklänge: fünf Wochen Training, die sechste ist die Deload-Woche. */
const val DEFAULT_DELOAD_CYCLE_WEEKS = 6

/** Grenzen für die einstellbare Blocklänge. */
const val MIN_CYCLE_WEEKS = 2
const val MAX_CYCLE_WEEKS = 16

/**
 * Ab so vielen Tagen ohne Training gilt die Pause selbst als Deload – der Block beginnt
 * dann von vorn, sobald wieder trainiert wird.
 */
const val REST_RESETS_CYCLE_DAYS = 7L

/** Stand des Deload-Zyklus. */
data class DeloadStatus(
    /** Jetzt ist Deload-Woche: gleiche Gewichte, halbierte Sätze. */
    val isDeloadWeek: Boolean = false,
    /** Eingestellte Blocklänge in Wochen. */
    val cycleWeeks: Int = DEFAULT_DELOAD_CYCLE_WEEKS,
    /** Woche innerhalb des Blocks, 1 bis [cycleWeeks]. */
    val weekInCycle: Int = 1,
    /** Erster Trainingstag des laufenden Blocks. */
    val cycleStart: LocalDate? = null,
    /**
     * Erster Tag der kommenden – oder laufenden – Deload-Woche.
     *
     * Gezählt wird in Sieben-Tage-Blöcken ab [cycleStart], nicht im Kalender: Der Wert fällt
     * deshalb auf denselben Wochentag wie der erste Trainingstag des Blocks und ist bewusst
     * kein Montag.
     */
    val deloadWeekStart: LocalDate? = null,
    val lastSessionDate: LocalDate? = null,
    /** Aktuell läuft eine längere Pause; sie ersetzt den Deload. */
    val isResting: Boolean = false,
    /** Abgehakte Trainings im laufenden Block. */
    val sessionsInCycle: Int = 0,
    val totalSessions: Int = 0
) {
    /** Verbleibende Wochen bis zur Deload-Woche; 0 während der Deload-Woche. */
    val weeksUntilDeload: Int
        get() = if (isDeloadWeek) 0 else cycleWeeks - weekInCycle
}

/**
 * Ermittelt aus den Trainingstagen, ob ein Deload ansteht.
 *
 * Gezählt wird nicht der Kalender, sondern durchgehendes Training: Der Block startet mit dem
 * ersten Training und beginnt nach jeder Pause von [REST_RESETS_CYCLE_DAYS] Tagen neu – eine
 * trainingsfreie Woche erholt genauso wie ein Deload, ein weiterer wäre dann sinnlos.
 * In der [cycleWeeks]-ten Woche eines Blocks ist Deload.
 */
fun deloadStatus(
    sessionDates: List<LocalDate>,
    today: LocalDate,
    cycleWeeks: Int = DEFAULT_DELOAD_CYCLE_WEEKS
): DeloadStatus {
    val weeks = cycleWeeks.coerceIn(MIN_CYCLE_WEEKS, MAX_CYCLE_WEEKS)
    val days = sessionDates.distinct().sorted().filterNot { it.isAfter(today) }
    if (days.isEmpty()) return DeloadStatus(cycleWeeks = weeks)

    // Blockbeginn ist der erste Trainingstag nach der letzten längeren Pause.
    var cycleStart = days.first()
    for (index in 1..days.lastIndex) {
        val pause = ChronoUnit.DAYS.between(days[index - 1], days[index])
        if (pause >= REST_RESETS_CYCLE_DAYS) cycleStart = days[index]
    }

    val lastSession = days.last()
    val isResting = ChronoUnit.DAYS.between(lastSession, today) >= REST_RESETS_CYCLE_DAYS

    val weeksTrained = ChronoUnit.DAYS.between(cycleStart, today) / 7
    val weekInCycle = (weeksTrained % weeks).toInt() + 1
    val completedCycles = weeksTrained / weeks
    val deloadWeekStart = cycleStart.plusWeeks(completedCycles * weeks + weeks - 1)

    return DeloadStatus(
        // Während einer laufenden Pause bringt ein Deload nichts mehr.
        isDeloadWeek = !isResting && weekInCycle == weeks,
        cycleWeeks = weeks,
        weekInCycle = if (isResting) 1 else weekInCycle,
        cycleStart = cycleStart,
        deloadWeekStart = deloadWeekStart,
        lastSessionDate = lastSession,
        isResting = isResting,
        sessionsInCycle = days.count { !it.isBefore(cycleStart) },
        totalSessions = days.size
    )
}

/**
 * Sätze für die Deload-Woche: halbiert, aber aufgerundet und nie unter einem Satz.
 * `3 → 2`, `4 → 2`, `2 → 1`, `1 → 1`. Aufrunden, weil ein Deload das Volumen senken soll,
 * ohne das Training auf ein Alibi zusammenzustreichen.
 */
fun deloadSets(sets: Int?): Int? = sets?.let { ceil(it / 2.0).toInt().coerceAtLeast(1) }
