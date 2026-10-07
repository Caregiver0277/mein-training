package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC

/** Ein Mittwoch. */
private val HEUTE: LocalDate = LocalDate.of(2026, 10, 7)

class MilestonesTest {

    // --- Leitern -------------------------------------------------------------

    @Test
    fun nachDenFestenStufenGehtEsInSchrittenWeiter() {
        assertEquals(listOf(10, 25, 50, 100, 150, 200, 250, 300), (0..7).map { MilestoneKind.SESSIONS.threshold(it) })
        assertEquals(listOf(50, 60, 70, 80, 90, 100, 125, 150), (0..7).map { MilestoneKind.WEIGHT.threshold(it) })
        assertEquals(listOf(4, 8, 12, 26, 52, 104), (0..5).map { MilestoneKind.GOAL_STREAK.threshold(it) })
        assertEquals(40, MilestoneKind.WEIGHT.previousThreshold(0))
        assertEquals(100, MilestoneKind.WEIGHT.previousThreshold(6))
    }

    @Test
    fun kennungenBleibenStabil() {
        assertEquals("trainings:50", Milestone(MilestoneKind.SESSIONS, 50).id)
        assertEquals("gewicht:100:Rudern: eng", Milestone(MilestoneKind.WEIGHT, 100, "Rudern: eng").id)
    }

    // --- Trainings -----------------------------------------------------------

    @Test
    fun ohneDatenIstNichtsErreichtUndDieErsteStufeKommt() {
        val overview = milestones(data(), HEUTE, ZONE)
        assertTrue(overview.reached.isEmpty())
        val next = overview.next.first { it.milestone.kind == MilestoneKind.SESSIONS }
        assertEquals(10, next.milestone.threshold)
        assertEquals(0.0, next.fraction, 0.0)
        // Ohne Cardio-Einheit auch keine Cardio-Meilensteine in Sicht.
        assertTrue(overview.next.none { it.milestone.kind == MilestoneKind.CARDIO_KM })
    }

    @Test
    fun dasZehnteTrainingIstDerMeilensteinMitSeinemDatum() {
        // Zwölf Trainings, jeden zweiten Tag; das zehnte liegt 4 Tage zurück.
        val sessions = (22L downTo 0L step 2).map { session(1, it) }
        val overview = milestones(data(sessions = sessions), HEUTE, ZONE)
        val reached = overview.reached.single { it.milestone.kind == MilestoneKind.SESSIONS }
        assertEquals(10, reached.milestone.threshold)
        assertEquals(HEUTE.minusDays(4), reached.date)
        val next = overview.next.first { it.milestone.kind == MilestoneKind.SESSIONS }
        assertEquals(25, next.milestone.threshold)
        assertEquals(12.0 / 25, next.fraction, 1e-9)
    }

    // --- Ziel-Serie ----------------------------------------------------------

    @Test
    fun dieSerieIstMitDemZielTrainingDerVierteWocheErreicht() {
        // Ziel 2: Vier Wochen in Folge je zwei Trainings, Montag und Mittwoch. Die vierte Woche ist
        // die laufende, ihr zweites Training heute.
        val sessions = (0L..3L).flatMap { weeks ->
            val monday = HEUTE.minusWeeks(weeks).minusDays(2)
            listOf(sessionOn(1, monday), sessionOn(2, monday.plusDays(2)))
        }
        val overview = milestones(data(sessions = sessions, weeklyGoal = 2), HEUTE, ZONE)
        val streak = overview.reached.single { it.milestone.kind == MilestoneKind.GOAL_STREAK }
        assertEquals(4, streak.milestone.threshold)
        assertEquals(HEUTE, streak.date)
        // Mit Ziel 3 erreicht keine Woche das Ziel.
        val strict = milestones(data(sessions = sessions, weeklyGoal = 3), HEUTE, ZONE)
        assertTrue(strict.reached.none { it.milestone.kind == MilestoneKind.GOAL_STREAK })
    }

    @Test
    fun eineLueckeBeginntDieSerieNeu() {
        // Drei Wochen, eine Pause, wieder drei Wochen: nie vier in Folge.
        val weeks = listOf(0L, 1L, 2L, 4L, 5L, 6L)
        val sessions = weeks.map { sessionOn(1, HEUTE.minusWeeks(it)) }
        val overview = milestones(data(sessions = sessions, weeklyGoal = 1), HEUTE, ZONE)
        assertTrue(overview.reached.none { it.milestone.kind == MilestoneKind.GOAL_STREAK })
        val next = overview.next.first { it.milestone.kind == MilestoneKind.GOAL_STREAK }
        assertEquals(4, next.milestone.threshold)
        assertEquals(3.0, next.current, 0.0)
    }

    // --- Runden --------------------------------------------------------------

    @Test
    fun eineGeradeVolleRundeZaehltSofort() {
        // Drei Tage: gestern Tag 1 und 2, heute Tag 3 – die Runde bleibt bis Mitternacht stehen,
        // der Meilenstein ist trotzdem heute erreicht.
        val sessions = listOf(session(1, 1), session(2, 1), session(3, 0))
        val overview = milestones(data(sessions = sessions, dayCount = 3), HEUTE, ZONE)
        val round = overview.reached.single { it.milestone.kind == MilestoneKind.FULL_ROUNDS }
        assertEquals(1, round.milestone.threshold)
        assertEquals(HEUTE, round.date)
    }

