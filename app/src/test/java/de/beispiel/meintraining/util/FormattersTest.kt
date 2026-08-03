package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val UNIT = "Kg"

class FormattersTest {

    // --- Gewicht -----------------------------------------------------------

    @Test
    fun ganzeZahlOhneNachkommastellen() {
        assertEquals("20 Kg", 20.0.toWeightLabel(UNIT))
    }

    @Test
    fun dezimalwertMitDeutschemKomma() {
        assertEquals("22,5 Kg", 22.5.toWeightLabel(UNIT))
    }

    @Test
    fun zweiNachkommastellenBleibenErhalten() {
        assertEquals("1,25 Kg", 1.25.toWeightLabel(UNIT))
    }

    @Test
    fun fehlendesGewichtHatKeinLabel() {
        val weight: Double? = null
        assertNull(weight?.toWeightLabel(UNIT))
    }

    // --- Anzeigename -------------------------------------------------------

    @Test
    fun variationStehtInKlammernHinterDemNamen() {
        assertEquals("Trizeps (Seil)", exerciseTitle("Trizeps", "Seil"))
    }

    @Test
    fun ohneVariationBleibtNurDerName() {
        assertEquals("Trizeps", exerciseTitle("Trizeps", null))
        assertEquals("Trizeps", exerciseTitle("Trizeps", "   "))
    }

    // --- Sätze und Wiederholungen -----------------------------------------

    @Test
    fun wiederholungsspanneAlsBereich() {
        assertEquals("3 x 4-6", 3.toSetsRepsLabel(4, 6))
    }

    @Test
    fun gleicheGrenzenWerdenZusammengefasst() {
        assertEquals("3 x 6", 3.toSetsRepsLabel(6, 6))
    }

    @Test
    fun ohneWiederholungenNurSaetze() {
        assertEquals("3", 3.toSetsRepsLabel(null, null))
    }

    @Test
    fun einzelneGrenzeAlsEinzelwert() {
        assertEquals("3 x 8", 3.toSetsRepsLabel(null, 8))
    }

    @Test
    fun fehlendeSaetzeHabenKeinLabel() {
        val sets: Int? = null
        assertNull(sets.toSetsRepsLabel(4, 6))
    }
}
