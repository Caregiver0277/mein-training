package de.beispiel.meintraining.util

import java.time.LocalDate

/** Ein abgehaktes Training, auf das eingedampft, was die Runde davon braucht. */
data class RotationEntry(
    val dayId: Int,
    val date: LocalDate,
    /**
     * Zeitstempel des Eintrags – gebraucht wird er nur für den Rundenschnitt (siehe
     * [completedDaysInRotation]). Er steht neben dem Datum und nicht an dessen Stelle, weil aus
     * einem Zeitstempel ein Datum zu machen der teuerste Schritt der ganzen Rechnung ist und
     * genau einmal passieren soll.
     */
    val completedAt: Long = NO_ROTATION_CUT
)

/**
 * Kein Rundenschnitt: Es zählt der ganze Verlauf.
 *
 * Zugleich der Vorgabewert für [RotationEntry.completedAt] – ein Eintrag ohne Zeitstempel liegt
 * damit nie vor einem Schnitt und zählt immer mit.
 */
const val NO_ROTATION_CUT = 0L

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
 * [startAfter] ist der von Hand gezogene Rundenschnitt: Alles, was zu diesem Zeitpunkt schon im
 * Verlauf stand, gehört zur vorigen Runde und zählt nicht mehr mit. Ohne ihn müsste man auf den
 * nächsten Kalendertag warten, um die Haken loszuwerden – siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.startNextRotation].
 *
 * Diese Menge sagt, was *angezeigt* wird, und ist bewusst keine Grundlage dafür, ob ein Tippen
 * auf den Haken einträgt oder zurücknimmt – siehe
 * [de.beispiel.meintraining.data.repository.TrainingRepository.toggleWorkout].
 */
fun completedDaysInRotation(
    entriesOldestFirst: List<RotationEntry>,
    dayCount: Int,
    today: LocalDate,
    startAfter: Long = NO_ROTATION_CUT
): Set<Int> {
    // Ohne Tage gibt es keine Runde; ohne diese Abfrage gälte jede Runde sofort als voll.
    if (dayCount <= 0) return emptySet()

    val done = mutableSetOf<Int>()
    var isFull = false
    entriesOldestFirst.forEach { entry ->
        // Vor dem Schnitt: gehört zur vorigen Runde. Die Abfrage auf den Schnitt selbst steht
        // mit davor, damit Einträge ohne Zeitstempel nicht reihenweise wegfallen.
        if (startAfter > NO_ROTATION_CUT && entry.completedAt <= startAfter) return@forEach

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
