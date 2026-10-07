package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Ein Sonntag – so lässt sich das Wochenende sauber gegen die Woche abgrenzen. */
private val TODAY: LocalDate = LocalDate.of(2026, 8, 2)

class StatisticsTest {

    @Test
    fun cardioZaehltNichtBeiDenGewichten() {
        val definitions = listOf(
            ExerciseDefinition(name = "Kreuzheben", weightKg = 120.0),
            // Ein Gewicht aus der Zeit als Kraftübung – trotzdem außen vor.
            ExerciseDefinition(name = "Rudergerät", weightKg = 200.0, kind = ExerciseKind.CARDIO),
            ExerciseDefinition(name = "Curls"),
            ExerciseDefinition(name = "Bankdrücken", weightKg = 80.0)
        )
        assertEquals(
            mapOf("Kreuzheben" to 120.0),
            currentStrengthWeights(definitions, setOf("Kreuzheben", "Rudergerät", "Curls"))
        )
    }

    // --- Häufigkeit --------------------------------------------------------

    @Test
    fun ohneTrainingGibtEsKeineFrequenz() {
        assertEquals(0.0, sessionsPerWeek(emptyList(), TODAY), 0.0)
        assertEquals(0, currentWeeklyStreak(emptyList(), TODAY, goal = 1))
        assertEquals(0, longestWeeklyStreak(emptyList(), goal = 1))
    }

    @Test
    fun dreiTrainingsInSiebenTagenSindDreiProWoche() {
        val dates = listOf(TODAY.minusDays(6), TODAY.minusDays(4), TODAY.minusDays(2))
        assertEquals(3.0, sessionsPerWeek(dates, TODAY), 0.01)
    }

    @Test
    fun dieBilanzZaehltDieLetztenTageSamtHeute() {
        val dates = listOf(TODAY, TODAY.minusDays(6), TODAY.minusDays(7), TODAY.minusDays(29))
        assertEquals(2, sessionsInLastDays(dates, TODAY, 7))
        assertEquals(4, sessionsInLastDays(dates, TODAY, 30))
    }

    @Test
    fun eintraegeAusDerZukunftZaehlenInKeinerSpanne() {
        // Durch Zeitzonenwechsel oder eine eingelesene Sicherung nach heute datiert.
        val dates = listOf(TODAY.plusDays(1), TODAY)
        assertEquals(1, sessionsInLastDays(dates, TODAY, 7))
    }

    @Test
    fun dasErsteTrainingWirdNichtAufSiebenHochgerechnet() {
        // Ein Training am ersten Tag: über eine Woche gerechnet ist das eines, nicht sieben.
        assertEquals(1.0, sessionsPerWeek(listOf(TODAY), TODAY), 0.01)
    }

    @Test
    fun ueberLaengereZeitraeumeZaehltDerEchteSchnitt() {
        // Acht Trainings im Wochenabstand: erstes vor 49 Tagen, Spanne also 50 Tage.
        val dates = (0L until 8L).map { TODAY.minusDays(it * 7) }
        assertEquals(8 * 7.0 / 50, sessionsPerWeek(dates, TODAY), 0.001)
    }

    @Test
    fun serieZaehltZusammenhaengendeWochen() {
        // Je ein Training in dieser und den beiden Vorwochen.
        val dates = listOf(TODAY.minusDays(1), TODAY.minusDays(8), TODAY.minusDays(15))
        assertEquals(3, currentWeeklyStreak(dates, TODAY, goal = 1))
    }

    @Test
    fun eineAusgelasseneWocheBeendetDieSerie() {
        val dates = listOf(TODAY.minusDays(1), TODAY.minusDays(22))
        assertEquals(1, currentWeeklyStreak(dates, TODAY, goal = 1))
    }

    @Test
    fun dieNochOffeneWocheBrichtDieSerieNicht() {
        // Letztes Training in der Vorwoche, diese Woche noch nichts: Serie bleibt bei 1.
        val dates = listOf(TODAY.minusDays(3))
        val monday = TODAY.plusDays(1)
        assertEquals(1, currentWeeklyStreak(dates, monday, goal = 1))
    }

