package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC

/** Ein Mittwoch. */
private val HEUTE: LocalDate = LocalDate.of(2026, 10, 7)
private val SEPTEMBER = ReviewPeriod.Month(YearMonth.of(2026, 9))

class ReviewTest {

    // --- Zeitraum ------------------------------------------------------------

    @Test
    fun monatUndJahrBlaetternUndWechseln() {
        assertEquals(ReviewPeriod.Month(YearMonth.of(2026, 8)), SEPTEMBER.previous())
        assertEquals(LocalDate.of(2026, 9, 30), SEPTEMBER.end)
        assertEquals(ReviewPeriod.Year(2026), SEPTEMBER.toggled(HEUTE))
        // Zurück ins laufende Jahr: der laufende Monat; in ein früheres: sein Dezember.
        assertEquals(ReviewPeriod.Month(YearMonth.of(2026, 10)), ReviewPeriod.Year(2026).toggled(HEUTE))
        assertEquals(ReviewPeriod.Month(YearMonth.of(2025, 12)), ReviewPeriod.Year(2025).toggled(HEUTE))
        assertTrue(LocalDate.of(2026, 12, 31) in ReviewPeriod.Year(2026))
        assertFalse(LocalDate.of(2026, 10, 1) in SEPTEMBER)
    }

    // --- Inhalt --------------------------------------------------------------

    @Test
    fun ohneDatenIstDerRueckblickLeer() {
        val review = review(SEPTEMBER, emptyList(), emptyList(), emptyList(), emptyList(), 3, emptyList(), HEUTE, ZONE)
        assertTrue(review.isEmpty)
        assertNull(review.trainingMinutes)
        assertNull(review.topWeekday)
        assertEquals(0, review.bestStreak)
        // Der Kalender steht trotzdem: fünf Wochen, Montag 31. August bis Sonntag 4. Oktober.
        assertEquals(5, review.weeks.size)
        assertNull(review.weeks.first().days.first())
        assertEquals(LocalDate.of(2026, 9, 1), review.weeks.first().days[1]!!.date)
    }

    @Test
    fun trainingsDauerUndWochentagZaehlenNurImZeitraum() {
        val sessions = listOf(
            session(LocalDate.of(2026, 8, 31), minutes = 50), // Montag davor
            session(LocalDate.of(2026, 9, 1), minutes = 60), // Dienstag
            session(LocalDate.of(2026, 9, 8), minutes = 45), // Dienstag
            session(LocalDate.of(2026, 9, 10)), // Donnerstag, ohne Dauer
            session(LocalDate.of(2026, 10, 1), minutes = 70) // danach
        )
        val review = review(SEPTEMBER, sessions, emptyList(), emptyList(), emptyList(), 3, emptyList(), HEUTE, ZONE)
        assertEquals(3, review.sessions)
        assertEquals(105, review.trainingMinutes)
        assertEquals(DayOfWeek.TUESDAY, review.topWeekday)
        assertEquals(2, review.topWeekdayCount)
        assertEquals(1, review.weeks[1].days[1]!!.count)
        assertFalse(review.isEmpty)
    }

    @Test
    fun derZuwachsRechnetVomStandZuBeginn() {
        val logs = listOf(
            weight("Bankdrücken", 60.0, LocalDate.of(2026, 8, 20)), // Stand zu Beginn
            weight("Bankdrücken", 62.5, LocalDate.of(2026, 9, 5)),
            weight("Bankdrücken", 65.0, LocalDate.of(2026, 9, 20)), // +5
            weight("Kniebeuge", 80.0, LocalDate.of(2026, 9, 2)), // neu im September
            weight("Kniebeuge", 82.5, LocalDate.of(2026, 9, 25)), // +2,5
            weight("Curls", 15.0, LocalDate.of(2026, 8, 1)),
            weight("Curls", 12.5, LocalDate.of(2026, 9, 3)), // im Minus: fehlt
            weight("Rudern", 50.0, LocalDate.of(2026, 9, 1)),
            weight("Rudern", 70.0, LocalDate.of(2026, 10, 2)) // erst danach
        )
        val review = review(SEPTEMBER, emptyList(), logs, emptyList(), emptyList(), 3, emptyList(), HEUTE, ZONE)
        assertEquals(7.5, review.gainKg, 1e-9)
        assertEquals("Bankdrücken", review.topGain!!.name)
        assertEquals(5.0, review.topGain!!.gainKg, 1e-9)
    }

