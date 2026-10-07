package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC

/** Ein Mittwoch. */
private val HEUTE: LocalDate = LocalDate.of(2026, 10, 7)

class SetRecordsTest {

    @Test
    fun epleyBisZwoelfWiederholungen() {
        assertEquals(100.0 * (1 + 10 / 30.0), estimatedOneRepMax(100.0, 10)!!, 1e-9)
        assertEquals(80.0 * 1.4, estimatedOneRepMax(80.0, 12)!!, 1e-9)
        assertNull(estimatedOneRepMax(80.0, 13))
    }

    @Test
    fun eineWiederholungIstDasEinerMaximum() {
        assertEquals(120.0, estimatedOneRepMax(120.0, 1)!!, 0.0)
    }

    @Test
    fun ohneGewichtOderWiederholungKeinEinerMaximum() {
        assertNull(estimatedOneRepMax(null, 8))
        assertNull(estimatedOneRepMax(0.0, 8))
        assertNull(estimatedOneRepMax(60.0, 0))
    }

    @Test
    fun verlaufNimmtJeTagDenBestenSatz() {
        val sets = listOf(
            set(daysAgo = 7, number = 1, reps = 10, kg = 60.0),
            set(daysAgo = 7, number = 2, reps = 8, kg = 60.0),
            set(daysAgo = 3, number = 1, reps = 5, kg = 70.0),
            // Über zwölf Wiederholungen: taugt nicht.
            set(daysAgo = 1, number = 1, reps = 15, kg = 50.0)
        )
        val history = oneRepMaxHistory(sets, ZONE)
        assertEquals(listOf(HEUTE.minusDays(7), HEUTE.minusDays(3)), history.map { it.date })
        assertEquals(80.0, history[0].kg, 1e-9)
        assertEquals(70.0 * (1 + 5 / 30.0), history[1].kg, 1e-9)
    }

    @Test
    fun besterSatzNachEinerMaximum() {
        val heavy = set(daysAgo = 5, number = 1, reps = 3, kg = 100.0)
        val volume = set(daysAgo = 3, number = 1, reps = 10, kg = 85.0)
        // 100 × 3 ≈ 110, 85 × 10 ≈ 113,3.
        assertEquals(volume, bestSet(listOf(heavy, volume)))
    }

    @Test
    fun besterSatzBeiGleichstandDerFruehere() {
        val first = set(daysAgo = 9, number = 1, reps = 8, kg = 60.0)
        val again = set(daysAgo = 2, number = 1, reps = 8, kg = 60.0)
        assertEquals(first, bestSet(listOf(again, first)))
    }

    @Test
    fun besterSatzOhneTauglichenNachGewicht() {
        val light = set(daysAgo = 5, number = 1, reps = 20, kg = 40.0)
        val heavier = set(daysAgo = 3, number = 1, reps = 15, kg = 45.0)
        assertEquals(heavier, bestSet(listOf(light, heavier)))
        assertNull(bestSet(listOf(set(daysAgo = 1, number = 1, reps = 12, kg = null))))
    }

    @Test
    fun rekordeJeWiederholungszahl() {
        val sets = listOf(
            set(daysAgo = 20, number = 1, reps = 5, kg = 80.0),
            set(daysAgo = 10, number = 1, reps = 5, kg = 85.0),
            // Dieselben 85 kg später noch einmal: Der Rekord bleibt beim ersten Mal.
            set(daysAgo = 4, number = 1, reps = 5, kg = 85.0),
            set(daysAgo = 8, number = 2, reps = 8, kg = 75.0),
            set(daysAgo = 6, number = 3, reps = 10, kg = 60.0)
        )
        val records = repRecords(sets)
        assertEquals(listOf(5, 8, 10), records.map { it.reps })
        assertEquals(listOf(85.0, 75.0, 60.0), records.map { it.weightKg })
        assertEquals(sets[1].performedAt, records[0].performedAt)
    }

    @Test
    fun einRekordMitMehrWiederholungenSchlaegtDenMitWeniger() {
        val sets = listOf(
            set(daysAgo = 9, number = 1, reps = 6, kg = 75.0),
            set(daysAgo = 2, number = 1, reps = 8, kg = 80.0)
        )
        assertEquals(listOf(8), repRecords(sets).map { it.reps })
    }

    @Test
    fun volumenJeWocheMitLuecken() {
        val sets = listOf(
            // Diese Woche (ab Montag, 5. Oktober).
            set(daysAgo = 1, number = 1, reps = 10, kg = 60.0),
            set(daysAgo = 1, number = 2, reps = 8, kg = 60.0),
            // Ohne Gewicht: trägt nichts bei.
            set(daysAgo = 2, number = 1, reps = 12, kg = null),
            // Vor zwei Wochen.
            set(daysAgo = 15, number = 1, reps = 5, kg = 100.0)
        )
        val weeks = weeklyVolume(sets, HEUTE, weeks = 3, zone = ZONE)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5)),
            weeks.map { it.weekStart }
        )
        assertEquals(listOf(500.0, 0.0, 1080.0), weeks.map { it.volumeKg })
    }

    private fun set(daysAgo: Long, number: Int, reps: Int, kg: Double?) = SetLog(
        id = daysAgo * 10 + number,
        exerciseName = "Bankdrücken",
        dayId = 1,
        performedAt = HEUTE.minusDays(daysAgo).atTime(18, number).toInstant(ZONE).toEpochMilli(),
        setNumber = number,
        reps = reps,
        weightKg = kg
    )
}