    @Test
    fun laengsteSerieUeberlebtSpaetereLuecken() {
        val dates = listOf(
            TODAY.minusWeeks(9), TODAY.minusWeeks(8), TODAY.minusWeeks(7),
            // Lücke
            TODAY.minusWeeks(1)
        )
        assertEquals(3, longestWeeklyStreak(dates, goal = 1))
    }

    // --- Wochenziel ---------------------------------------------------------

    /** [count] Trainings in der Woche, die [weeksBack] Wochen vor der von [TODAY] liegt. */
    private fun week(weeksBack: Long, count: Int): List<LocalDate> =
        List(count) { TODAY.minusWeeks(weeksBack).minusDays(it.toLong()) }

    @Test
    fun dieZielSerieZaehltNurWochenMitErreichtemZiel() {
        // Diese Woche 3, Vorwoche 4, davor 2 – bei Ziel 3 sind es zwei Wochen.
        val dates = week(0, 3) + week(1, 4) + week(2, 2) + week(3, 3)
        assertEquals(2, currentWeeklyStreak(dates, TODAY, goal = 3))
        assertEquals(4, currentWeeklyStreak(dates, TODAY, goal = 2))
    }

    @Test
    fun dieOffeneWocheBrichtDieZielSerieNichtSolangeDasZielFehlt() {
        // Diese Woche erst 1 von 3: zählt nicht mit, bricht aber auch nichts.
        val dates = week(0, 1) + week(1, 3) + week(2, 3)
        assertEquals(2, currentWeeklyStreak(dates, TODAY, goal = 3))
        // Am Montag darauf ist sie vorbei – und hat das Ziel verfehlt.
        assertEquals(0, currentWeeklyStreak(dates, TODAY.plusDays(1), goal = 3))
    }

    @Test
    fun dieLaengsteZielSerieMisstAmAktuellenZiel() {
        val dates = week(10, 3) + week(9, 3) + week(8, 3) + week(7, 2) + week(1, 3)
        assertEquals(3, longestWeeklyStreak(dates, goal = 3))
        assertEquals(4, longestWeeklyStreak(dates, goal = 2))
        assertEquals(0, longestWeeklyStreak(dates, goal = 4))
    }

    @Test
    fun zweiTrainingsAmSelbenTagZaehlenBeideFuersZiel() {
        val dates = listOf(TODAY, TODAY)
        assertEquals(1, currentWeeklyStreak(dates, TODAY, goal = 2))
    }

    @Test
    fun dieWochenbalkenReichenZwoelfWochenZurueckBisZurLaufenden() {
        val dates = week(0, 2) + week(3, 1) + week(12, 5) + listOf(TODAY.plusDays(1))
        val counts = weeklyCounts(dates, TODAY)
        assertEquals(GOAL_WEEKS, counts.size)
        assertEquals(TODAY.with(DayOfWeek.MONDAY), counts.last().weekStart)
        assertEquals(TODAY.with(DayOfWeek.MONDAY).minusWeeks(11), counts.first().weekStart)
        assertEquals(2, counts.last().count)
        assertEquals(1, counts[GOAL_WEEKS - 1 - 3].count)
        // Die Woche vor zwölf Wochen liegt außerhalb, die Lücken stehen mit 0 darin.
        assertEquals(3, counts.sumOf { it.count })
        assertEquals(0, counts[GOAL_WEEKS - 2].count)
    }

    // --- Cardio -------------------------------------------------------------

    private fun cardio(date: LocalDate, minutes: Double?, km: Double?) = CardioLog(
        exerciseName = "Laufband",
        dayId = 1,
        performedAt = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        durationMin = minutes,
        distanceKm = km
    )

    @Test
    fun ohneCardioGibtEsKeineCardioBilanz() {
        assertNull(cardioTotals(emptyList(), TODAY))
    }

