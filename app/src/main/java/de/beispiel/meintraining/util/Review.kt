package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Der Zeitraum eines Rückblicks: ein Kalendermonat oder ein Kalenderjahr. */
sealed interface ReviewPeriod {
    /** Erster Tag. */
    val start: LocalDate

    /** Letzter Tag, eingeschlossen. */
    val end: LocalDate

    /** Der Zeitraum davor – derselben Art. */
    fun previous(): ReviewPeriod

    /** Der Zeitraum danach – derselben Art. */
    fun next(): ReviewPeriod

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    data class Month(val month: YearMonth) : ReviewPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.atEndOfMonth()
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : ReviewPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }
}

/**
 * Wechselt zwischen Monat und Jahr, ohne den Ort zu verlieren: Aus einem Monat wird sein Jahr, aus
 * einem Jahr dessen letzter Monat bis heute – im laufenden Jahr also der laufende Monat.
 */
fun ReviewPeriod.toggled(today: LocalDate): ReviewPeriod = when (this) {
    is ReviewPeriod.Month -> ReviewPeriod.Year(month.year)
    is ReviewPeriod.Year -> ReviewPeriod.Month(
        if (year == today.year) YearMonth.from(today) else YearMonth.of(year, 12)
    )
}

/**
 * Der Rückblick auf einen Zeitraum – siehe [review]. Was der Zeitraum nicht hergibt, ist 0,
 * `null` oder leer; die Seite lässt es dann weg.
 */
data class Review(
    val period: ReviewPeriod,
    val sessions: Int,
    /** Summe der bekannten Trainingsdauern in Minuten; `null`, wenn keine bekannt ist. */
    val trainingMinutes: Int?,
    /** Der Gewichtszuwachs im Zeitraum, über alle Kraftübungen – siehe [review]. */
    val gainKg: Double,
    /** Die Übung mit dem größten Zuwachs im Zeitraum; `null` ohne einen. */
    val topGain: ExerciseGain?,
    val cardioMinutes: Double,
    val cardioKm: Double,
    val cardioSessions: Int,
    /** Der Wochentag mit den meisten Trainings; bei Gleichstand der frühere in der Woche. */
    val topWeekday: DayOfWeek?,
    val topWeekdayCount: Int,
    /** Die längste Ziel-Serie im Zeitraum, in Wochen. */
    val bestStreak: Int,
    /** Die im Zeitraum erreichten Meilensteine, der älteste zuerst. */
    val milestones: List<ReachedMilestone>,
    /** Der Kalender des Zeitraums wie in der Statistik, eine Spalte je Woche – bis heute. */
    val weeks: List<HeatmapWeek>
) {
    val isEmpty: Boolean get() = sessions == 0 && cardioSessions == 0 && milestones.isEmpty()
}

/**
 * Rechnet den Rückblick auf [period] zusammen.
 *
 * - Trainings und ihre Dauer: die im Zeitraum abgehakten; die Dauer nur, wo sie bekannt ist (T2).
 * - Gewichtszuwachs: je Kraftübung vom Stand zu Beginn des Zeitraums – dem letzten Eintrag davor,
 *   sonst dem ersten darin – bis zum letzten Eintrag im Zeitraum, wie in der Statistik bei Pfeil
 *   nach unten das Gesunkene. Eine Übung im Minus zählt nicht gegen die anderen; sie fehlt.
 * - Cardio: Minuten, Kilometer und Einheiten im Zeitraum.
 * - Beste Serie: die längste Folge von Wochen mit erreichtem [weeklyGoal] unter den Wochen, die in
 *   den Zeitraum hineinreichen. Gemessen wird wie bei [currentWeeklyStreak] am aktuellen Ziel und
 *   an der ganzen Woche, auch an ihren Tagen vor oder nach dem Zeitraum.
 * - Meilensteine: die mit einem Datum im Zeitraum (aus [milestones]).
 */
