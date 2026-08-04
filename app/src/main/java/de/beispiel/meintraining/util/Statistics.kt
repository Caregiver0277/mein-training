package de.beispiel.meintraining.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

private const val SECONDS_PER_DAY = 24 * 60 * 60

/** Ab dieser Ruhezeit gilt das Gewicht einer Übung als festgefahren. */
const val STAGNATION_DAYS = 28L

/** Gewichtsentwicklung einer Übung vom ersten bis zum aktuellen Eintrag. */
data class ExerciseGain(val name: String, val fromKg: Double, val toKg: Double) {
    val gainKg: Double get() = toKg - fromKg
    val gainPercent: Double get() = if (fromKg > 0.0) gainKg / fromKg * 100.0 else 0.0
}

/** Übung, deren Gewicht seit [sinceDays] Tagen unverändert ist. */
data class StagnatingExercise(val name: String, val weightKg: Double, val sinceDays: Long)

/**
 * Trainings pro Woche über den gesamten bisherigen Zeitraum.
 *
 * Gerechnet wird über mindestens eine Woche: Sonst ergäbe ein einziges Training am ersten Tag
 * hochgerechnete „7 pro Woche“. Mit wachsender Datenlage nähert sich der Wert dem echten an.
 */
fun sessionsPerWeek(dates: List<LocalDate>, today: LocalDate): Double {
    if (dates.isEmpty()) return 0.0
    val span = ChronoUnit.DAYS.between(dates.min(), today) + 1
    return dates.size * 7.0 / span.coerceAtLeast(7)
}

/**
 * Wochen in Folge mit mindestens einem Training, rückwärts gezählt.
 *
 * Die laufende Woche zählt nicht gegen die Serie, solange sie noch offen ist – sonst stünde
 * jeden Montagmorgen eine 0 da.
 */
fun currentWeeklyStreak(dates: List<LocalDate>, today: LocalDate): Int {
    if (dates.isEmpty()) return 0
    val weeks = dates.map { it.weekStart() }.toSet()
    var cursor = today.weekStart()
    if (cursor !in weeks) cursor = cursor.minusWeeks(1)
    var streak = 0
    while (cursor in weeks) {
        streak++
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

/** Die längste jemals erreichte Serie zusammenhängender Trainingswochen. */
fun longestWeeklyStreak(dates: List<LocalDate>): Int {
    val weeks = dates.map { it.weekStart() }.distinct().sorted()
    if (weeks.isEmpty()) return 0
    var best = 1
    var current = 1
    weeks.zipWithNext { previous, next ->
        current = if (ChronoUnit.WEEKS.between(previous, next) == 1L) current + 1 else 1
        best = maxOf(best, current)
    }
    return best
}

/** Anzahl Trainings je Wochentag, beginnend mit Montag. */
fun weekdayDistribution(dates: List<LocalDate>): List<Int> {
    val counts = IntArray(DayOfWeek.entries.size)
    dates.forEach { counts[it.dayOfWeek.ordinal]++ }
    return counts.toList()
}

/**
 * Die typische Trainingszeit.
 *
 * Gemittelt wird über den Kreis der Uhrzeiten, nicht über die Sekunden: Sonst ergäben
 * 23:50 und 00:10 die Mittagszeit statt Mitternacht.
 */
fun typicalTimeOfDay(times: List<LocalTime>): LocalTime? {
    if (times.isEmpty()) return null
    var x = 0.0
    var y = 0.0
    times.forEach { time ->
        val angle = 2 * PI * time.toSecondOfDay() / SECONDS_PER_DAY
        x += cos(angle)
        y += sin(angle)
    }
    // Verteilen sich die Zeiten gleichmäßig über den Tag, gibt es keine typische Zeit.
    if (abs(x) < 1e-9 && abs(y) < 1e-9) return null
    var angle = atan2(y, x)
    if (angle < 0) angle += 2 * PI
    val second = (angle / (2 * PI) * SECONDS_PER_DAY).roundToLong() % SECONDS_PER_DAY
    return LocalTime.ofSecondOfDay(second)
}

/**
 * Gewichtsentwicklung je Übung, die größten Zuwächse zuerst.
 * [entries] sind Paare aus Übungsname und Gewicht in zeitlicher Reihenfolge.
 */
fun exerciseGains(entries: List<Pair<String, Double>>): List<ExerciseGain> =
    entries.groupBy({ it.first }, { it.second })
        .mapNotNull { (name, weights) ->
            if (weights.size < 2) return@mapNotNull null
            ExerciseGain(name = name, fromKg = weights.first(), toKg = weights.last())
        }
        .filter { it.gainKg > 0.0 }
        .sortedByDescending { it.gainKg }

/**
 * Übungen, deren Gewicht seit mindestens [minDays] Tagen steht.
 * [lastChanged] hält je Übung den Zeitpunkt der letzten Gewichtsänderung.
 */
fun stagnatingExercises(
    lastChanged: Map<String, LocalDate>,
    currentWeights: Map<String, Double>,
    today: LocalDate,
    minDays: Long = STAGNATION_DAYS
): List<StagnatingExercise> = lastChanged.mapNotNull { (name, changedAt) ->
    val weight = currentWeights[name] ?: return@mapNotNull null
    val days = ChronoUnit.DAYS.between(changedAt, today)
    if (days < minDays) null else StagnatingExercise(name, weight, days)
}.sortedByDescending { it.sinceDays }

private fun LocalDate.weekStart(): LocalDate = with(DayOfWeek.MONDAY)
