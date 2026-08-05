package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val DAYS = 4

private val TODAY: LocalDate = LocalDate.of(2026, 8, 5)
private val YESTERDAY: LocalDate = TODAY.minusDays(1)

/** Abgehakte Tage von gestern – die typische Vorgeschichte einer laufenden Runde. */
private fun past(vararg dayIds: Int) = dayIds.map { RotationEntry(it, YESTERDAY) }

private fun today(vararg dayIds: Int) = dayIds.map { RotationEntry(it, TODAY) }

class RotationTest {

    @Test
    fun ohneTrainingIstNichtsAbgehakt() {
        assertTrue(completedDaysInRotation(emptyList(), DAYS, TODAY).isEmpty())
    }

    @Test
    fun abgehakteTageSammelnSich() {
        assertEquals(setOf(1, 2), completedDaysInRotation(past(1, 2), DAYS, TODAY))
    }

    @Test
    fun eineGesternGeschlosseneRundeIstAbgeraeumt() {
        assertTrue(completedDaysInRotation(past(1, 2, 3, 4), DAYS, TODAY).isEmpty())
    }

    @Test
    fun dieHeuteGeschlosseneRundeBleibtStehen() {
        assertEquals(
            setOf(1, 2, 3, 4),
            completedDaysInRotation(past(1, 2, 3) + today(4), DAYS, TODAY)
        )
    }

    @Test
    fun nachDemNaechstenTrainingBeginntDieRundeTrotzdemNeu() {
        // Volle Runde von heute, dann noch ein Training am selben Abend.
        assertEquals(
            setOf(1),
            completedDaysInRotation(past(1, 2, 3) + today(4, 1), DAYS, TODAY)
        )
    }

    @Test
    fun dieNaechsteRundeZaehltWiederHoch() {
        assertEquals(setOf(1), completedDaysInRotation(past(1, 2, 3, 4, 1), DAYS, TODAY))
    }

    @Test
    fun derselbeTagZweimalSchliesstDieRundeNicht() {
        // Tag 2 doppelt abgehakt: Tag 3 und 4 fehlen weiterhin.
        assertEquals(setOf(1, 2), completedDaysInRotation(past(1, 2, 2), DAYS, TODAY))
    }

    @Test
    fun reihenfolgeDerTageIstEgal() {
        assertEquals(setOf(3, 1), completedDaysInRotation(past(3, 1), DAYS, TODAY))
        assertTrue(completedDaysInRotation(past(3, 1, 4, 2), DAYS, TODAY).isEmpty())
    }

    @Test
    fun eintraegeAusDerZukunftRaeumenNichtAb() {
        // Zeitumstellung oder eingelesene Sicherung: lieber stehen lassen als verschlucken.
        val morgen = TODAY.plusDays(1)
        assertEquals(
            setOf(1, 2, 3, 4),
            completedDaysInRotation(past(1, 2, 3) + RotationEntry(4, morgen), DAYS, TODAY)
        )
    }

    @Test
    fun beiEinemTagProRundeBleibtDerHeutigeHakenStehen() {
        assertEquals(setOf(1), completedDaysInRotation(today(1), 1, TODAY))
        assertTrue(completedDaysInRotation(past(1), 1, TODAY).isEmpty())
    }

    @Test
    fun ohneTageGibtEsKeineRunde() {
        assertTrue(completedDaysInRotation(today(1), 0, TODAY).isEmpty())
    }

    @Test
    fun naechsterTagLaeuftImKreis() {
        assertEquals(2, nextDayId(1, DAYS))
        assertEquals(4, nextDayId(3, DAYS))
        assertEquals(1, nextDayId(DAYS, DAYS))
    }
}
