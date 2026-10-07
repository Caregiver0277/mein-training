package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.WeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC
private val HEUTE: LocalDate = LocalDate.of(2026, 10, 7)

class ForecastTest {

    @Test
    fun markenUnterVierzigInFuenferschritten() {
        assertEquals(5.0, nextMark(0.0), 0.0)
        assertEquals(25.0, nextMark(22.5), 0.0)
        assertEquals(40.0, nextMark(35.0), 0.0)
        assertEquals(40.0, nextMark(37.5), 0.0)
    }

    @Test
    fun markenAbVierzigInZehnerschritten() {
        assertEquals(50.0, nextMark(40.0), 0.0)
        assertEquals(100.0, nextMark(92.5), 0.0)
        assertEquals(110.0, nextMark(100.0), 0.0)
        // Ein Rundungsrest knapp unter der Marke zählt als die Marke selbst.
        assertEquals(110.0, nextMark(99.99999999), 0.0)
    }

    @Test
    fun gleichmaessigesTempoErgibtDasErwarteteDatum() {
        // Alle 14 Tage +2,5 kg, seit gut drei Monaten; zuletzt heute auf 90 kg.
        val logs = (0..7).map { step -> weight(72.5 + step * 2.5, daysAgo = 98L - step * 14) }
        val forecast = weightForecast("Bankdrücken", logs, HEUTE, ZONE)!!
        assertEquals(90.0, forecast.currentKg, 0.0)
        assertEquals(100.0, forecast.targetKg, 0.0)
        // Die Treppe steigt im Schnitt 2,5 kg je 14 Tage: 10 kg brauchen etwa 56 Tage.
        val days = java.time.temporal.ChronoUnit.DAYS.between(HEUTE, forecast.date)
        assertTrue("$days Tage", days in 50..62)
        assertEquals(1.25, forecast.kgPerWeek, 0.15)
    }

    @Test
    fun zuWenigeSteigerungenKeinePrognose() {
        val logs = listOf(weight(60.0, 60), weight(62.5, 40), weight(65.0, 20))
        assertNull(weightForecast("Bankdrücken", logs, HEUTE, ZONE))
    }

    @Test
    fun steigerungenInZuKurzerZeitKeinePrognose() {
        // Drei Steigerungen, aber alle innerhalb von drei Wochen.
        val logs = listOf(weight(60.0, 21), weight(62.5, 14), weight(65.0, 7), weight(67.5, 0))
        assertNull(weightForecast("Bankdrücken", logs, HEUTE, ZONE))
    }

    @Test
    fun stillstandImZeitraumKeinePrognose() {
        // Die Steigerungen liegen ein halbes Jahr zurück, seither steht das Gewicht.
        val logs = listOf(
            weight(60.0, 250), weight(62.5, 230), weight(65.0, 210), weight(67.5, 190)
        )
        assertNull(weightForecast("Bankdrücken", logs, HEUTE, ZONE))
    }

    @Test
    fun zuLangsamesTempoKeinePrognose() {
        // 1,25 kg in 90 Tagen; der Rest bis 100 kg dauerte Jahre.
        val logs = listOf(
            weight(90.0, 200),
            weight(90.5, 150),
            weight(91.0, 100),
            weight(91.25, 80),
            weight(91.5, 40)
        )
        assertNull(weightForecast("Bankdrücken", logs, HEUTE, ZONE))
    }

    @Test
    fun einbruchImZeitraumKeinePrognose() {
        val logs = listOf(
            weight(60.0, 100), weight(62.5, 80), weight(65.0, 60), weight(67.5, 40),
            // Danach deutlich zurückgenommen – die Gerade fällt.
            weight(50.0, 20)
        )
        assertNull(weightForecast("Bankdrücken", logs, HEUTE, ZONE))
    }

    private fun weight(kg: Double, daysAgo: Long) = WeightLog(
        exerciseName = "Bankdrücken",
        weightKg = kg,
        recordedAt = HEUTE.minusDays(daysAgo).atTime(18, 0).toInstant(ZONE).toEpochMilli()
    )
}