    @Test
    fun eineAbgebrocheneRundeIstKeineVolle() {
        val sessions = listOf(session(1, 2), session(2, 1))
        val cut = millis(1) + 1
        val overview = milestones(
            data(sessions = sessions + session(1, 0), dayCount = 3, cuts = listOf(cut)),
            HEUTE,
            ZONE
        )
        assertTrue(overview.reached.none { it.milestone.kind == MilestoneKind.FULL_ROUNDS })
    }

    // --- Gewichte ------------------------------------------------------------

    @Test
    fun gewichtsStufenZaehlenErstUeberDemStartgewicht() {
        val logs = listOf(weight("Bankdrücken", 55.0, 30), weight("Bankdrücken", 62.5, 20), weight("Bankdrücken", 70.0, 0))
        val overview = milestones(data(weightLogs = logs, definitions = listOf(strength("Bankdrücken"))), HEUTE, ZONE)
        val weights = overview.reached.filter { it.milestone.kind == MilestoneKind.WEIGHT }
        // 50 war schon beim Anlegen da – nur 60 und 70 sind erreicht.
        assertEquals(listOf(70, 60), weights.map { it.milestone.threshold })
        assertEquals(HEUTE, weights[0].date)
        assertEquals(HEUTE.minusDays(20), weights[1].date)
        val next = overview.next.single { it.milestone.kind == MilestoneKind.WEIGHT }
        assertEquals(80, next.milestone.threshold)
        assertEquals("Bankdrücken", next.milestone.exercise)
        assertEquals(0.0, next.fraction, 1e-9)
    }

    @Test
    fun einMalErreichtBleibtErreicht() {
        // 95 → 100 → zurück auf 90 (Deload): die 100 bleiben.
        val logs = listOf(weight("Kniebeuge", 95.0, 20), weight("Kniebeuge", 100.0, 10), weight("Kniebeuge", 90.0, 0))
        val overview = milestones(data(weightLogs = logs, definitions = listOf(strength("Kniebeuge"))), HEUTE, ZONE)
        assertEquals(
            listOf(100),
            overview.reached.filter { it.milestone.kind == MilestoneKind.WEIGHT }.map { it.milestone.threshold }
        )
        // Als Nächstes die 125 – bei 90 kg noch außer Sicht.
        assertTrue(overview.next.none { it.milestone.kind == MilestoneKind.WEIGHT })
    }

    @Test
    fun pfeilNachUntenUndCardioHabenKeineGewichtsMeilensteine() {
        val logs = listOf(
            weight("Klimmzug", 40.0, 20), weight("Klimmzug", 60.0, 10),
            weight("Rudergerät", 40.0, 20), weight("Rudergerät", 60.0, 10)
        )
        val definitions = listOf(
            ExerciseDefinition(name = "Klimmzug", weightKg = 60.0, progressionDown = true),
            ExerciseDefinition(name = "Rudergerät", weightKg = 60.0, kind = ExerciseKind.CARDIO)
        )
        val overview = milestones(data(weightLogs = logs, definitions = definitions), HEUTE, ZONE)
        assertTrue(overview.reached.none { it.milestone.kind == MilestoneKind.WEIGHT })
    }

    @Test
    fun alsNaechstesKommenDieGewichteMitDemKleinstenRest() {
        val logs = listOf(
            weight("A", 41.0, 9), weight("A", 45.0, 0),
            weight("B", 41.0, 9), weight("B", 49.0, 0),
            weight("C", 41.0, 9), weight("C", 42.0, 0),
            weight("D", 41.0, 9), weight("D", 47.0, 0),
            weight("E", 20.0, 9), weight("E", 25.0, 0)
        )
        val definitions = listOf("A", "B", "C", "D", "E").map { strength(it) }
        val overview = milestones(data(weightLogs = logs, definitions = definitions), HEUTE, ZONE)
        assertEquals(
            listOf("B", "D", "A"),
            overview.next.filter { it.milestone.kind == MilestoneKind.WEIGHT }.map { it.milestone.exercise }
        )
        assertEquals(0.9, overview.next.first { it.milestone.exercise == "B" }.fraction, 1e-9)
    }

    // --- Gesamtzuwachs -------------------------------------------------------

    @Test
    fun derGesamtzuwachsSummiertDieUebungen() {
        val logs = listOf(
            weight("Bankdrücken", 60.0, 30),
            weight("Kniebeuge", 80.0, 30),
            weight("Bankdrücken", 75.0, 20), // +15
            weight("Kniebeuge", 92.5, 10), // +12,5 → 27,5
            weight("Kniebeuge", 70.0, 5), // im Minus zählt 0 → 15
            weight("Kniebeuge", 95.0, 0) // +15 → 30
        )
        val definitions = listOf(strength("Bankdrücken"), strength("Kniebeuge"))
        val overview = milestones(data(weightLogs = logs, definitions = definitions), HEUTE, ZONE)
        val gain = overview.reached.single { it.milestone.kind == MilestoneKind.TOTAL_GAIN }
        assertEquals(25, gain.milestone.threshold)
        assertEquals(HEUTE.minusDays(10), gain.date)
        val next = overview.next.single { it.milestone.kind == MilestoneKind.TOTAL_GAIN }
        assertEquals(50, next.milestone.threshold)
        assertEquals(30.0, next.current, 1e-9)
    }

