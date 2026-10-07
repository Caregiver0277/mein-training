package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
 * Das Wochenziel: so viele Trainings pro Woche sollen es sein. Drei sind der übliche Rahmen für
 * einen Plan über mehrere Tage; mehr als eins am Tag sieht die Einstellung nicht vor.
 */
const val DEFAULT_WEEKLY_GOAL = 3
const val MIN_WEEKLY_GOAL = 1
const val MAX_WEEKLY_GOAL = 7

/** So viele Wochen zeigt das Wochenziel als Balken. */
const val GOAL_WEEKS = 12

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
 * Ziel-Serie: Wochen in Folge, in denen das Wochenziel von [goal] Trainings erreicht wurde,
 * rückwärts gezählt.
 *
 * Die laufende Woche zählt nicht gegen die Serie, solange sie noch offen ist – sonst stünde
 * jeden Montagmorgen eine 0 da. Hat sie das Ziel schon erreicht, zählt sie mit.
 *
 * Gemessen wird der ganze Verlauf am *aktuellen* Ziel: Wer es von 3 auf 4 anhebt, sieht seine
 * Serie so, als hätte immer 4 gegolten. Ein Verlauf der Ziele wäre genauer, hieße aber für eine
 * Zahl auf der Statistikseite, jede Änderung der Einstellung mitzuschreiben.
 */
fun currentWeeklyStreak(dates: List<LocalDate>, today: LocalDate, goal: Int): Int {
    val reached = weeksReachingGoal(dates, goal)
    var cursor = today.weekStart()
    if (cursor !in reached) cursor = cursor.minusWeeks(1)
    var streak = 0
    while (cursor in reached) {
        streak++
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

/** Die längste jemals erreichte Ziel-Serie – siehe [currentWeeklyStreak]. */
fun longestWeeklyStreak(dates: List<LocalDate>, goal: Int): Int {
    val weeks = weeksReachingGoal(dates, goal).sorted()
    if (weeks.isEmpty()) return 0
    var best = 1
    var current = 1
    weeks.zipWithNext { previous, next ->
        current = if (ChronoUnit.WEEKS.between(previous, next) == 1L) current + 1 else 1
        best = maxOf(best, current)
    }
    return best
}

/** Eine Woche und ihre Trainings – ein Balken im Wochenziel. */
data class WeekCount(val weekStart: LocalDate, val count: Int)

/**
 * Trainings je Woche für die letzten [weeks] Wochen, älteste zuerst; die letzte ist die laufende.
 * Wochen ohne Training stehen mit 0 darin – eine Lücke soll man sehen.
 */
fun weeklyCounts(dates: List<LocalDate>, today: LocalDate, weeks: Int = GOAL_WEEKS): List<WeekCount> {
    val counts = dates.groupingBy { it.weekStart() }.eachCount()
    val current = today.weekStart()
    return (weeks - 1 downTo 0).map { back ->
        val start = current.minusWeeks(back.toLong())
        WeekCount(start, counts[start] ?: 0)
    }
}

/** Minuten und Kilometer der Cardio-Einheiten einer Woche. */
data class CardioWeek(val weekStart: LocalDate, val minutes: Double, val km: Double)

/**
 * Die Cardio-Bilanz: Minuten und Kilometer je Woche für die letzten [weeks] Wochen (älteste
 * zuerst, die laufende zuletzt) und die Summen seit der ersten Einheit.
 */
data class CardioTotals(
    val weeks: List<CardioWeek>,
    val totalMinutes: Double,
    val totalKm: Double,
    /** Alle eingetragenen Einheiten. */
    val sessions: Int
)

/**
 * Rechnet die Cardio-Einheiten zur Bilanz zusammen; `null` ohne eine einzige.
 *
 * Gezählt wird jede eingetragene Einheit, auch die einer Übung, die inzwischen gelöscht, pausiert
 * oder wieder Kraft ist: Gelaufen ist gelaufen. Ein fehlender Wert zählt als 0 – eine Einheit nur
 * mit Dauer hat keine Kilometer beigetragen. Die Summen reichen über alles, auch über Einheiten,
 * die durch einen Zeitzonenwechsel nach heute datiert sind; die Wochen nur bis heute.
 */
fun cardioTotals(
    logs: List<CardioLog>,
    today: LocalDate,
    weeks: Int = GOAL_WEEKS,
    zone: ZoneId = ZoneId.systemDefault()
): CardioTotals? {
    if (logs.isEmpty()) return null
    val byWeek = logs.groupBy { it.performedAt.toLocalDate(zone).weekStart() }
    val current = today.weekStart()
    return CardioTotals(
        weeks = (weeks - 1 downTo 0).map { back ->
            val start = current.minusWeeks(back.toLong())
            val inWeek = byWeek[start].orEmpty()
            CardioWeek(
                weekStart = start,
                minutes = inWeek.sumOf { it.durationMin ?: 0.0 },
                km = inWeek.sumOf { it.distanceKm ?: 0.0 }
            )
        },
        totalMinutes = logs.sumOf { it.durationMin ?: 0.0 },
        totalKm = logs.sumOf { it.distanceKm ?: 0.0 },
        sessions = logs.size
    )
}

/** Die Wochen (als ihr Montag), in denen mindestens [goal] Trainings stehen. */
internal fun weeksReachingGoal(dates: List<LocalDate>, goal: Int): Set<LocalDate> =
    dates.groupingBy { it.weekStart() }.eachCount()
        .filterValues { it >= goal.coerceAtLeast(1) }
        .keys

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
 * sonst für immer in der Liste. Ebenso fehlen die Übungen in [stillProgressing] – solche mit
 * Satz-Protokoll, deren Wiederholungen beim aktuellen Gewicht noch steigen (siehe
 * [repsStillRising]): Sie stehen nicht, sie werden gerade über die Wiederholungen gesteigert.
 */
fun stagnatingExercises(
    lastChanged: Map<String, Long>,
    currentWeights: Map<String, Double>,
    plannedDays: Map<String, Set<Int>>,
    sessions: List<Pair<Int, Long>>,
    today: LocalDate,
    minSessions: Int = STAGNATION_SESSIONS,
    stillProgressing: Set<String> = emptySet()
): List<StagnatingExercise> = lastChanged.mapNotNull { (name, changedAt) ->
    if (name in stillProgressing) return@mapNotNull null
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

/** Der Montag der Woche, in der das Datum liegt. */
internal fun LocalDate.weekStart(): LocalDate = with(DayOfWeek.MONDAY)
