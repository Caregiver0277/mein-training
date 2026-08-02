package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY: LocalDate = LocalDate.of(2026, 8, 2)

/** Trainingstage im Abstand von [everyDays] Tagen, beginnend vor [startDaysAgo] Tagen. */
private fun sessions(startDaysAgo: Long, everyDays: Long = 2, untilDaysAgo: Long = 0) =
    generateSequence(startDaysAgo) { it - everyDays }
        .takeWhile { it >= untilDaysAgo }
        .map { TODAY.minusDays(it) }
        .toList()

class DeloadTest {

    @Test
    fun ohneTrainingKeinDeload() {
        val status = deloadStatus(emptyList(), TODAY)
        assertFalse(status.isDeloadWeek)
        assertEquals(0, status.totalSessions)
    }

    @Test
    fun frischerBlockStartetInWocheEins() {
        val status = deloadStatus(sessions(startDaysAgo = 4), TODAY)
        assertEquals(1, status.weekInCycle)
        assertFalse(status.isDeloadWeek)
    }

    @Test
    fun nachFuenfWochenTrainingKommtDerDeload() {
        // 35 Tage durchgehend: Tag 35 ist Blockbeginn, heute liegt in Woche 6.
        val status = deloadStatus(sessions(startDaysAgo = 35), TODAY)
        assertEquals(DEFAULT_DELOAD_CYCLE_WEEKS, status.weekInCycle)
        assertTrue(status.isDeloadWeek)
        assertEquals(0, status.weeksUntilDeload)
    }

    @Test
    fun inWocheDreiIstNochKeinDeload() {
        val status = deloadStatus(sessions(startDaysAgo = 16), TODAY)
        assertEquals(3, status.weekInCycle)
        assertFalse(status.isDeloadWeek)
        assertEquals(3, status.weeksUntilDeload)
    }

    @Test
    fun eineTrainingsfreieWocheErsetztDenDeload() {
        // Fünf Wochen Training, dann acht Tage Pause, dann wieder zwei Trainings.
        val block = sessions(startDaysAgo = 45, untilDaysAgo = 12)
        val nachPause = listOf(TODAY.minusDays(3), TODAY.minusDays(1))
        val status = deloadStatus(block + nachPause, TODAY)

        assertFalse("Nach der Pause darf kein Deload anstehen", status.isDeloadWeek)
        assertEquals(1, status.weekInCycle)
        assertEquals(TODAY.minusDays(3), status.cycleStart)
    }

    @Test
    fun laufendePauseZaehltSelbstAlsDeload() {
        // Langer Block, aber seit zehn Tagen nichts mehr: Erholung läuft bereits.
        val status = deloadStatus(sessions(startDaysAgo = 60, untilDaysAgo = 10), TODAY)
        assertTrue(status.isResting)
        assertFalse(status.isDeloadWeek)
    }

    @Test
    fun nachDemDeloadGehtEsWiederVonVornLos() {
        // 42 Tage durchgehend: die Deload-Woche liegt hinter uns, Woche 1 des nächsten Blocks.
        val status = deloadStatus(sessions(startDaysAgo = 42), TODAY)
        assertEquals(1, status.weekInCycle)
        assertFalse(status.isDeloadWeek)
    }

    @Test
    fun einheitenImBlockWerdenAbPauseNeuGezaehlt() {
        val block = sessions(startDaysAgo = 40, untilDaysAgo = 20)
        val nachPause = listOf(TODAY.minusDays(6), TODAY.minusDays(4), TODAY.minusDays(2))
        val status = deloadStatus(block + nachPause, TODAY)

        assertEquals(3, status.sessionsInCycle)
        assertEquals(block.size + 3, status.totalSessions)
    }

    @Test
    fun mehrfachEintraegeAmSelbenTagZaehlenEinmal() {
        val tag = TODAY.minusDays(2)
        val status = deloadStatus(listOf(tag, tag, tag), TODAY)
        assertEquals(1, status.totalSessions)
    }

    @Test
    fun kuerzererZyklusBringtDenDeloadFrueher() {
        // 21 Tage Training: bei einem Vierwochenblock ist das Woche 4 – also Deload.
        val status = deloadStatus(sessions(startDaysAgo = 21), TODAY, cycleWeeks = 4)
        assertEquals(4, status.weekInCycle)
        assertTrue(status.isDeloadWeek)
    }

    @Test
    fun beiLaengeremZyklusIstDerselbeZeitpunktNochTraining() {
        val status = deloadStatus(sessions(startDaysAgo = 21), TODAY, cycleWeeks = 8)
        assertEquals(4, status.weekInCycle)
        assertFalse(status.isDeloadWeek)
        assertEquals(4, status.weeksUntilDeload)
    }

    @Test
    fun unsinnigeZykluslaengenWerdenBegrenzt() {
        assertEquals(MIN_CYCLE_WEEKS, deloadStatus(emptyList(), TODAY, cycleWeeks = 0).cycleWeeks)
        assertEquals(MAX_CYCLE_WEEKS, deloadStatus(emptyList(), TODAY, cycleWeeks = 99).cycleWeeks)
    }

    // --- Sätze halbieren ---------------------------------------------------

    @Test
    fun saetzeWerdenAufgerundetHalbiert() {
        assertEquals(2, deloadSets(3))
        assertEquals(2, deloadSets(4))
        assertEquals(3, deloadSets(5))
        assertEquals(1, deloadSets(2))
    }

    @Test
    fun einSatzBleibtEinSatz() {
        assertEquals(1, deloadSets(1))
    }

    @Test
    fun ohneSaetzeBleibtEsLeer() {
        assertEquals(null, deloadSets(null))
    }
}