    @Test
    fun beiPfeilNachUntenZaehltDasGesunkeneAlsZuwachs() {
        val logs = listOf(weight("Klimmzug", 50.0, 20), weight("Klimmzug", 20.0, 0))
        val definitions = listOf(ExerciseDefinition(name = "Klimmzug", progressionDown = true))
        val overview = milestones(data(weightLogs = logs, definitions = definitions), HEUTE, ZONE)
        assertEquals(
            listOf(25),
            overview.reached.filter { it.milestone.kind == MilestoneKind.TOTAL_GAIN }.map { it.milestone.threshold }
        )
    }

    // --- Cardio --------------------------------------------------------------

    @Test
    fun cardioSummiertKilometerUndMinuten() {
        val logs = listOf(
            cardio(10, minutes = 60.0, km = 6.0),
            cardio(5, minutes = 45.0),
            cardio(0, minutes = 30.0, km = 5.0)
        )
        val overview = milestones(data(cardioLogs = logs), HEUTE, ZONE)
        val km = overview.reached.single { it.milestone.kind == MilestoneKind.CARDIO_KM }
        assertEquals(10, km.milestone.threshold)
        assertEquals(HEUTE, km.date)
        val minutes = overview.reached.single { it.milestone.kind == MilestoneKind.CARDIO_MINUTES }
        assertEquals(100, minutes.milestone.threshold)
        assertEquals(HEUTE.minusDays(5), minutes.date)
        assertEquals(500, overview.next.single { it.milestone.kind == MilestoneKind.CARDIO_MINUTES }.milestone.threshold)
    }

    // --- Feiern --------------------------------------------------------------

    @Test
    fun gefeiertWirdNurNeuesVonHeute() {
        val reached = listOf(
            ReachedMilestone(Milestone(MilestoneKind.SESSIONS, 10), HEUTE),
            ReachedMilestone(Milestone(MilestoneKind.CARDIO_KM, 10), HEUTE.minusDays(3)),
            ReachedMilestone(Milestone(MilestoneKind.FULL_ROUNDS, 1), HEUTE)
        )
        val fresh = setOf("trainings:10", "km:10")
        assertEquals(listOf(Milestone(MilestoneKind.SESSIONS, 10)), milestonesToCelebrate(reached, fresh, HEUTE))
    }

    @Test
    fun umbenennenUndLoeschenTreffenNurGewichteDerUebung() {
        val ids = setOf("gewicht:100:Bank", "gewicht:60:Bankdrücken", "trainings:10")
        assertEquals(
            setOf("gewicht:100:Bankdrücken", "gewicht:60:Bankdrücken", "trainings:10"),
            renameMilestoneIds(ids, "Bank", "Bankdrücken")
        )
        assertEquals(setOf("trainings:10"), dropMilestoneIds(ids, setOf("Bank", "Bankdrücken")))
        assertFalse("gewicht:60:Bankdrücken" in dropMilestoneIds(ids, setOf("Bankdrücken")))
    }

    // --- Hilfen --------------------------------------------------------------

    private fun data(
        sessions: List<WorkoutSession> = emptyList(),
        weightLogs: List<WeightLog> = emptyList(),
        cardioLogs: List<CardioLog> = emptyList(),
        definitions: List<ExerciseDefinition> = emptyList(),
        dayCount: Int = 4,
        cuts: List<Long> = emptyList(),
        weeklyGoal: Int = DEFAULT_WEEKLY_GOAL
    ) = MilestoneData(sessions, weightLogs, cardioLogs, definitions, dayCount, cuts, weeklyGoal)

    private fun strength(name: String) = ExerciseDefinition(name = name)

    private fun session(dayId: Int, daysAgo: Long) = WorkoutSession(dayId = dayId, completedAt = millis(daysAgo))

    private fun sessionOn(dayId: Int, date: LocalDate) =
        WorkoutSession(dayId = dayId, completedAt = date.atTime(18, 0).toInstant(ZONE).toEpochMilli())

    private fun weight(name: String, kg: Double, daysAgo: Long) =
        WeightLog(exerciseName = name, weightKg = kg, recordedAt = millis(daysAgo))

    private fun cardio(daysAgo: Long, minutes: Double? = null, km: Double? = null) = CardioLog(
        exerciseName = "Laufband",
        dayId = 1,
        performedAt = millis(daysAgo),
        durationMin = minutes,
        distanceKm = km
    )

    private fun millis(daysAgo: Long) =
        HEUTE.minusDays(daysAgo).atTime(18, 0).toInstant(ZONE).toEpochMilli()
}