fun review(
    period: ReviewPeriod,
    sessions: List<WorkoutSession>,
    weightLogsOldestFirst: List<WeightLog>,
    cardioLogs: List<CardioLog>,
    definitions: List<ExerciseDefinition>,
    weeklyGoal: Int,
    reached: List<ReachedMilestone>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): Review {
    val allDates = sessions.map { it.completedAt.toLocalDate(zone) }
    val inPeriod = sessions.indices.filter { allDates[it] in period }
    val dates = inPeriod.map { allDates[it] }

    val minutes = inPeriod.mapNotNull { durationMinutes(sessions[it].startedAt, sessions[it].completedAt) }

    val weekdays = weekdayDistribution(dates)
    val topWeekdayCount = weekdays.maxOrNull() ?: 0

    val cardio = cardioLogs.filter { it.performedAt.toLocalDate(zone) in period }

    val gains = periodGains(period, weightLogsOldestFirst, definitions, zone)

    return Review(
        period = period,
        sessions = dates.size,
        trainingMinutes = minutes.takeIf { it.isNotEmpty() }?.sum(),
        gainKg = gains.sumOf { it.gainKg },
        topGain = gains.maxByOrNull { it.gainKg },
        cardioMinutes = cardio.sumOf { it.durationMin ?: 0.0 },
        cardioKm = cardio.sumOf { it.distanceKm ?: 0.0 },
        cardioSessions = cardio.size,
        topWeekday = if (topWeekdayCount > 0) DayOfWeek.entries[weekdays.indexOf(topWeekdayCount)] else null,
        topWeekdayCount = topWeekdayCount,
        bestStreak = bestStreakIn(period, allDates, weeklyGoal),
        milestones = reached.filter { it.date in period }.sortedBy { it.date },
        weeks = periodWeeks(period, dates, today)
    )
}

/** Die Zuwächse der Kraftübungen im Zeitraum – siehe [review]. */
private fun periodGains(
    period: ReviewPeriod,
    logsOldestFirst: List<WeightLog>,
    definitions: List<ExerciseDefinition>,
    zone: ZoneId
): List<ExerciseGain> {
    val cardio = definitions.filter { it.kind == ExerciseKind.CARDIO }.mapTo(HashSet()) { it.name }
    val decreasing = definitions.filter { it.progressionDown }.mapTo(HashSet()) { it.name }
    return logsOldestFirst.filter { it.exerciseName !in cardio }
        .groupBy { it.exerciseName }
        .mapNotNull { (name, logs) ->
            val dated = logs.map { it to it.recordedAt.toLocalDate(zone) }
            val within = dated.filter { (_, date) -> date in period }
            if (within.isEmpty()) return@mapNotNull null
            val before = dated.lastOrNull { (_, date) -> date.isBefore(period.start) }
            ExerciseGain(
                name = name,
                fromKg = (before ?: within.first()).first.weightKg,
                toKg = within.last().first.weightKg,
                isDecreasing = name in decreasing
            )
        }
        .filter { it.gainKg > 0.0 }
}

/** Die längste Ziel-Serie unter den Wochen, die in den Zeitraum hineinreichen. */
private fun bestStreakIn(period: ReviewPeriod, dates: List<LocalDate>, goal: Int): Int {
    val first = period.start.weekStart()
    val weeks = weeksReachingGoal(dates, goal)
        .filter { !it.isBefore(first) && !it.isAfter(period.end) }
        .sorted()
    var best = 0
    var current = 0
    var previous: LocalDate? = null
    weeks.forEach { week ->
        current = if (previous?.let { ChronoUnit.WEEKS.between(it, week) } == 1L) current + 1 else 1
        best = maxOf(best, current)
        previous = week
    }
    return best
}

/**
 * Der Kalender des Zeitraums: eine Spalte je Woche, Montag oben, von der Woche des ersten Tags bis
 * zur Woche des letzten – höchstens bis heute. Tage außerhalb des Zeitraums und nach heute sind
 * `null`. Im Jahr steht über der Woche, in der ein Monat beginnt, sein Name.
 */
private fun periodWeeks(period: ReviewPeriod, dates: List<LocalDate>, today: LocalDate): List<HeatmapWeek> {
    val last = minOf(period.end, today)
    if (last.isBefore(period.start)) return emptyList()
    val counts = dates.groupingBy { it }.eachCount()
    val weeks = mutableListOf<HeatmapWeek>()
    var monday = period.start.weekStart()
    while (!monday.isAfter(last)) {
        val days = (0L until DayOfWeek.entries.size).map { offset ->
            val date = monday.plusDays(offset)
            if (date !in period || date.isAfter(today)) null else HeatmapDay(date, counts[date] ?: 0)
        }
        val monthStart = days.firstOrNull { it != null && it.date.dayOfMonth == 1 }
        weeks += HeatmapWeek(
            days = days,
            monthLabel = monthStart?.date?.month?.takeIf { period is ReviewPeriod.Year }
        )
        monday = monday.plusWeeks(1)
    }
    return weeks
}
