package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.data.model.WeightLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ab so vielen Steigerungen nennt der Fortschritt ein Tempo („im Schnitt alle 11 Tage +2,5 kg“).
 * Nach einer einzigen wäre das kein Schnitt, sondern bloß die Zeit bis zu ihr.
 */
const val MIN_TEMPO_INCREASES = 2

/**
 * Kleiner als jeder Schritt, den es gibt (0,625 kg, 0,1 km/h, 1 s): Darunter ist ein Unterschied
 * nur ein Rundungsrest der Kommazahlen und keine Änderung.
 */
private const val CHANGE_TOLERANCE = 1e-6

/**
 * Der Fortschritt einer Kraftübung aus ihrem Gewichtsverlauf – eine Zeile der Liste „Fortschritt
 * je Übung“ und der Kopf ihrer Detailseite.
 *
 * Wie bei [ExerciseGain] heißt [isDecreasing]: Der Pfeil zeigt nach unten, die Last ist eine
 * Unterstützung, und Fortschritt ist, dass sie sinkt. Eine *Steigerung* ist dann eine Senkung –
 * gezählt wird immer der Schritt in Richtung Fortschritt, der andere ist eine Korrektur.
 */
data class StrengthProgress(
    val name: String,
    /** Der erste eingetragene Stand. */
    val fromKg: Double,
    /** Der jüngste Stand – das Gewicht, mit dem gerade trainiert wird. */
    val toKg: Double,
    val isDecreasing: Boolean,
    /** Wie oft das Gewicht in Richtung Fortschritt verändert wurde. */
    val increases: Int,
    /** Um wie viel zusammen – nur die Schritte in Richtung Fortschritt, als positive Zahl. */
    val increasedKg: Double,
    /** Zeitpunkt des ersten Eintrags. */
    val firstAt: Long,
    /** Zeitpunkt der jüngsten Steigerung; `null` ohne eine. */
    val lastIncreaseAt: Long?
) {
    val gainKg: Double get() = if (isDecreasing) fromKg - toKg else toKg - fromKg
    val gainPercent: Double? get() = if (fromKg > 0.0) gainKg / fromKg * 100.0 else null

    /**
     * Das Tempo: im Schnitt so viele Tage je Steigerung und so viel je Steigerung; `null` unter
     * [MIN_TEMPO_INCREASES].
     *
     * Gemessen vom ersten Eintrag bis zur jüngsten Steigerung – nicht bis heute: Wie lange es seit
     * ihr steht, sagt [daysSinceLastIncrease]. Bis heute gerechnet zöge eine Pause das Tempo
     * herunter, und die Zeile sagte zweimal dasselbe.
     */
    fun tempo(zone: ZoneId = ZoneId.systemDefault()): ProgressTempo? {
        val last = lastIncreaseAt ?: return null
        if (increases < MIN_TEMPO_INCREASES) return null
        val days = ChronoUnit.DAYS.between(firstAt.toLocalDate(zone), last.toLocalDate(zone))
        return ProgressTempo(
            daysPerIncrease = days.toDouble() / increases,
            amountPerIncrease = increasedKg / increases
        )
    }

    /** Kalendertage seit der jüngsten Steigerung; `null` ohne eine. Nie unter 0. */
    fun daysSinceLastIncrease(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long? =
        lastIncreaseAt?.let { ChronoUnit.DAYS.between(it.toLocalDate(zone), today).coerceAtLeast(0) }
}

/** Siehe [StrengthProgress.tempo]. */
data class ProgressTempo(val daysPerIncrease: Double, val amountPerIncrease: Double)

/**
 * Fasst den Gewichtsverlauf *einer* Übung zu ihrem Fortschritt zusammen; `null` ohne Eintrag.
 *
 * [logsOldestFirst] müssen zur Übung [name] gehören, älteste zuerst – die Reihenfolge, in der sie
 * aus der Datenbank kommen. Ein einziger Eintrag ergibt Start gleich aktuell und keine
 * Steigerung: Die Übung steht trotzdem in der Liste, sie hat bloß noch keinen Fortschritt.
 */
fun strengthProgress(
    name: String,
    logsOldestFirst: List<WeightLog>,
    isDecreasing: Boolean
): StrengthProgress? {
    val first = logsOldestFirst.firstOrNull() ?: return null
    val direction = if (isDecreasing) -1.0 else 1.0
    var increases = 0
    var increasedKg = 0.0
    var lastIncreaseAt: Long? = null
    logsOldestFirst.zipWithNext { previous, next ->
        val step = (next.weightKg - previous.weightKg) * direction
        if (step > CHANGE_TOLERANCE) {
            increases++
            increasedKg += step
            lastIncreaseAt = next.recordedAt
        }
    }
    return StrengthProgress(
        name = name,
        fromKg = first.weightKg,
        toKg = logsOldestFirst.last().weightKg,
        isDecreasing = isDecreasing,
        increases = increases,
        increasedKg = increasedKg,
        firstAt = first.recordedAt,
        lastIncreaseAt = lastIncreaseAt
    )
}

/**
 * Der Fortschritt einer Cardio-Übung: wie sich der Wert [value] von der ersten bis zur jüngsten
 * Einheit verändert hat, in der er eingetragen ist.
 *
 * [unit] ist nur beim Tempo gesetzt – km/h oder Stufe, je nachdem, welche Einheiten zählen (siehe
 * [cardioProgress]). [isDecreasing]: Der Pfeil steuert genau diesen Wert nach unten, weniger ist
 * dann mehr – wie in der %-Ansicht des Trackings (siehe `decreasingCardioNames`).
 */
data class CardioProgress(
    val name: String,
    val variation: String?,
    val value: CardioValue,
    val unit: IntensityUnit?,
    val isDecreasing: Boolean,
    val from: Double,
    val to: Double,
    /** Einheiten, in denen [value] eingetragen ist. */
    val entries: Int,
    val firstAt: Long,
    val lastAt: Long
) {
    val gain: Double get() = if (isDecreasing) from - to else to - from
    val gainPercent: Double? get() = if (from > 0.0) gain / from * 100.0 else null
}

/**
 * Der Wert, an dem der Fortschritt einer Cardio-Übung gemessen wird: der, den ihr Pfeil
 * verschiebt. Ohne Pfeil der erste, der in ihren Einheiten vorkommt – in der Reihenfolge der
 * Zeile: Dauer, Distanz, Tempo, Steigung. `null`, wenn keine Einheit einen Wert hat.
 */
fun progressValueOf(targets: CardioTargets, logs: List<CardioLog>): CardioValue? =
    targets.arrowValue ?: CardioValue.entries.firstOrNull { value ->
        logs.any { it.amountOf(value) != null }
    }

/**
 * Fasst die Einheiten *einer* Cardio-Übung (Name samt Variation, älteste zuerst) zu ihrem
 * Fortschritt zusammen; `null`, wenn keine Einheit den Wert hat.
 *
 * Beim Tempo zählen nur die Einheiten in der Einheit, die die Übung gerade hat: km/h und Stufe
 * lassen sich nicht verrechnen, und gemeint ist das Gerät, auf dem heute trainiert wird. Ein
 * Eintrag ohne Einheit gilt als km/h, wie überall.
 */
fun cardioProgress(
    name: String,
    variation: String?,
    logsOldestFirst: List<CardioLog>,
    targets: CardioTargets
): CardioProgress? {
    val value = progressValueOf(targets, logsOldestFirst) ?: return null
    val unit = targets.intensityUnit.takeIf { value == CardioValue.INTENSITY }
    val withValue = logsOldestFirst.mapNotNull { log ->
        if (unit != null && (log.intensityUnit ?: IntensityUnit.KMH) != unit) return@mapNotNull null
        log.amountOf(value)?.let { log to it }
    }
    if (withValue.isEmpty()) return null
    val (firstLog, from) = withValue.first()
    val (lastLog, to) = withValue.last()
    return CardioProgress(
        name = name,
        variation = variation,
        value = value,
        unit = unit,
        isDecreasing = targets.arrowDown && targets.arrowValue == value,
        from = from,
        to = to,
        entries = withValue.size,
        firstAt = firstLog.performedAt,
        lastAt = lastLog.performedAt
    )
}

/** Der Betrag des Werts [value] in dieser Einheit; `null`, wenn er nicht eingetragen ist. */
fun CardioLog.amountOf(value: CardioValue): Double? = when (value) {
    CardioValue.DURATION -> durationMin
    CardioValue.DISTANCE -> distanceKm
    CardioValue.INTENSITY -> intensity
    CardioValue.INCLINE -> inclinePercent
}
