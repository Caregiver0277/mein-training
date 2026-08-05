package de.beispiel.meintraining.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eine krumme Rate, damit sich niemand auf glatte 44,1 kHz verlässt. */
private const val RATE = 8_000

class RestTimerSoundTest {

    @Test
    fun derTonHatDieErwarteteLaenge() {
        // Zwei Töne à 170 ms mit 60 ms Pause dazwischen: 400 ms.
        val samples = chimeSamples(RATE)
        assertEquals(RATE * 400 / 1000, samples.size)
    }

    @Test
    fun anfangUndEndeSindStill() {
        // Ein hart ein- oder ausgeschalteter Sinus knackt; die Hüllkurve verhindert genau das.
        val samples = chimeSamples(RATE)
        assertEquals(0, samples.first().toInt())
        assertTrue("Am Ende noch ${samples.last()}", samples.last().toInt() in -64..64)
    }

    @Test
    fun zwischenDenToenenIstPause() {
        val samples = chimeSamples(RATE)
        val toneLength = RATE * 170 / 1000
        val gapLength = RATE * 60 / 1000
        val gap = samples.copyOfRange(toneLength, toneLength + gapLength)
        assertTrue("Pause nicht still: ${gap.maxOf { it.toInt() }}", gap.all { it.toInt() == 0 })
    }

    @Test
    fun derAusschlagBleibtUnterDerGrenze() {
        // Voll ausgesteuert übersteuert der Ton auf manchen Geräten hörbar.
        val samples = chimeSamples(RATE)
        val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Ausschlag $peak", peak in 1..(Short.MAX_VALUE * 0.8).toInt())
    }

    @Test
    fun eineUnmoeglicheAbtastrateFliegtAuf() {
        // Lieber hier als in einem stummen AudioTrack.
        assertThrows(IllegalArgumentException::class.java) { chimeSamples(0) }
    }
}
