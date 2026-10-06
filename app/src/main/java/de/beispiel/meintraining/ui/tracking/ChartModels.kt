package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.util.toDecimalString
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

/**
 * Ein Punkt im Graphen.
 *
 * Meist eine tatsächlich eingetragene Gewichtsänderung. [isCarried] markiert die übernommenen
 * Stände: am linken Rand den Wert, der schon vor dem Zeitraum galt, und am rechten Ende den
 * letzten Stand, der bis heute weiterläuft. Sie tragen die Linie, sind aber keine Änderung –
 * gezeichnet werden sie deshalb ohne Punkt und ihre Strecken blasser (siehe [WeightChart]).
 */
data class ChartPoint(
    val timeMillis: Long,
    val weightKg: Double,
    val isCarried: Boolean = false,
    /**
     * Veränderung gegenüber dem ersten Wert der Kurve in Prozent – nur in der %-Ansicht
     * gesetzt, siehe [toPercentSeries].
     */
    val percent: Double? = null
) {
    /**
     * Der Wert, nach dem der Punkt in der Höhe steht: in der %-Ansicht die Veränderung, sonst
     * das Gewicht. Skala und Zeichnen fragen nur danach und wissen so nichts von Kilogramm.
     */
    val plotted: Double get() = percent ?: weightKg
}

/** Der Verlauf einer Übung, ältester Punkt zuerst. */
data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>,
    /**
     * In der %-Ansicht: Die Kurve beginnt bei 0 kg und hat damit keinen Bezugswert. Sie bleibt
     * ohne Punkte in der Liste stehen, damit die Legende sie vermerken kann und die übrigen
     * Kurven ihre Farben aus der kg-Ansicht behalten.
     */
    val hasNoPercentBase: Boolean = false
) {
    /**
     * Die Linie, zerlegt in Stücke gleicher Art: echte Strecken zwischen zwei Änderungen und
     * übernommene, die an einem übernommenen Stand hängen. Aufeinanderfolgende Strecken derselben
     * Art bilden ein Stück, damit ein Strichmuster über die Punkte hinweg durchläuft.
     */
    val pieces: List<ChartPiece>
        get() {
            val result = mutableListOf<ChartPiece>()
            points.zipWithNext().forEach { (from, to) ->
                val isCarried = from.isCarried || to.isCarried
                val last = result.lastOrNull()
                if (last != null && last.isCarried == isCarried) {
                    result[result.lastIndex] = last.copy(points = last.points + to)
                } else {
                    result += ChartPiece(listOf(from, to), isCarried)
                }
            }
            return result
        }
}

/** Ein zusammenhängendes Stück einer Linie, siehe [ChartSeries.pieces]. */
data class ChartPiece(val points: List<ChartPoint>, val isCarried: Boolean)

/** Warum der Graph leer ist – jeder Grund hat seinen eigenen Hinweis. */
enum class ChartEmptyReason {
    /** Es gibt überhaupt keinen Verlauf. */
    NOTHING_RECORDED,

    /** Alle Übungen sind abgewählt. */
    NOTHING_SELECTED,

    /** Die gewählten Übungen haben im Zeitraum keinen Stand. */
    NOTHING_IN_RANGE,

    /** In der %-Ansicht: Jede gewählte Übung beginnt bei 0 kg, keine hat einen Bezugswert. */
    NO_PERCENT_BASE
}

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
 * Ein Gewicht ist ein Zustand: Es gilt vom Eintrag an bis zur nächsten Änderung. Gab es vor dem
 * Zeitraum schon einen Wert, beginnt die Linie deshalb am linken Rand mit diesem Stand, und der
 * letzte Stand läuft bis heute weiter – bei einem vergangenen Kalenderjahr bis zu dessen Ende.
 * Früher endete die Linie an den eingetragenen Punkten; bei einem Monat Rückblick verschwand so
 * jede Übung, deren Gewicht sich darin nicht geändert hatte, und blieb keine übrig, stand da
 * „Noch keine Gewichte aufgezeichnet“.
 *
 * Diese übernommenen Stände sind als solche markiert ([ChartPoint.isCarried]) – echte Änderungen
 * bleiben so als Punkte erkennbar.
 *
 * Eine Übung, die an keinem Trainingstag mehr steht (nicht in [activeNames]), läuft nicht bis
 * heute weiter: Ihr Stand galt nur, solange sie trainiert wurde, und endet beim letzten Punkt.
 *
 * Eine Übung mit nur einem Punkt und nichts davor oder danach bleibt sichtbar: Sie zeigt genau
 * diesen einen Punkt, ohne Linie.
 */
