package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.WeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC

private fun log(weight: Double, date: LocalDate, id: Long = 0) = WeightLog(
    id = id,
    exerciseName = "Bankdrücken",
    weightKg = weight,
    recordedAt = date.atTime(18, 0).toInstant(ZONE).toEpochMilli()
)

class WeightHistoryTest {

    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun ohneVerlaufGibtEsKeineZeile() {
        assertNull(weightHistory(emptyList(), today, ZONE))
    }

    @Test
    fun letzteSteigerungUndTageSeitdem() {
        val history = weightHistory(
            listOf(
                log(55.0, LocalDate.of(2026, 8, 20)),
                log(57.5, LocalDate.of(2026, 9, 4)),
                log(60.0, LocalDate.of(2026, 9, 18))
            ),
            today,
            ZONE
        )!!
        assertEquals(60.0, history.currentKg, 0.0)
        assertEquals(LocalDate.of(2026, 9, 18), history.since)
        assertEquals(12, history.daysSince)
        assertEquals(WeightChange(deltaKg = 2.5, date = LocalDate.of(2026, 9, 18)), history.lastChange)
        assertEquals(listOf(55.0, 57.5, 60.0), history.recentWeights)
    }

    @Test
    fun senkungIstEineNegativeAenderung() {
        // Pfeil nach unten: Die Zeile sagt dann „zuletzt −2,5 kg“.
        val history = weightHistory(
            listOf(log(20.0, LocalDate.of(2026, 9, 1)), log(17.5, LocalDate.of(2026, 9, 29))),
            today,
            ZONE
        )!!
        assertEquals(-2.5, history.lastChange!!.deltaKg, 1e-9)
        assertEquals(1, history.daysSince)
    }

    @Test
    fun einzigerWertHatKeineAenderung() {
        val history = weightHistory(listOf(log(40.0, today)), today, ZONE)!!
        assertNull(history.lastChange)
        assertEquals(0, history.daysSince)
    }

    @Test
    fun gleicherWertNochmalVerlaengertDieZeitSeitDerAenderung() {
        // Ein zweiter Eintrag mit demselben Gewicht ist keine Änderung: „seit“ zählt ab der
        // letzten echten.
        val history = weightHistory(
            listOf(
                log(50.0, LocalDate.of(2026, 9, 1)),
                log(52.5, LocalDate.of(2026, 9, 10)),
                log(52.5, LocalDate.of(2026, 9, 20))
            ),
            today,
            ZONE
        )!!
        assertEquals(LocalDate.of(2026, 9, 10), history.since)
        assertEquals(20, history.daysSince)
        assertEquals(LocalDate.of(2026, 9, 10), history.lastChange!!.date)
    }

    @Test
    fun sparklineNimmtNurDieLetztenWerte() {
        val logs = (1..15).map { log(it.toDouble(), LocalDate.of(2026, 9, it)) }
        val history = weightHistory(logs, today, ZONE)!!
        assertEquals((6..15).map { it.toDouble() }, history.recentWeights)
    }

    @Test
    fun eintraegeAusDerZukunftZaehlenAlsHeute() {
        // Uhr des Handys zurückgestellt: lieber „seit heute“ als „seit -3 Tagen“.
        val history = weightHistory(listOf(log(30.0, today.plusDays(3))), today, ZONE)!!
        assertEquals(0, history.daysSince)
    }

    @Test
    fun vorzeichenStehtImmerDabei() {
        assertEquals("+2,5", 2.5.toSignedDecimalString())
        assertEquals("−1,25", (-1.25).toSignedDecimalString())
        assertEquals("+0,625", 0.625.toSignedDecimalString())
    }
}
