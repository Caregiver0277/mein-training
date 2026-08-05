package de.beispiel.meintraining.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eine krumme Rate, damit sich niemand auf glatte 44,1 kHz verlässt. */
private const val RATE = 8_000

private const val TONE_SAMPLES = RATE * 220 / 1000
private const val GAP_SAMPLES = RATE * 55 / 1000

class RestTimerSoundTest {

    @Test
    fun derTonHatDieErwarteteLaenge() {
        // Vier Töne à 220 ms mit je 55 ms Pause dazwischen: 1045 ms.
        val samples = chimeSamples(RATE)
        assertEquals(RATE * 1045 / 1000, samples.size)
    }

    @Test
    fun anfangUndEndeSindStill() {
        // Eine hart ein- oder ausgeschaltete Schwingung knackt; die Hüllkurve verhindert das.
        val samples = chimeSamples(RATE)
        assertEquals(0, samples.first().toInt())
        assertTrue("Am Ende noch ${samples.last()}", samples.last().toInt() in -64..64)
    }

    @Test
    fun zwischenDenToenenIstPause() {
        val samples = chimeSamples(RATE)
        val gap = samples.copyOfRange(TONE_SAMPLES, TONE_SAMPLES + GAP_SAMPLES)
        assertTrue("Pause nicht still: ${gap.maxOf { it.toInt() }}", gap.all { it.toInt() == 0 })
    }

    @Test
    fun derAusschlagBleibtUnterDerGrenze() {
        // Der Ton steuert absichtlich fast voll aus; was über die Grenze ginge, käme als
        // beschnittene Welle zurück und knarzte.
        val samples = chimeSamples(RATE)
        val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Ausschlag $peak", peak in 1..(Short.MAX_VALUE * 0.99).toInt())
    }

    /**
     * Der eigentliche Punkt der ganzen Übung: Der Ton war zu leise, und zwar nicht am Ausschlag
     * gemessen, sondern an der Energie. Der Effektivwert ist das, was das Ohr als Lautstärke
     * nimmt – ein reiner Sinus mit weich abfallender Hüllkurve kam hier auf etwa 0,24, obwohl
     * er genauso voll aussteuerte.
     *
     * Die Grenze ist bewusst weit unter dem erreichten Wert und weit über dem alten: Sie soll
     * auffallen, wenn jemand Sättigung oder Haltephase wieder herausnimmt, und nicht bei jeder
     * Feinjustierung an den Konstanten anschlagen.
     */
    @Test
    fun derTonTraegtGenugEnergie() {
        val samples = chimeSamples(RATE)
        val meanSquare = samples.sumOf { it.toDouble() * it.toDouble() } / samples.size
        val rms = kotlin.math.sqrt(meanSquare) / Short.MAX_VALUE
        assertTrue("Effektivwert nur $rms", rms > 0.5)
    }

    /**
     * Die Haltephase ist der größte Einzelgewinn an Lautheit. Fiele die Hüllkurve wieder vom
     * ersten Augenblick an ab, bliebe die Mitte des Tones deutlich unter dem Vollausschlag.
     */
    @Test
    fun derTonStehtInSeinerMitteVollDa() {
        val samples = chimeSamples(RATE)
        val middle = samples.copyOfRange(TONE_SAMPLES / 3, TONE_SAMPLES / 2)
        val peak = middle.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("In der Mitte nur $peak", peak > Short.MAX_VALUE * 0.9)
    }

    @Test
    fun eineUnmoeglicheAbtastrateFliegtAuf() {
        // Lieber hier als in einem stummen AudioTrack.
        assertThrows(IllegalArgumentException::class.java) { chimeSamples(0) }
    }

    // --- Lautstärkeregler --------------------------------------------------

    @Test
    fun dieEndenDesReglersBleibenDieEnden() {
        assertEquals(0f, chimeGain(0f), 0f)
        assertEquals(1f, chimeGain(1f), 0f)
    }

    /**
     * Halber Regler ergibt ein Viertel der Verstärkung – und klingt damit etwa halb so laut.
     * Ein linear durchgereichter Wert wäre bei 0,5 kaum vom Vollausschlag zu unterscheiden.
     */
    @Test
    fun derReglerFolgtDemGehoer() {
        assertEquals(0.25f, chimeGain(0.5f), 1e-6f)
        assertTrue("Zu laut: ${chimeGain(0.25f)}", chimeGain(0.25f) < 0.1f)
    }

    /** Was außerhalb liegt, würde AudioTrack.setVolume ablehnen. */
    @Test
    fun werteAusserhalbWerdenGekappt() {
        assertEquals(0f, chimeGain(-1f), 0f)
        assertEquals(1f, chimeGain(2f), 0f)
    }
}