    @Test
    fun cardioWirdJeWocheUndSeitBeginnSummiert() {
        val logs = listOf(
            cardio(TODAY, 30.0, 5.0),
            cardio(TODAY.minusDays(2), 20.0, null),
            cardio(TODAY.minusWeeks(2), null, 3.5),
            // Vor dem Fenster der zwölf Wochen: nur in den Summen.
            cardio(TODAY.minusWeeks(20), 45.0, 8.0)
        )
        val totals = cardioTotals(logs, TODAY)!!
        assertEquals(GOAL_WEEKS, totals.weeks.size)
        assertEquals(50.0, totals.weeks.last().minutes, 0.001)
        assertEquals(5.0, totals.weeks.last().km, 0.001)
        assertEquals(3.5, totals.weeks[GOAL_WEEKS - 3].km, 0.001)
        assertEquals(0.0, totals.weeks[GOAL_WEEKS - 2].minutes, 0.001)
        assertEquals(95.0, totals.totalMinutes, 0.001)
        assertEquals(16.5, totals.totalKm, 0.001)
        assertEquals(4, totals.sessions)
    }

    // --- Verteilungen ------------------------------------------------------

    @Test
    fun wochentageBeginnenMitMontag() {
        val monday = TODAY.plusDays(1)
        val counts = weekdayDistribution(listOf(monday, monday, TODAY))
        assertEquals(2, counts[DayOfWeek.MONDAY.ordinal])
        assertEquals(1, counts[DayOfWeek.SUNDAY.ordinal])
        assertEquals(0, counts[DayOfWeek.WEDNESDAY.ordinal])
    }

    @Test
    fun typischeZeitLiegtZwischenDenEingaben() {
        val time = typicalTimeOfDay(listOf(LocalTime.of(18, 0), LocalTime.of(20, 0)))
        assertEquals(LocalTime.of(19, 0), time?.withSecond(0)?.withNano(0))
    }

    @Test
    fun typischeZeitLaeuftUeberMitternacht() {
        // Der naive Mittelwert läge bei 12:00 – richtig ist Mitternacht.
        val time = typicalTimeOfDay(listOf(LocalTime.of(23, 50), LocalTime.of(0, 10)))
        assertEquals(0, time?.hour)
    }

    @Test
    fun ohneZeitenGibtEsKeineTypischeZeit() {
        assertNull(typicalTimeOfDay(emptyList()))
    }

    // --- Fortschritt -------------------------------------------------------

    @Test
    fun zuwachsWirdVomErstenBisZumLetztenEintragGerechnet() {
        val gains = exerciseGains(
            listOf("Bank" to 50.0, "Bank" to 55.0, "Bank" to 60.0, "Curl" to 20.0)
        )
        assertEquals(1, gains.size)
        assertEquals(10.0, gains.first().gainKg, 0.0)
        assertEquals(20.0, gains.first().gainPercent, 0.01)
    }

    @Test
    fun unveraenderteUebungenTauchenNichtAlsZuwachsAuf() {
        assertTrue(exerciseGains(listOf("Bank" to 50.0, "Bank" to 50.0)).isEmpty())
    }

    @Test
    fun groessterZuwachsStehtVorn() {
        val gains = exerciseGains(
            listOf("Bank" to 50.0, "Bank" to 55.0, "Squat" to 60.0, "Squat" to 90.0)
        )
        assertEquals("Squat", gains.first().name)
    }

    /**
     * Bei einer Übung mit Pfeil nach unten ist die Last Unterstützung: Von 40 auf 25 kg sind
     * 15 kg Fortschritt – und kein Rückgang, der aus der Rechnung fällt.
     */
    @Test
    fun wenigerUnterstuetzungZaehltAlsZuwachs() {
        val gains = exerciseGains(
            listOf("Klimmzug" to 40.0, "Klimmzug" to 30.0, "Klimmzug" to 25.0),
            decreasing = setOf("Klimmzug")
        )
        assertEquals(15.0, gains.single().gainKg, 0.0)
    }

