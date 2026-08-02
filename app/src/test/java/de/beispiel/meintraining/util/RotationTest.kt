package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAYS = 4

class RotationTest {

    @Test
    fun ohneTrainingIstNichtsAbgehakt() {
        assertTrue(completedDaysInRotation(emptyList(), DAYS).isEmpty())
    }

    @Test
    fun abgehakteTageSammelnSich() {
        assertEquals(setOf(1, 2), completedDaysInRotation(listOf(1, 2), DAYS))
    }

    @Test
    fun nachAllenTagenBeginntEineNeueRunde() {
        assertTrue(completedDaysInRotation(listOf(1, 2, 3, 4), DAYS).isEmpty())
    }

    @Test
    fun dieNaechsteRundeZaehltWiederHoch() {
        assertEquals(setOf(1), completedDaysInRotation(listOf(1, 2, 3, 4, 1), DAYS))
    }

    @Test
    fun derselbeTagZweimalSchliesstDieRundeNicht() {
        // Tag 2 doppelt abgehakt: Tag 3 und 4 fehlen weiterhin.
        assertEquals(setOf(1, 2), completedDaysInRotation(listOf(1, 2, 2), DAYS))
    }

    @Test
    fun reihenfolgeDerTageIstEgal() {
        assertEquals(setOf(3, 1), completedDaysInRotation(listOf(3, 1), DAYS))
        assertTrue(completedDaysInRotation(listOf(3, 1, 4, 2), DAYS).isEmpty())
    }

    @Test
    fun naechsterTagLaeuftImKreis() {
        assertEquals(2, nextDayId(1, DAYS))
        assertEquals(4, nextDayId(3, DAYS))
        assertEquals(1, nextDayId(DAYS, DAYS))
    }
}
