package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
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

/**
 * Ab so vielen Trainings ohne Gewichtsänderung gilt eine Übung als festgefahren.
 *
 * Gezählt werden Trainings und keine Kalendertage: Nach einer Pause von ein paar Wochen ist
 * nichts festgefahren, es wurde nur nicht trainiert.
 */
const val STAGNATION_SESSIONS = 6

/**
 * Gewichtsentwicklung einer Übung vom ersten bis zum aktuellen Eintrag.
 *
 * [isDecreasing] heißt: Der Pfeil der Übung zeigt nach unten, die Last ist eine Unterstützung
 * – etwa an der Klimmzugmaschine –, und Fortschritt heißt, dass sie *sinkt*. Der Zuwachs ist dann,
 * was sie gesunken ist: Von 40 auf 25 kg Unterstützung sind 15 kg mehr, die man selbst bewegt.
 */
data class ExerciseGain(
    val name: String,
    val fromKg: Double,
    val toKg: Double,
    val isDecreasing: Boolean = false
) {
    val gainKg: Double get() = if (isDecreasing) fromKg - toKg else toKg - fromKg
    val gainPercent: Double get() = if (fromKg > 0.0) gainKg / fromKg * 100.0 else 0.0
}

/**
 * Übung, deren Gewicht seit [sinceSessions] Trainings unverändert ist – an den Tagen, an denen
 * sie im Plan steht. [sinceDays] ist dieselbe Spanne in Kalendertagen, zur Einordnung.
 */
data class StagnatingExercise(
    val name: String,
    val weightKg: Double,
    val sinceSessions: Int,
    val sinceDays: Long
)

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
 * Trainings in den letzten [days] Tagen, heute eingeschlossen.
 *
 * Einträge mit einem Datum nach [today] zählen nicht – sie kommen durch einen
 * Zeitzonenwechsel oder eine eingelesene Sicherung zustande und liegen in keiner Spanne,
 * die von heute aus rückwärts reicht.
 */
fun sessionsInLastDays(dates: List<LocalDate>, today: LocalDate, days: Int): Int =
    dates.count { ChronoUnit.DAYS.between(it, today) in 0 until days }

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
 *
 * [decreasing] sind die Übungen, deren Pfeil nach unten zeigt (siehe [ExerciseGain.isDecreasing]).
 * Ohne diese Angabe zählte dort ausgerechnet ein Rückschritt – mehr Unterstützung – als Zuwachs,
 * und der eigentliche Fortschritt fiele weg.
 */
fun exerciseGains(
    entries: List<Pair<String, Double>>,
    decreasing: Set<String> = emptySet()
): List<ExerciseGain> =
    entries.groupBy({ it.first }, { it.second })
        .mapNotNull { (name, weights) ->
            if (weights.size < 2) return@mapNotNull null
            ExerciseGain(
                name = name,
                fromKg = weights.first(),
                toKg = weights.last(),
                isDecreasing = name in decreasing
            )
        }
        .filter { it.gainKg > 0.0 }
        .sortedByDescending { it.gainKg }

/**
 * Das eingetragene Gewicht jeder Kraftübung unter [names] – Grundlage für „Festgefahren“ und
 * „Schwerste Übung“.
 *
 * Cardio-Übungen fehlen, auch wenn sie aus ihrer Zeit als Kraftübung noch ein Gewicht tragen:
 * Sie werden nicht über ein Gewicht gesteigert, stünden sonst bald für immer als festgefahren da
 * – und ihre Auswertung kommt aus den eingetragenen Einheiten.
 */
fun currentStrengthWeights(
    definitions: List<ExerciseDefinition>,
    names: Set<String>
): Map<String, Double> = definitions
    .filter { it.name in names && it.kind == ExerciseKind.STRENGTH }
    .mapNotNull { definition -> definition.weightKg?.let { definition.name to it } }
    .toMap()

/**
 * Übungen, deren Gewicht seit mindestens [minSessions] Trainings steht.
 *
 * [lastChanged] hält je Übung den Zeitpunkt der letzten Gewichtsänderung, [plannedDays] die
 * Trainingstage, an denen sie im Plan steht. [sessions] sind die abgehakten Trainings als Paare
 * aus Trainingstag und Zeitpunkt. Gezählt wird, wie oft seit der letzten Änderung ein Tag der
 * Übung abgehakt wurde – nur dann wurde sie auch trainiert.
 *
 * Übungen ohne Gewicht oder mit 0 kg fehlen: Bei ihnen gibt es nichts zu steigern, sie stünden
 * sonst für immer in der Liste.
 */
fun stagnatingExercises(
    lastChanged: Map<String, Long>,
    currentWeights: Map<String, Double>,
    plannedDays: Map<String, Set<Int>>,
    sessions: List<Pair<Int, Long>>,
    today: LocalDate,
    minSessions: Int = STAGNATION_SESSIONS
): List<StagnatingExercise> = lastChanged.mapNotNull { (name, changedAt) ->
    val weight = currentWeights[name]?.takeIf { it > 0.0 } ?: return@mapNotNull null
    val days = plannedDays[name].orEmpty()
    val count = sessions.count { (dayId, completedAt) -> dayId in days && completedAt > changedAt }
    if (count < minSessions) return@mapNotNull null
    StagnatingExercise(
        name = name,
        weightKg = weight,
        sinceSessions = count,
        sinceDays = ChronoUnit.DAYS.between(changedAt.toLocalDate(), today)
    )
}.sortedWith(compareByDescending<StagnatingExercise> { it.sinceSessions }.thenByDescending { it.sinceDays })

private fun LocalDate.weekStart(): LocalDate = with(DayOfWeek.MONDAY)