    @Test
    fun mehrUnterstuetzungIstKeinZuwachs() {
        val gains = exerciseGains(
            listOf("Klimmzug" to 25.0, "Klimmzug" to 30.0),
            decreasing = setOf("Klimmzug")
        )
        assertTrue(gains.isEmpty())
    }

    @Test
    fun dieRichtungGiltNurFuerDieGenannteUebung() {
        val gains = exerciseGains(
            listOf("Bank" to 50.0, "Bank" to 55.0, "Klimmzug" to 40.0, "Klimmzug" to 20.0),
            decreasing = setOf("Klimmzug")
        )
        assertEquals(listOf("Klimmzug", "Bank"), gains.map { it.name })
        assertEquals(25.0, gains.sumOf { it.gainKg }, 0.0)
    }

    // --- Festgefahren -----------------------------------------------------

    /** Mittag des Tages [daysAgo] Tage vor heute, als Zeitstempel. */
    private fun at(daysAgo: Long): Long =
        TODAY.minusDays(daysAgo).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Trainings an Tag [dayId], eines je angegebenem Abstand zu heute. */
    private fun sessions(dayId: Int, vararg daysAgo: Long) = daysAgo.map { dayId to at(it) }

    private fun stagnating(
        lastChanged: Map<String, Long>,
        currentWeights: Map<String, Double>,
        sessions: List<Pair<Int, Long>>,
        plannedDays: Map<String, Set<Int>> = currentWeights.mapValues { setOf(1) }
    ) = stagnatingExercises(lastChanged, currentWeights, plannedDays, sessions, TODAY)

    @Test
    fun stagnationGreiftErstNachSechsTrainings() {
        val result = stagnating(
            lastChanged = mapOf("Alt" to at(43), "Frisch" to at(10)),
            currentWeights = mapOf("Alt" to 60.0, "Frisch" to 20.0),
            sessions = sessions(1, 42, 35, 28, 21, 14, 9, 2)
        )
        assertEquals(listOf("Alt"), result.map { it.name })
        assertEquals(7, result.single().sinceSessions)
        assertEquals(43L, result.single().sinceDays)
    }

    @Test
    fun eineTrainingspauseMachtNichtsFestgefahren() {
        // Seit 60 Tagen unverändert, aber nur zweimal trainiert – dazwischen war Pause.
        val result = stagnating(
            lastChanged = mapOf("Bank" to at(60)),
            currentWeights = mapOf("Bank" to 60.0),
            sessions = sessions(1, 59, 3)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun gezaehltWirdNurAnDenTagenDerUebung() {
        // Sechs Trainings seit der Änderung, aber nur zwei davon an Tag 2, wo die Übung steht.
        val result = stagnating(
            lastChanged = mapOf("Kreuzheben" to at(30)),
            currentWeights = mapOf("Kreuzheben" to 100.0),
            sessions = sessions(1, 28, 21, 14, 7) + sessions(2, 25, 11),
            plannedDays = mapOf("Kreuzheben" to setOf(2))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun uebungenOhneGewichtSindNieFestgefahren() {
        val result = stagnating(
            lastChanged = mapOf("Nordic curl" to at(100), "Dips" to at(100)),
            currentWeights = mapOf("Nordic curl" to 0.0),
            sessions = sessions(1, 90, 80, 70, 60, 50, 40, 30)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun amLaengstenFestgefahrenSteht() {
        val result = stagnating(
            lastChanged = mapOf("Kurz" to at(20), "Lang" to at(50)),
            currentWeights = mapOf("Kurz" to 20.0, "Lang" to 40.0),
            sessions = sessions(1, 45, 40, 35, 30, 19, 15, 10, 5, 4, 3, 2, 1)
        )
        assertEquals(listOf("Lang", "Kurz"), result.map { it.name })
        assertEquals(listOf(12, 8), result.map { it.sinceSessions })
    }
}
