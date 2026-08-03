package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.WeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
private const val ONE_DAY = 24L * 60 * 60 * 1000

/** Fester Bezugspunkt, damit die Tests unabhängig vom Tag des Ausführens sind. */
private val NOW = LocalDate.of(2026, 7, 15).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli()

private fun log(name: String, weight: Double, daysAgo: Long) =
    WeightLog(exerciseName = name, weightKg = weight, recordedAt = NOW - daysAgo * ONE_DAY)

class ChartModelsTest {

    // --- Zeitfenster -------------------------------------------------------

    @Test
    fun einMonatBlicktDreissigTageZurueck() {
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, emptyList(), NOW, ZONE)
        assertEquals(NOW - 30 * ONE_DAY, window.startMillis)
        // Rechts bleibt Luft, damit der jüngste Punkt nicht am Rand klebt.
        assertTrue(window.endMillis > NOW)
    }

    @Test
    fun gesamtBeginntBeimAeltestenEintrag() {
        val logs = listOf(log("A", 20.0, 400), log("A", 25.0, 10))
        val window = timeWindowFor(TimeRange.TOTAL, 2026, logs, NOW, ZONE)
        assertEquals(NOW - 400 * ONE_DAY, window.startMillis)
        assertTrue(window.endMillis > NOW)
    }

    @Test
    fun derErsteEintragStehtLinksUndNichtAmRechtenRand() {
        // Nur ein Eintrag von heute: Er muss auf der linken Seite der Skala landen.
        val logs = listOf(log("A", 20.0, 0))
        val window = timeWindowFor(TimeRange.TOTAL, 2026, logs, NOW, ZONE)

        assertEquals(NOW, window.startMillis)
        val share = (NOW - window.startMillis).toDouble() / (window.endMillis - window.startMillis)
        assertTrue("Punkt läge bei $share statt links", share < 0.1)
    }

    @Test
    fun gesamtOhneEintraegeBleibtDarstellbarBreit() {
        val window = timeWindowFor(TimeRange.TOTAL, 2026, emptyList(), NOW, ZONE)
        assertTrue((window.endMillis - window.startMillis) / ONE_DAY >= 14L)
    }

    @Test
    fun manuellesJahrUmfasstDasKalenderjahr() {
        val window = timeWindowFor(TimeRange.MANUAL_YEAR, 2025, emptyList(), NOW, ZONE)
        val start = LocalDate.of(2025, 1, 1).atStartOfDay(ZONE).toInstant().toEpochMilli()
        assertEquals(start, window.startMillis)
        assertTrue(window.endMillis > start)
    }

    // --- Linien ------------------------------------------------------------

    @Test
    fun dieLinieEndetBeimLetztenPunkt() {
        // Keine Stützstelle am rechten Rand: Wo nichts eingetragen wurde, läuft keine Linie.
        val logs = listOf(log("A", 20.0, 20), log("A", 22.5, 10))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        val series = buildSeries(logs, listOf("A"), window).single()

        assertEquals(2, series.points.size)
        assertEquals(logs.last().recordedAt, series.points.last().timeMillis)
        assertEquals(22.5, series.points.last().weightKg, 0.0)
    }

    @Test
    fun aeltereAenderungenVorDemFensterZaehlenNichtMehr() {
        // Früher trug dieser Eintrag den linken Rand; jetzt gibt es im Fenster nichts zu zeigen.
        val logs = listOf(log("A", 40.0, 200))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        assertTrue(buildSeries(logs, listOf("A"), window).isEmpty())
    }

    @Test
    fun eineEinzelneAenderungBleibtAlsPunktSichtbar() {
        val logs = listOf(log("A", 20.0, 5))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        val series = buildSeries(logs, listOf("A"), window).single()

        assertEquals(1, series.points.size)
        assertEquals(20.0, series.points.single().weightKg, 0.0)
    }

    @Test
    fun uebungenOhneDatenImFensterEntfallen() {
        val logs = listOf(log("A", 20.0, 5))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        val series = buildSeries(logs, listOf("A", "B"), window)

        assertEquals(listOf("A"), series.map { it.name })
    }

    // --- X-Achse -----------------------------------------------------------

    @Test
    fun kurzerZeitraumWirdInTagenBeschriftet() {
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, emptyList(), NOW, ZONE)
        val labels = buildTimeAxis(window, ZONE).map { it.label }
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.all { it.endsWith(".") })
    }

    @Test
    fun halbesJahrWirdInMonatenBeschriftet() {
        val window = timeWindowFor(TimeRange.MONTHS_6, 2026, emptyList(), NOW, ZONE)
        val labels = buildTimeAxis(window, ZONE).map { it.label }
        assertTrue(labels.contains("Mai") || labels.contains("Apr"))
    }

    @Test
    fun langerZeitraumWirdInJahrenBeschriftet() {
        val logs = listOf(log("A", 20.0, 1200))
        val window = timeWindowFor(TimeRange.TOTAL, 2026, logs, NOW, ZONE)
        val labels = buildTimeAxis(window, ZONE).map { it.label }
        assertTrue(labels.contains("2026"))
    }

    @Test
    fun achseBleibtLesbarKurz() {
        val logs = listOf(log("A", 20.0, 300))
        val window = timeWindowFor(TimeRange.TOTAL, 2026, logs, NOW, ZONE)
        assertTrue(buildTimeAxis(window, ZONE).size <= 7)
    }

    // --- Aussehen der Kurven ----------------------------------------------

    @Test
    fun ersteKurveBekommtDieRuhigsteDarstellung() {
        assertEquals(SeriesStyle.SOLID, appearanceFor(0).style)
    }

    @Test
    fun benachbarteKurvenUnterscheidenSichInFarbeUndLinienart() {
        val first = appearanceFor(0)
        val second = appearanceFor(1)
        assertTrue(first.color != second.color)
        assertTrue(first.style != second.style)
    }
}
