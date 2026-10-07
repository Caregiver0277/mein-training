package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/** Ein Mittwoch. */
private val TODAY: LocalDate = LocalDate.of(2026, 10, 7)

class HeatmapTest {

    @Test
    fun derKalenderReichtGenauEinJahrZurueckUndEndetHeute() {
        val map = heatmap(emptyList(), TODAY)
        val days = map.weeks.flatMap { it.days }.filterNotNull()
        assertEquals(LocalDate.of(2025, 10, 8), days.first().date)
        assertEquals(TODAY, days.last().date)
        assertEquals(365, days.size)
    }

    @Test
    fun jedeSpalteIstEineWocheVonMontagBisSonntag() {
        val map = heatmap(emptyList(), TODAY)
        map.weeks.forEach { week ->
            assertEquals(7, week.days.size)
            week.days.forEachIndexed { index, day ->
                if (day != null) assertEquals(DayOfWeek.entries[index], day.date.dayOfWeek)
            }
        }
        // 8. Oktober 2025 ist ein Mittwoch: Montag und Dienstag davor bleiben leer.
        assertNull(map.weeks.first().days[0])
        assertNull(map.weeks.first().days[1])
        // Heute ist Mittwoch: Donnerstag bis Sonntag bleiben leer.
        assertEquals(TODAY, map.weeks.last().days[2]?.date)
        assertNull(map.weeks.last().days[3])
        assertEquals(53, map.weeks.size)
    }

    @Test
    fun trainingsWerdenJeTagGezaehlt() {
        val dates = listOf(TODAY, TODAY, TODAY.minusDays(1), TODAY.minusDays(9))
        val map = heatmap(dates, TODAY)
        val counts = map.weeks.flatMap { it.days }.filterNotNull().associate { it.date to it.count }
        assertEquals(2, counts[TODAY])
        assertEquals(1, counts[TODAY.minusDays(1)])
        assertEquals(1, counts[TODAY.minusDays(9)])
        assertEquals(0, counts[TODAY.minusDays(2)])
    }

    @Test
    fun trainingsAusserhalbDesZeitraumsFehlen() {
        val dates = listOf(TODAY.plusDays(1), LocalDate.of(2025, 10, 7), LocalDate.of(2025, 10, 8))
        val map = heatmap(dates, TODAY)
        assertEquals(1, map.weeks.flatMap { it.days }.filterNotNull().sumOf { it.count })
    }

    @Test
    fun derMonatsnameStehtUeberDerWocheMitDemErsten() {
        val map = heatmap(emptyList(), TODAY)
        // Der 1. November 2025 ist ein Samstag – in der Woche ab dem 27. Oktober.
        val november = map.weeks.indexOfFirst { it.monthLabel == Month.NOVEMBER }
        assertEquals(
            LocalDate.of(2025, 10, 27),
            map.weeks[november].days.filterNotNull().first().date
        )
        // Jeder Monat bekommt genau einen Namen; der Oktober zweimal – angebrochen vorn, neu hinten.
        val labels = map.weeks.mapNotNull { it.monthLabel }
        assertEquals(13, labels.size)
        assertEquals(Month.OCTOBER, labels.first())
        assertEquals(Month.OCTOBER, labels.last())
    }

    @Test
    fun derAngebrocheneErsteMonatBekommtKeinenNamenOhnePlatz() {
        // Beginn am 28. Oktober (Dienstag), der 1. November ist ein Samstag derselben Woche:
        // Die erste Spalte trägt schon den November.
        val today = LocalDate.of(2026, 10, 27)
        val map = heatmap(emptyList(), today)
        assertEquals(Month.NOVEMBER, map.weeks[0].monthLabel)
        assertEquals(1, map.weeks.count { it.monthLabel == Month.NOVEMBER })

        // Beginn am 20. Oktober 2025 (Montag), der 1. November liegt zwei Spalten weiter.
        val tight = heatmap(emptyList(), LocalDate.of(2026, 10, 19))
        assertNull(tight.weeks[0].monthLabel)
        assertEquals(Month.NOVEMBER, tight.weeks[1].monthLabel)
    }

    @Test
    fun dayAtLiefertNullAusserhalb() {
        val map = heatmap(listOf(TODAY), TODAY)
        assertEquals(1, map.dayAt(map.weeks.lastIndex, 2)?.count)
        assertNull(map.dayAt(map.weeks.size, 0))
        assertNull(map.dayAt(0, 7))
        assertNull(map.dayAt(-1, 0))
    }
}
