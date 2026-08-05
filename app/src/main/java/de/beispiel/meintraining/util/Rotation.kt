package de.beispiel.meintraining.util

import java.time.LocalDate

/** Ein abgehaktes Training, auf das eingedampft, was die Runde davon braucht. */
data class RotationEntry(val dayId: Int, val date: LocalDate)

/**
 * Die Trainingstage, die in der laufenden Runde schon abgehakt sind.
 *
 * Eine Runde ist durch, sobald jeder Tag einmal dran war – aber die Haken bleiben bis
 * Mitternacht stehen. Wer das letzte Training der Runde abhakt, hat etwas geschafft und soll
 * das für den Rest des Tages auch sehen; eine Anzeige, die im selben Moment wieder auf null
 * springt, nimmt genau diesen Abschluss weg. Erst der nächste Kalendertag räumt sie ab, und
 * der nächste Eintrag beginnt die neue Runde – auch wenn er noch am selben Abend kommt.
 *
 * [entriesOldestFirst] sind die abgehakten Trainings in der Reihenfolge, in der sie eingetragen
 * wurden, [today] das laufende Datum (siehe [CurrentDate], damit über Nacht nicht das Datum von
 * gestern gilt).
 *
 * Diese Menge sagt, was *angezeigt* wird, und ist bewusst keine Grundlage dafür, ob ein Tippen
 * auf den Haken einträgt oder zurücknimmt – siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.toggleWorkout].
 */
fun completedDaysInRotation(
    entriesOldestFirst: List<RotationEntry>,
    dayCount: Int,
    today: LocalDate
): Set<Int> {
    // Ohne Tage gibt es keine Runde; ohne diese Abfrage gälte jede Runde sofort als voll.
    if (dayCount <= 0) return emptySet()

    val done = mutableSetOf<Int>()
    var isFull = false
    entriesOldestFirst.forEach { entry ->
        // Eine volle Runde bleibt bis Mitternacht stehen – der nächste Eintrag beginnt trotzdem
        // die neue. Wer am selben Abend noch einmal trainiert, sieht also einen Tag von vorn.
        if (isFull) {
            done.clear()
            isFull = false
        }
        done += entry.dayId
        if (done.size >= dayCount) {
            // Nur was vor heute liegt, ist abgelaufen. Ein Eintrag von heute – oder ein durch
            // Zeitumstellung oder eingelesene Sicherung in der Zukunft gelandeter – bleibt.
            if (entry.date.isBefore(today)) done.clear() else isFull = true
        }
    }
    return done
}

/** Der auf [dayId] folgende Trainingstag; nach dem letzten geht es wieder bei 1 los. */
fun nextDayId(dayId: Int, dayCount: Int): Int = if (dayId >= dayCount) 1 else dayId + 1
