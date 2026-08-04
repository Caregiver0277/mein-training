package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Ein Sonntag – so lässt sich das Wochenende sauber gegen die Woche abgrenzen. */
private val TODAY: LocalDate = LocalDate.of(2026, 8, 2)

class StatisticsTest {

    // --- Häufigkeit --------------------------------------------------------

    @Test
    fun ohneTrainingGibtEsKeineFrequenz() {
        assertEquals(0.0, sessionsPerWeek(emptyList(), TODAY), 0.0)
        assertEquals(0, currentWeeklyStreak(emptyList(), TODAY))
        assertEquals(0, longestWeeklyStreak(emptyList()))
    }

    @Test
    fun dreiTrainingsInSiebenTagenSindDreiProWoche() {
        val dates = listOf(TODAY.minusDays(6), TODAY.minusDays(4), TODAY.minusDays(2))
        assertEquals(3.0, sessionsPerWeek(dates, TODAY), 0.01)
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
        assertEquals(3, currentWeeklyStreak(dates, TODAY))
    }

    @Test
    fun eineAusgelasseneWocheBeendetDieSerie() {
        val dates = listOf(TODAY.minusDays(1), TODAY.minusDays(22))
        assertEquals(1, currentWeeklyStreak(dates, TODAY))
    }

    @Test
    fun dieNochOffeneWocheBrichtDieSerieNicht() {
        // Letztes Training in der Vorwoche, diese Woche noch nichts: Serie bleibt bei 1.
        val dates = listOf(TODAY.minusDays(3))
        val monday = TODAY.plusDays(1)
        assertEquals(1, currentWeeklyStreak(dates, monday))
    }

    @Test
    fun laengsteSerieUeberlebtSpaetereLuecken() {
        val dates = listOf(
            TODAY.minusWeeks(9), TODAY.minusWeeks(8), TODAY.minusWeeks(7),
            // Lücke
            TODAY.minusWeeks(1)
        )
        assertEquals(3, longestWeeklyStreak(dates))
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

    @Test
    fun stagnationGreiftErstNachDerWartezeit() {
        val stagnating = stagnatingExercises(
            lastChanged = mapOf(
                "Alt" to TODAY.minusDays(40),
                "Frisch" to TODAY.minusDays(3)
            ),
            currentWeights = mapOf("Alt" to 60.0, "Frisch" to 20.0),
            today = TODAY
        )
        assertEquals(listOf("Alt"), stagnating.map { it.name })
        assertEquals(40L, stagnating.first().sinceDays)
    }

}
