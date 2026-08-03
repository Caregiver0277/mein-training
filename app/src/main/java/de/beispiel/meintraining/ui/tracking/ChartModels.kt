package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.WeightLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Auswählbare Zeiträume der X-Achse. */
enum class TimeRange {
    TOTAL,
    YEAR_1,
    MONTHS_6,
    MONTHS_3,
    MONTH_1,

    /** Ein bestimmtes Kalenderjahr, siehe [TrackingUiState.manualYear]. */
    MANUAL_YEAR
}

/** Ein Messpunkt im Graphen – immer eine tatsächlich eingetragene Gewichtsänderung. */
data class ChartPoint(
    val timeMillis: Long,
    val weightKg: Double
)

/** Der Verlauf einer Übung. */
data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>
)

/** Eine Beschriftung der X-Achse. */
data class AxisTick(val timeMillis: Long, val label: String)

/** Zeitfenster des Graphen. */
data class TimeWindow(val startMillis: Long, val endMillis: Long)

/** Anteil des Zeitraums, der rechts über heute hinaus freigehalten wird. */
private const val FORWARD_BUFFER_SHARE = 0.12

/** Mindestbreite von „Gesamt“, damit ein einzelner Tag kein Strich wird. */
private const val TOTAL_MIN_SPAN_DAYS = 14L

private const val DAYS_PER_MONTH = 30L
private const val MONTHS_3_DAYS = 91L
private const val MONTHS_6_DAYS = 182L
private const val YEAR_DAYS = 365L

/** Ab dieser Spanne beschriftet die Achse Monate statt Tage. */
private const val MONTH_LABEL_THRESHOLD_DAYS = 45L

/** Ab dieser Spanne beschriftet die Achse Jahre statt Monate. */
private const val YEAR_LABEL_THRESHOLD_DAYS = 800L

private const val MAX_TICKS = 7

/**
 * Berechnet das darzustellende Zeitfenster.
 *
 * Alle Fenster reichen etwas über heute hinaus, damit der jüngste Punkt nicht am rechten Rand
 * klebt und die Linie sichtbar Platz zum Weiterwachsen hat.
 *
 * [TimeRange.TOTAL] beginnt beim ersten Eintrag statt bei einem festen Rückblick: So steht der
 * Trainingsbeginn links und der Verlauf wächst nach rechts. Bei nur einem Zeitpunkt wird auf
 * [TOTAL_MIN_SPAN_DAYS] aufgefüllt, sonst fiele der Graph auf eine Linie zusammen.
 */
fun timeWindowFor(
    range: TimeRange,
    manualYear: Int,
    logs: List<WeightLog>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): TimeWindow = when (range) {
    TimeRange.MONTH_1 -> pastWindow(now, DAYS_PER_MONTH)
    TimeRange.MONTHS_3 -> pastWindow(now, MONTHS_3_DAYS)
    TimeRange.MONTHS_6 -> pastWindow(now, MONTHS_6_DAYS)
    TimeRange.YEAR_1 -> pastWindow(now, YEAR_DAYS)
    TimeRange.MANUAL_YEAR -> {
        val start = LocalDate.of(manualYear, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(manualYear, 12, 31).atTime(23, 59).atZone(zone)
            .toInstant().toEpochMilli()
        TimeWindow(start, end)
    }
    TimeRange.TOTAL -> {
        val start = logs.minOfOrNull { it.recordedAt } ?: now
        val end = maxOf(now, start + TOTAL_MIN_SPAN_DAYS.days())
        TimeWindow(start, end + forwardBuffer(end - start))
    }
}

/** Fenster über die letzten [days] Tage, mit etwas Luft nach rechts. */
private fun pastWindow(now: Long, days: Long): TimeWindow {
    val span = days.days()
    return TimeWindow(now - span, now + forwardBuffer(span))
}

private fun forwardBuffer(span: Long): Long = (span * FORWARD_BUFFER_SHARE).toLong()

/**
 * Formt die Verlaufseinträge in Linien um.
 *
 * Gezeichnet wird ausschließlich zwischen tatsächlich eingetragenen Änderungen: Die Linie
 * beginnt beim ersten Punkt im Zeitraum und endet beim letzten. Bewusst keine Stützstellen
 * an den Rändern – eine Linie, die bis zum Rand weiterläuft, behauptet Messpunkte, die es
 * nicht gibt. Wo nichts eingetragen wurde, steht auch keine Linie.
 *
 * Eine Übung mit nur einem Punkt im Zeitraum bleibt sichtbar: Sie zeigt genau diesen einen
 * Punkt, ohne Linie.
 */
fun buildSeries(
    logs: List<WeightLog>,
    names: Collection<String>,
    window: TimeWindow
): List<ChartSeries> {
    val byName = logs.groupBy { it.exerciseName }

    return names.sortedWith(String.CASE_INSENSITIVE_ORDER).mapNotNull { name ->
        val inside = byName[name].orEmpty()
            .filter { it.recordedAt in window.startMillis..window.endMillis }
            .sortedBy { it.recordedAt }
        if (inside.isEmpty()) return@mapNotNull null

        ChartSeries(
            name = name,
            points = inside.map { ChartPoint(it.recordedAt, it.weightKg) }
        )
    }
}

/**
 * Beschriftungen der X-Achse. Die Einteilung richtet sich nach der Spanne des Fensters:
 * Tage bei bis zu gut sechs Wochen, danach Monate, ab gut zwei Jahren Jahreszahlen.
 */
fun buildTimeAxis(
    window: TimeWindow,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.GERMANY
): List<AxisTick> {
    val spanDays = (window.endMillis - window.startMillis) / ONE_DAY_MILLIS
    val start = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(window.endMillis).atZone(zone).toLocalDate()

    val ticks = when {
        spanDays >= YEAR_LABEL_THRESHOLD_DAYS -> {
            (start.year..end.year).map { year ->
                AxisTick(
                    timeMillis = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    label = year.toString()
                )
            }
        }
        spanDays >= MONTH_LABEL_THRESHOLD_DAYS -> {
            generateSequence(start.withDayOfMonth(1)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(end) }
                .map { date ->
                    AxisTick(
                        timeMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                        label = date.month.getDisplayName(TextStyle.SHORT, locale)
                    )
                }
                .toList()
        }
        else -> {
            generateSequence(start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end) }
                .map { date ->
                    AxisTick(
                        timeMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                        label = "${date.dayOfMonth}."
                    )
                }
                .toList()
        }
    }
    return ticks.thinnedTo(MAX_TICKS).filter { it.timeMillis >= window.startMillis }
}

/** Lässt gleichmäßig Beschriftungen weg, bis höchstens [max] übrig sind. */
private fun List<AxisTick>.thinnedTo(max: Int): List<AxisTick> {
    if (size <= max) return this
    val step = (size + max - 1) / max
    return filterIndexed { index, _ -> index % step == 0 }
}

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

private fun Long.days(): Long = this * ONE_DAY_MILLIS
