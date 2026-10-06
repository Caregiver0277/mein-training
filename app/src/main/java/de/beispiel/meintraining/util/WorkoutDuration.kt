package de.beispiel.meintraining.util

import kotlin.math.roundToInt

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Kürzeste und längste Dauer, die als echtes Training durchgeht.
 *
 * Darunter war es eher eine Pausenuhr, die aus Versehen lief; darüber hat jemand den Haken
 * vergessen und erst am Abend nachgeholt. Beides zeigte eine Dauer, die es nie gab – dann lieber
 * keine.
 */
const val MIN_WORKOUT_MILLIS = 10 * MILLIS_PER_MINUTE
const val MAX_WORKOUT_MILLIS = 4 * 60 * MILLIS_PER_MINUTE

/**
 * So lange ohne Aktivität, und ein Training gilt als beendet: Eine neue Aktivität beginnt dann
 * ein neues.
 */
const val WORKOUT_IDLE_MILLIS = 30 * MILLIS_PER_MINUTE

/**
 * Der Merker für ein laufendes Training: wann es begann und wann zuletzt etwas geschah.
 *
 * Er entsteht mit der ersten Aktivität nach dem letzten Abhaken – dem Start einer Pausenuhr,
 * später auch einem protokollierten Satz oder einem Cardio-Eintrag – und wird beim Abhaken
 * verbraucht: Aus [startedAt] wird der Beginn des Trainings (siehe [plausibleStart]).
 */
data class WorkoutMarker(
    /** Der Trainingstag, an dem zuletzt etwas geschah. */
    val dayId: Int,
    /** Die erste Aktivität – der Beginn des Trainings. */
    val startedAt: Long,
    /** Die jüngste Aktivität. */
    val lastActivityAt: Long,
    /**
     * Bis wann das Training sicher noch läuft, auch ohne neue Aktivität – etwa das Ende einer
     * gestarteten Pausenuhr. Nie vor [lastActivityAt].
     */
    val activeUntil: Long = lastActivityAt
) {
    /** Ab hier zählt die Ruhe: nach der letzten Aktivität und nach allem, was noch lief. */
    val quietSince: Long get() = maxOf(lastActivityAt, activeUntil)
}

/**
 * Der Merker nach einer Aktivität zum Zeitpunkt [at] an Trainingstag [dayId]; [activeUntil] ist
 * das Ende dessen, was sie angestoßen hat – bei einer Pausenuhr ihr Ablauf.
 *
 * Ohne Merker beginnt hier ein Training. Ebenso, wenn der alte schon [WORKOUT_IDLE_MILLIS] lang
 * ruht: Das Training, zu dem er gehörte, ist vorbei, auch wenn niemand den Haken gedrückt hat.
 * Sonst läuft das Training weiter, und der Tag ist der der jüngsten Aktivität.
 */
fun WorkoutMarker?.withActivity(dayId: Int, at: Long, activeUntil: Long = at): WorkoutMarker {
    val until = maxOf(at, activeUntil)
    if (this == null || at - quietSince >= WORKOUT_IDLE_MILLIS) {
        return WorkoutMarker(dayId = dayId, startedAt = at, lastActivityAt = at, activeUntil = until)
    }
    return WorkoutMarker(
        dayId = dayId,
        startedAt = minOf(startedAt, at),
        lastActivityAt = maxOf(lastActivityAt, at),
        activeUntil = maxOf(this.activeUntil, until)
    )
}

/**
 * Der Merker, nachdem ein Abhaken zurückgenommen wurde: der verbrauchte [consumed] kommt wieder –
 * sonst wäre die Dauer nach einem Fehltipp weg.
 *
 * Gab es seither schon neue Aktivität ([current]), gehört sie zum selben Training: Beginn bleibt
 * der frühere, alles andere kommt vom jüngeren Merker.
 */
fun restoredMarker(consumed: WorkoutMarker, current: WorkoutMarker?): WorkoutMarker {
    if (current == null) return consumed
    return WorkoutMarker(
        dayId = current.dayId,
        startedAt = minOf(consumed.startedAt, current.startedAt),
        lastActivityAt = maxOf(consumed.lastActivityAt, current.lastActivityAt),
        activeUntil = maxOf(consumed.activeUntil, current.activeUntil)
    )
}

/**
 * Der Beginn, der mit einem Abhaken um [completedAt] gespeichert wird – oder `null`, wenn die
 * Dauer nicht plausibel ist (siehe [MIN_WORKOUT_MILLIS], [MAX_WORKOUT_MILLIS]) oder es keinen
 * Beginn gibt.
 */
fun plausibleStart(startedAt: Long?, completedAt: Long): Long? {
    if (startedAt == null) return null
    val duration = completedAt - startedAt
    return startedAt.takeIf { duration in MIN_WORKOUT_MILLIS..MAX_WORKOUT_MILLIS }
}

/** Die Dauer in ganzen Minuten, gerundet; `null` ohne bekannten Beginn. */
fun durationMinutes(startedAt: Long?, completedAt: Long): Int? {
    if (startedAt == null || startedAt > completedAt) return null
    return ((completedAt - startedAt).toDouble() / MILLIS_PER_MINUTE).roundToInt()
}

/** Ein Training mit seinen Zeiten – so, wie die Dauer-Statistik es braucht. */
data class SessionTimes(val dayId: Int, val startedAt: Long?, val completedAt: Long)

/**
 * Die durchschnittliche Dauer, gesamt und je Trainingstag.
 *
 * [perDay] ist nach Tag sortiert und enthält nur Tage mit mindestens einer bekannten Dauer.
 */
data class DurationSummary(val averageMinutes: Int, val perDay: List<Pair<Int, Int>>)

/**
 * Ø Dauer aus allen Trainings mit bekanntem Beginn; `null`, solange es keines gibt. Trainings ohne
 * Dauer zählen nicht mit – sie hätten sonst 0 Minuten und zögen den Schnitt nach unten.
 */
fun durationSummary(sessions: List<SessionTimes>): DurationSummary? {
    val known = sessions.mapNotNull { session ->
        durationMinutes(session.startedAt, session.completedAt)?.let { session.dayId to it }
    }
    if (known.isEmpty()) return null
    return DurationSummary(
        averageMinutes = known.map { it.second }.average().roundToInt(),
        perDay = known.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (dayId, minutes) -> dayId to minutes.average().roundToInt() }
    )
}
