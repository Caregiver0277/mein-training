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

    // --- Progressionsschritt ----------------------------------------------

    @Test
    fun kommaAlsDezimaltrenner() {
        assertEquals(2.5, parseProgressionStep("2,5"), 0.0)
    }

    @Test
    fun punktAlsDezimaltrenner() {
        assertEquals(5.0, parseProgressionStep("5.0"), 0.0)
    }

    @Test
    fun leereEingabeNutztStandardwert() {
        assertEquals(DEFAULT_PROGRESSION_STEP_KG, parseProgressionStep("   "), 0.0)
    }

    @Test
    fun nichtPositiveWerteNutzenStandardwert() {
        assertEquals(DEFAULT_PROGRESSION_STEP_KG, parseProgressionStep("0"), 0.0)
        assertEquals(DEFAULT_PROGRESSION_STEP_KG, parseProgressionStep("-2,5"), 0.0)
    }

    @Test
    fun unlesbareEingabeNutztStandardwert() {
        assertEquals(DEFAULT_PROGRESSION_STEP_KG, parseProgressionStep("abc"), 0.0)
    }

    // --- Optionale Eingaben ------------------------------------------------

    @Test
    fun leereDezimaleingabeErgibtNull() {
        assertNull(parseOptionalDecimal(""))
    }

    @Test
    fun leereGanzzahleingabeErgibtNull() {
        assertNull(parseOptionalInt(" "))
    }

    @Test
    fun negativeGanzzahlErgibtNull() {
        assertNull(parseOptionalInt("-3"))
    }

    // --- Progression -------------------------------------------------------

    @Test
    fun progressionOhneFliesskommaFehler() {
        assertEquals(22.5, increaseWeight(20.0, 2.5), 0.0)
        assertEquals(21.25, increaseWeight(20.0, 1.25), 0.0)
        assertEquals("22,5 Kg", increaseWeight(20.0, 2.5).toWeightLabel(UNIT))
    }

    @Test
    fun mehrfacheProgressionBleibtExakt() {
        var weight = 20.0
        repeat(3) { weight = increaseWeight(weight, 2.5) }
        assertEquals("27,5 Kg", weight.toWeightLabel(UNIT))
    }
}