fun buildSeries(
    logs: List<WeightLog>,
    names: Collection<String>,
    window: TimeWindow,
    now: Long,
    activeNames: Set<String>
): List<ChartSeries> {
    val byName = logs.groupBy { it.exerciseName }

    return names.sortedWith(String.CASE_INSENSITIVE_ORDER).mapNotNull { name ->
        val all = byName[name].orEmpty().sortedBy { it.recordedAt }
        if (all.isEmpty()) return@mapNotNull null
        val before = all.lastOrNull { it.recordedAt < window.startMillis }
        val inside = all.filter { it.recordedAt in window.startMillis..window.endMillis }

        // Bis hierhin gilt der jüngste Stand: bis heute, solange die Übung noch trainiert wird,
        // sonst bis zu ihrem letzten Eintrag – und nie über den Zeitraum hinaus.
        val validUntil = minOf(
            window.endMillis,
            if (name in activeNames) now else all.last().recordedAt
        )
        // Ein Stand von vorher, der im Zeitraum gar nicht mehr galt, gehört nicht hinein.
        val carriedStart = before?.takeIf { inside.isNotEmpty() || validUntil > window.startMillis }
        if (carriedStart == null && inside.isEmpty()) return@mapNotNull null

        val points = buildList {
            carriedStart?.let { add(ChartPoint(window.startMillis, it.weightKg, isCarried = true)) }
            inside.forEach { add(ChartPoint(it.recordedAt, it.weightKg)) }
            val latest = last()
            if (validUntil > latest.timeMillis) {
                add(ChartPoint(validUntil, latest.weightKg, isCarried = true))
            }
        }
        ChartSeries(name = name, points = points)
    }
}

/**
 * Rechnet die Kurven in Prozent um: jeder Punkt als Veränderung gegenüber dem ersten Wert seiner
 * Kurve im Zeitraum. So lassen sich Übungen mit ganz verschiedenen Gewichten nebeneinanderlegen
 * – 5 kg mehr beim Kreuzheben sind etwas anderes als 5 kg mehr beim Seitheben.
 *
 * Der erste Wert ist der erste Punkt der Linie, also auch ein übernommener Stand am linken Rand:
 * Die Kurve beginnt im Zeitraum bei 0 % und zeigt, was sich *darin* getan hat.
 *
 * Bei Übungen in [decreasingNames] – Pfeil nach unten – ist eine Senkung der Fortschritt und
 * zählt deshalb positiv: 20 kg auf 15 kg ergibt +25 %.
 *
 * Eine Kurve, die bei 0 kg beginnt, hat keinen Bezugswert; durch null teilen lässt sich nicht.
 * Sie verliert ihre Punkte und ist als [ChartSeries.hasNoPercentBase] markiert.
 */
fun toPercentSeries(series: List<ChartSeries>, decreasingNames: Set<String>): List<ChartSeries> =
    series.map { line ->
        val base = line.points.firstOrNull()?.weightKg ?: return@map line
        if (base <= 0.0) return@map line.copy(points = emptyList(), hasNoPercentBase = true)
        val direction = if (line.name in decreasingNames) -1.0 else 1.0
        line.copy(
            points = line.points.map { point ->
                point.copy(percent = direction * (point.weightKg - base) / base * PERCENT)
            }
        )
    }

/**
 * Zahl an der Prozent-Achse: `10.0 → "10"`, `-5.0 → "−5"` – mit echtem Minuszeichen, ohne Plus:
 * Die Null steht als eigene Linie dazwischen.
 */
fun Double.toAxisNumber(): String =
    if (this < 0) "$MINUS_SIGN${(-this).toDecimalString()}" else toDecimalString()

private const val PERCENT = 100.0
private const val MINUS_SIGN = '\u2212'

/**
 * Die Übungen, die noch an einem Trainingstag stehen – ihr letzter Stand läuft im Graphen bis
 * heute weiter (siehe [buildSeries]).
 *
 * Nicht dazu gehören ausgeblendete Übungen und solche, die nur an stillgelegten Tagen jenseits
 * der eingestellten Runde stehen: Auf keinem Trainingstag zu sehen, wird ihr Gewicht auch nicht
 * mehr trainiert.
 */
fun activeExerciseNames(
    exercises: List<ExerciseItem>,
    dayCount: Int,
    hiddenNames: Set<String>
): Set<String> = exercises.asSequence()
    .filter { it.dayId <= dayCount && it.name !in hiddenNames }
    .map { it.name }
    .toSet()

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
