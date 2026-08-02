package de.beispiel.meintraining.util

/**
 * Die Trainingstage, die in der laufenden Runde schon abgehakt sind.
 *
 * Eine Runde ist durch, sobald jeder Tag einmal dran war – dann fängt die Zählung von vorn an
 * und alle Haken sind wieder offen. [dayIdsOldestFirst] sind die abgehakten Tage in der
 * Reihenfolge, in der sie eingetragen wurden.
 */
fun completedDaysInRotation(dayIdsOldestFirst: List<Int>, dayCount: Int): Set<Int> {
    val done = mutableSetOf<Int>()
    dayIdsOldestFirst.forEach { dayId ->
        done += dayId
        if (done.size >= dayCount) done.clear()
    }
    return done
}

/** Der auf [dayId] folgende Trainingstag; nach dem letzten geht es wieder bei 1 los. */
fun nextDayId(dayId: Int, dayCount: Int): Int = if (dayId >= dayCount) 1 else dayId + 1
