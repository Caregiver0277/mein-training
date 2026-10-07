package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private const val DAYS = 3

/** Ein Montag. */
private val START: LocalDate = LocalDate.of(2026, 6, 1)
private val TODAY: LocalDate = LocalDate.of(2026, 8, 5)

private fun at(date: LocalDate): Long =
    date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Tag [dayId] am [offset]-ten Tag nach [START]. */
private fun entry(dayId: Int, offset: Long): RotationEntry {
    val date = START.plusDays(offset)
    return RotationEntry(dayId = dayId, date = date, completedAt = at(date))
}

/** Ein Schnitt am Abend des [offset]-ten Tages – wie der Pfeil neben dem Haken ihn zieht. */
private fun cut(offset: Long): Long = at(START.plusDays(offset)) + 6 * 60 * 60 * 1000L

class RotationStatsTest {

    @Test
    fun ohneAbgeschlosseneRundeGibtEsKeineBilanz() {
        assertNull(rotationSummary(emptyList(), DAYS, TODAY))
        // Die laufende Runde zählt nicht.
        assertNull(rotationSummary(listOf(entry(1, 0), entry(2, 2)), DAYS, TODAY))
    }

    @Test
    fun eineHeuteVolleRundeZaehltErstAbMorgen() {
        val today = START.plusDays(4)
        val entries = listOf(entry(1, 0), entry(2, 2), entry(3, 4))
        assertNull(rotationSummary(entries, DAYS, today))
        assertEquals(1, rotationSummary(entries, DAYS, today.plusDays(1))?.count)
    }

    @Test
    fun volleRundenZaehlenMitIhrerDauer() {
        // Runde 1: Tag 0 bis 4 (5 Tage), Runde 2: Tag 7 bis 9 (3 Tage), dann läuft Runde 3.
        val entries = listOf(
            entry(1, 0), entry(2, 2), entry(3, 4),
            entry(1, 7), entry(2, 8), entry(3, 9),
            entry(1, 11)
        )
        val summary = rotationSummary(entries, DAYS, TODAY)!!
        assertEquals(2, summary.count)
        assertEquals(2, summary.fullCount)
        assertEquals(0, summary.abortedCount)
        assertEquals(4.0, summary.averageDays, 0.001)
        assertEquals(1.0, summary.fullShare, 0.001)
        assertNull(summary.mostMissedDayId)
    }

    @Test
    fun perPfeilAbgebrocheneRundenNennenDenFehlendenTag() {
        val entries = listOf(
            // Runde 1: Tag 3 fehlt, abgebrochen.
            entry(1, 0), entry(2, 2),
            // Runde 2: voll.
            entry(1, 7), entry(2, 8), entry(3, 9),
            // Runde 3: nur Tag 1, abgebrochen – 2 und 3 fehlen.
            entry(1, 14),
            // Runde 4 läuft.
            entry(1, 21)
        )
        val cuts = listOf(cut(2), cut(14))
        val summary = rotationSummary(entries, DAYS, TODAY, cuts)!!
        assertEquals(3, summary.count)
        assertEquals(1, summary.fullCount)
        assertEquals(2, summary.abortedCount)
        assertEquals(1.0 / 3, summary.fullShare, 0.001)
        assertEquals(3, summary.mostMissedDayId)
        assertEquals(2, summary.mostMissedCount)
        // Dauer: 3, 3 und 1 Tag.
        assertEquals(7.0 / 3, summary.averageDays, 0.001)
    }

    @Test
    fun beiGleichstandGiltDerVordersteTag() {
        // Eine abgebrochene Runde mit nur Tag 2: Tag 1 und 3 fehlen je einmal.
        val entries = listOf(entry(2, 0), entry(1, 5))
        val summary = rotationSummary(entries, DAYS, TODAY, listOf(cut(0)))!!
        assertEquals(1, summary.mostMissedDayId)
        assertEquals(1, summary.mostMissedCount)
    }

    @Test
    fun ohneSchnitteVerschmelzenAbgebrocheneRunden() {
        // Dieselben Trainings wie oben, aber ohne Schnitte – so stehen sie nach einer eingelesenen
        // Sicherung da: Die Runden ergeben sich nur noch aus vollen Runden.
        val entries = listOf(
            entry(1, 0), entry(2, 2),
            entry(1, 7), entry(2, 8), entry(3, 9),
            entry(1, 14),
            entry(1, 21)
        )
        val summary = rotationSummary(entries, DAYS, TODAY)!!
        // 1, 2, 1(+7) … erst mit Tag 3 an Tag 9 ist die erste Runde voll.
        assertEquals(1, summary.count)
        assertEquals(1, summary.fullCount)
    }
}
