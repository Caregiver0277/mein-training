package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.ExerciseItem
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

    /** Die Linien für den Zeitraum, mit „A“ und „B“ als Übungen, die noch trainiert werden. */
    private fun seriesFor(
        logs: List<WeightLog>,
        range: TimeRange = TimeRange.MONTH_1,
        names: List<String> = listOf("A"),
        activeNames: Set<String> = setOf("A", "B"),
        year: Int = 2026
    ): List<ChartSeries> =
        buildSeries(logs, names, timeWindowFor(range, year, logs, NOW, ZONE), NOW, activeNames)

    @Test
    fun derLetzteStandLaeuftBisHeuteWeiter() {
        val logs = listOf(log("A", 20.0, 20), log("A", 22.5, 10))

        val points = seriesFor(logs).single().points

        assertEquals(3, points.size)
        assertEquals(logs.last().recordedAt, points[1].timeMillis)
        assertFalse(points[1].isCarried)
        // Kein Messpunkt, sondern der übernommene Stand – genau bis heute, nicht bis zum Rand.
        assertEquals(ChartPoint(NOW, 22.5, isCarried = true), points.last())
    }

    @Test
    fun einStandVonVorDemZeitraumBeginntDieLinieAmLinkenRand() {
        val logs = listOf(log("A", 40.0, 200), log("A", 42.5, 10))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        val points = buildSeries(logs, listOf("A"), window, NOW, setOf("A")).single().points

        assertEquals(ChartPoint(window.startMillis, 40.0, isCarried = true), points.first())
        assertEquals(42.5, points[1].weightKg, 0.0)
        assertFalse(points[1].isCarried)
    }

    /** Der Fehler von früher: Ohne Änderung im Zeitraum verschwand die Übung ganz. */
    @Test
    fun eineUebungOhneAenderungImZeitraumBleibtAlsLinieStehen() {
        val logs = listOf(log("A", 40.0, 200))
        val window = timeWindowFor(TimeRange.MONTH_1, 2026, logs, NOW, ZONE)

        val points = seriesFor(logs).single().points

        assertEquals(
            listOf(
                ChartPoint(window.startMillis, 40.0, isCarried = true),
                ChartPoint(NOW, 40.0, isCarried = true)
            ),
            points
        )
    }

    @Test
    fun einVergangenesJahrLaeuftBisZuSeinemEnde() {
        val logs = listOf(log("A", 30.0, 600), log("A", 32.5, 400))
        val window = timeWindowFor(TimeRange.MANUAL_YEAR, 2025, logs, NOW, ZONE)

        val points = seriesFor(logs, TimeRange.MANUAL_YEAR, year = 2025).single().points

        // 600 Tage zurück liegt 2024: Der Stand trägt den linken Rand, 2025 kommt 32,5 dazu.
        assertEquals(30.0, points.first().weightKg, 0.0)
        assertTrue(points.first().isCarried)
        assertEquals(ChartPoint(window.endMillis, 32.5, isCarried = true), points.last())
    }

    @Test
    fun eineNichtMehrTrainierteUebungEndetBeimLetztenPunkt() {
        val logs = listOf(log("C", 20.0, 20), log("C", 22.5, 10))

        val points = seriesFor(logs, names = listOf("C")).single().points

        assertEquals(2, points.size)
        assertTrue(points.none { it.isCarried })
        assertEquals(logs.last().recordedAt, points.last().timeMillis)
    }

    @Test
    fun eineVorDemZeitraumAufgegebeneUebungFehltDarin() {
        // Ihr Stand galt nur, solange sie trainiert wurde – und das war vor dem Zeitraum.
        val logs = listOf(log("C", 40.0, 200))

        assertTrue(seriesFor(logs, names = listOf("C")).isEmpty())
    }

    @Test
    fun eineAufgegebeneUebungLaeuftBisZuIhremLetztenEintragWeiter() {
        // Vor dem Zeitraum eingetragen, im Zeitraum noch einmal: Der alte Stand trägt den Rand.
        val logs = listOf(log("C", 40.0, 200), log("C", 40.0, 10))

        val points = seriesFor(logs, names = listOf("C")).single().points

        assertTrue(points.first().isCarried)
        assertFalse(points.last().isCarried)
    }

    @Test
    fun eineEinzelneAenderungOhneVorgeschichteBleibtAlsPunktSichtbar() {
        val logs = listOf(log("C", 20.0, 5))

        val series = seriesFor(logs, names = listOf("C")).single()

        assertEquals(listOf(ChartPoint(logs.single().recordedAt, 20.0)), series.points)
    }

    @Test
    fun uebungenOhneDatenEntfallen() {
        val logs = listOf(log("A", 20.0, 5))

        val series = seriesFor(logs, names = listOf("A", "B"))

        assertEquals(listOf("A"), series.map { it.name })
    }

    @Test
    fun spaetereEintraegeTragenEinVergangenesJahrNicht() {
        // Erst im Juli 2026 eingetragen: 2025 gab es diesen Stand noch nicht.
        val logs = listOf(log("A", 20.0, 0))

        assertTrue(seriesFor(logs, TimeRange.MANUAL_YEAR, year = 2025).isEmpty())
    }

    @Test
    fun stueckeTrennenEchteVonUebernommenenStrecken() {
        val series = ChartSeries(
            "A",
            listOf(
                ChartPoint(0, 40.0, isCarried = true),
                ChartPoint(1, 42.5),
                ChartPoint(2, 45.0),
                ChartPoint(3, 45.0, isCarried = true)
            )
        )

        val pieces = series.pieces

        assertEquals(listOf(true, false, true), pieces.map { it.isCarried })
        assertEquals(listOf(1L, 2L), pieces[1].points.map { it.timeMillis })
        assertTrue(ChartSeries("A", listOf(ChartPoint(0, 40.0))).pieces.isEmpty())
    }

    @Test
    fun ausgeblendeteUndStillgelegteUebungenLaufenNichtWeiter() {
        fun item(name: String, dayId: Int) = ExerciseItem(
            id = 0, dayId = dayId, name = name, variation = null, sets = null, repsMin = null,
            repsMax = null, position = 0, supersetId = null, weightKg = 20.0,
            progressionStepKg = 2.5
        )
        val exercises = listOf(item("A", 1), item("B", 2), item("C", 6))

        assertEquals(setOf("A"), activeExerciseNames(exercises, dayCount = 4, hiddenNames = setOf("B")))
    }

    // --- Leerer Graph ----------------------------------------------------

    @Test
    fun einLeererGraphNenntSeinenGrund() {
        assertEquals(ChartEmptyReason.NOTHING_RECORDED, TrackingUiState().emptyReason)
        assertEquals(
            ChartEmptyReason.NOTHING_SELECTED,
            TrackingUiState(trackedNames = listOf("A")).emptyReason
        )
        assertEquals(
            ChartEmptyReason.NOTHING_IN_RANGE,
            TrackingUiState(trackedNames = listOf("A"), visibleNames = setOf("A")).emptyReason
        )
        val series = listOf(ChartSeries("A", listOf(ChartPoint(0, 20.0))))
        assertEquals(
            null,
            TrackingUiState(trackedNames = listOf("A"), visibleNames = setOf("A"), series = series)
                .emptyReason
        )
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

    // --- Y-Achse ----------------------------------------------------------

    private fun scaleFor(vararg weights: Double) = verticalScaleFor(
        listOf(ChartSeries("A", weights.mapIndexed { index, kg -> ChartPoint(index.toLong(), kg) }))
    )

    @Test
    fun ueblicheGewichteStehenAufRundenStufen() {
        // 60 → 62,5 kg: Hilfslinien im Abstand von 1 kg, beide Punkte liegen dazwischen.
        val scale = scaleFor(60.0, 62.5)
        assertEquals(1.0, scale.lines[1] - scale.lines[0], 1e-9)
        assertTrue(scale.min < 60.0 && scale.max > 62.5)
    }

    /**
     * Ein vertipptes Gewicht darf den Graphen nicht sprengen: Früher hörten die Stufen bei 100
     * auf, und 60 → 6 000 000 kg ergab Zehntausende Hilfslinien samt Beschriftung.
     */
    @Test
    fun einRiesigerWertErgibtTrotzdemNurEineHandvollLinien() {
        val scale = scaleFor(60.0, 6_000_000.0)
        assertTrue("${scale.lines.size} Linien", scale.lines.size <= 9)
        assertTrue(scale.min <= 60.0 && scale.max >= 6_000_000.0)
    }

    @Test
    fun unterNullGibtEsKeineHilfslinien() {
        val scale = scaleFor(0.0, 5.0)
        assertEquals(0.0, scale.min, 0.0)
        assertTrue(scale.lines.all { it >= 0.0 })
        assertTrue(scale.max > 5.0)
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

    // --- Prozent -----------------------------------------------------------

    @Test
    fun prozentZaehlenAbDemErstenWertImZeitraum() {
        val line = ChartSeries(
            "Bankdrücken",
            listOf(ChartPoint(0, 80.0, isCarried = true), ChartPoint(1, 88.0), ChartPoint(2, 100.0))
        )
        val percent = toPercentSeries(listOf(line), emptySet()).single()
        // Der übernommene Stand am linken Rand ist der Bezug: Die Kurve beginnt bei 0 %.
        assertEquals(listOf(0.0, 10.0, 25.0), percent.points.map { it.percent })
        // Das Gewicht bleibt erhalten – die Beschriftung am Cursor braucht beides.
        assertEquals(listOf(80.0, 88.0, 100.0), percent.points.map { it.weightKg })
        assertEquals(listOf(0.0, 10.0, 25.0), percent.points.map { it.plotted })
    }

    @Test
    fun beiPfeilNachUntenIstEineSenkungEinPlus() {
        val line = ChartSeries("Latzug Unterstützung", listOf(ChartPoint(0, 20.0), ChartPoint(1, 15.0)))
        val percent = toPercentSeries(listOf(line), setOf("Latzug Unterstützung")).single()
        assertEquals(25.0, percent.points.last().percent!!, 1e-9)
    }

    @Test
    fun eineKurveAbNullKgHatKeinenBezugswert() {
        val zero = ChartSeries("Klimmzüge", listOf(ChartPoint(0, 0.0), ChartPoint(1, 5.0)))
        val other = ChartSeries("Rudern", listOf(ChartPoint(0, 50.0)))
        val percent = toPercentSeries(listOf(zero, other), emptySet())
        // Sie bleibt an ihrer Stelle stehen – die Farben der anderen rutschen nicht nach.
        assertEquals(listOf("Klimmzüge", "Rudern"), percent.map { it.name })
        assertTrue(percent[0].hasNoPercentBase)
        assertTrue(percent[0].points.isEmpty())
        assertFalse(percent[1].hasNoPercentBase)
    }

    @Test
    fun nurKurvenOhneBezugswertErgebenEinenEigenenHinweis() {
        val state = TrackingUiState(
            trackedNames = listOf("Klimmzüge"),
            visibleNames = setOf("Klimmzüge"),
            series = listOf(ChartSeries("Klimmzüge", emptyList(), hasNoPercentBase = true)),
            isPercent = true
        )
        assertEquals(ChartEmptyReason.NO_PERCENT_BASE, state.emptyReason)
    }

    @Test
    fun dieSkalaRichtetSichInProzentNachDenProzenten() {
        val line = ChartSeries("A", listOf(ChartPoint(0, 100.0, percent = 0.0), ChartPoint(1, 120.0, percent = 20.0)))
        val scale = verticalScaleFor(listOf(line))
        assertTrue(scale.min <= 0.0 && scale.max >= 20.0)
        assertTrue("Skala reicht bis ${scale.max}", scale.max < 100.0)
    }

    @Test
    fun prozentAchseMitEchtemMinus() {
        assertEquals("10", 10.0.toAxisNumber())
        assertEquals("0", 0.0.toAxisNumber())
        assertEquals("\u22122,5", (-2.5).toAxisNumber())
    }
}
