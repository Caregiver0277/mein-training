package de.beispiel.meintraining.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/** So weit reicht der Kalender der Statistik zurück. */
const val HEATMAP_MONTHS = 12L

/**
 * Unter so vielen Spalten Abstand bekommt die erste Woche keinen eigenen Monatsnamen – er liefe
 * sonst in den des folgenden Monats hinein.
 */
private const val MIN_LABEL_GAP_WEEKS = 3

/** Ein Tag im Kalender: Datum und Anzahl der an ihm abgehakten Trainings. */
data class HeatmapDay(val date: LocalDate, val count: Int)

/**
 * Eine Spalte des Kalenders: eine Woche, Montag bis Sonntag.
 *
 * [days] hat immer sieben Stellen; Tage vor Beginn des Zeitraums und nach heute sind `null` und
 * bleiben leer. [monthLabel] ist der Monat, dessen Name über der Spalte steht – `null` für die
 * meisten Spalten.
 */
data class HeatmapWeek(val days: List<HeatmapDay?>, val monthLabel: Month? = null)

/** Der ganze Kalender, älteste Woche zuerst – die jüngste Spalte enthält [today]. */
data class Heatmap(val weeks: List<HeatmapWeek>, val today: LocalDate) {

    /** Der Tag an dieser Stelle – `null` außerhalb des Kalenders und für leere Tage. */
    fun dayAt(week: Int, dayOfWeek: Int): HeatmapDay? =
        weeks.getOrNull(week)?.days?.getOrNull(dayOfWeek)
}

/**
 * Der Kalender der letzten [months] Monate im Stil von GitHub: eine Spalte je Woche, Montag oben.
 *
 * Er beginnt am Tag nach demselben Datum vor [months] Monaten, sodass genau ein Jahr zu sehen ist
 * und nicht ein Jahr und ein Tag. Die erste Spalte füllt nur auf, was in diesen Zeitraum fällt;
 * die letzte endet heute. Trainings mit einem Datum nach heute – durch einen Zeitzonenwechsel
 * oder eine eingelesene Sicherung – liegen außerhalb und fehlen.
 *
 * Ein Monatsname steht über der Woche, in der der Monat beginnt. Der angebrochene erste Monat
 * hat keinen solchen Anfang; er bekommt seinen Namen über der ersten Spalte, wenn bis zum
 * nächsten Monatsnamen Platz ist.
 */
fun heatmap(dates: List<LocalDate>, today: LocalDate, months: Long = HEATMAP_MONTHS): Heatmap {
    val start = today.minusMonths(months).plusDays(1)
    val counts = dates.filter { !it.isBefore(start) && !it.isAfter(today) }
        .groupingBy { it }
        .eachCount()

    val weeks = mutableListOf<HeatmapWeek>()
    var monday = start.with(DayOfWeek.MONDAY)
    while (!monday.isAfter(today)) {
        val days = (0L until DayOfWeek.entries.size).map { offset ->
            val date = monday.plusDays(offset)
            if (date.isBefore(start) || date.isAfter(today)) null else HeatmapDay(date, counts[date] ?: 0)
        }
        val monthStart = days.firstOrNull { it != null && it.date.dayOfMonth == 1 }
        weeks += HeatmapWeek(days = days, monthLabel = monthStart?.date?.month)
        monday = monday.plusWeeks(1)
    }

    val firstLabel = weeks.indexOfFirst { it.monthLabel != null }
    if (weeks.isNotEmpty() && weeks[0].monthLabel == null &&
        (firstLabel == -1 || firstLabel >= MIN_LABEL_GAP_WEEKS)
    ) {
        weeks[0] = weeks[0].copy(monthLabel = start.month)
    }
    return Heatmap(weeks = weeks, today = today)
}