    @Test
    fun cardioUndPfeilNachUntenWieInDerStatistik() {
        val logs = listOf(
            weight("Klimmzug", 40.0, LocalDate.of(2026, 9, 1)),
            weight("Klimmzug", 30.0, LocalDate.of(2026, 9, 20)),
            weight("Ergometer", 10.0, LocalDate.of(2026, 9, 1)),
            weight("Ergometer", 50.0, LocalDate.of(2026, 9, 20))
        )
        val definitions = listOf(
            ExerciseDefinition(name = "Klimmzug", progressionDown = true),
            ExerciseDefinition(name = "Ergometer", kind = ExerciseKind.CARDIO)
        )
        val cardio = listOf(
            cardio(LocalDate.of(2026, 9, 2), minutes = 30.0, km = 5.0),
            cardio(LocalDate.of(2026, 9, 9), minutes = 45.0),
            cardio(LocalDate.of(2026, 10, 2), minutes = 20.0, km = 3.0)
        )
        val review = review(SEPTEMBER, emptyList(), logs, cardio, definitions, 3, emptyList(), HEUTE, ZONE)
        assertEquals(10.0, review.gainKg, 1e-9)
        assertEquals("Klimmzug", review.topGain!!.name)
        assertEquals(75.0, review.cardioMinutes, 0.0)
        assertEquals(5.0, review.cardioKm, 0.0)
        assertEquals(2, review.cardioSessions)
    }

    @Test
    fun dieBesteSerieZaehltWochenDieHineinreichen() {
        // Ziel 1: die Woche ab 31. August (reicht hinein), dann 7., 14. – Pause – 28. September.
        val sessions = listOf(
            LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 16),
            LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 24)
        ).map { session(it) }
        val review = review(SEPTEMBER, sessions, emptyList(), emptyList(), emptyList(), 1, emptyList(), HEUTE, ZONE)
        assertEquals(3, review.bestStreak)
    }

    @Test
    fun meilensteineUndKalenderDesJahres() {
        val reached = listOf(
            ReachedMilestone(Milestone(MilestoneKind.SESSIONS, 10), LocalDate.of(2026, 3, 1)),
            ReachedMilestone(Milestone(MilestoneKind.SESSIONS, 25), LocalDate.of(2026, 9, 1)),
            ReachedMilestone(Milestone(MilestoneKind.FULL_ROUNDS, 1), LocalDate.of(2025, 12, 30))
        )
        val year = review(ReviewPeriod.Year(2026), emptyList(), emptyList(), emptyList(), emptyList(), 3, reached, HEUTE, ZONE)
        assertEquals(listOf(10, 25), year.milestones.map { it.milestone.threshold })
        // Bis heute, nicht bis Silvester; Monatsnamen über den Wochen, in denen ein Monat beginnt.
        assertEquals(HEUTE, year.weeks.last().days.filterNotNull().last().date)
        assertEquals(Month.JANUARY, year.weeks.first().monthLabel)
        assertEquals(10, year.weeks.count { it.monthLabel != null })
        // Ein Monat hat keine Monatsnamen.
        val month = review(SEPTEMBER, emptyList(), emptyList(), emptyList(), emptyList(), 3, reached, HEUTE, ZONE)
        assertTrue(month.weeks.all { it.monthLabel == null })
        assertEquals(listOf(25), month.milestones.map { it.milestone.threshold })
    }

    // --- Hilfen --------------------------------------------------------------

    private fun at(date: LocalDate, hour: Int = 18) = date.atTime(hour, 0).toInstant(ZONE).toEpochMilli()

    private fun session(date: LocalDate, minutes: Int? = null) = WorkoutSession(
        dayId = 1,
        completedAt = at(date),
        startedAt = minutes?.let { at(date) - it * 60_000L }
    )

    private fun weight(name: String, kg: Double, date: LocalDate) =
        WeightLog(exerciseName = name, weightKg = kg, recordedAt = at(date))

    private fun cardio(date: LocalDate, minutes: Double? = null, km: Double? = null) = CardioLog(
        exerciseName = "Laufband",
        dayId = 1,
        performedAt = at(date),
        durationMin = minutes,
        distanceKm = km
    )
}
