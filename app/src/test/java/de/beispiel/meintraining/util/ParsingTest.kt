package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val UNIT = "Kg"

class ParsingTest {

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

    @Test
    fun gueltigeDezimalzahlKommtDurch() {
        assertEquals(22.5, parseOptionalDecimal("22,5")!!, 0.0)
        assertEquals(0.0, parseOptionalDecimal("0")!!, 0.0)
    }

    /**
     * Die Java-Zahlenlesung nimmt „NaN“ und „Infinity“ klaglos an. Ein solcher Wert landete
     * als Gewicht in der Datenbank und ließe [increaseWeight] beim nächsten Druck auf den
     * Pfeil scheitern – mitten in einer Coroutine, also mit Absturz.
     */
    @Test
    fun nichtDarstellbareZahlenErgebenNull() {
        assertNull(parseOptionalDecimal("NaN"))
        assertNull(parseOptionalDecimal("Infinity"))
        assertNull(parseOptionalDecimal("-Infinity"))
    }

    /** Negative Gewichte gibt es nicht – der Graph zeichnete sie sonst klaglos mit. */
    @Test
    fun negativeDezimalzahlErgibtNull() {
        assertNull(parseOptionalDecimal("-20"))
        assertNull(parseOptionalDecimal("-0,5"))
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

    @Test
    fun senkenOhneFliesskommaFehler() {
        assertEquals(20.0, decreaseWeight(22.5, 2.5), 0.0)
        assertEquals("19,375 Kg", decreaseWeight(20.0, 0.625).toWeightLabel(UNIT))
    }

    /** Unten ist bei 0 Schluss: Negative Gewichte gibt es nicht. */
    @Test
    fun senkenBleibtBeiNullStehen() {
        assertEquals(0.0, decreaseWeight(2.0, 2.5), 0.0)
        assertEquals(0.0, decreaseWeight(0.0, 2.5), 0.0)
    }

    /** Hinauf und zurück ergibt genau den Ausgangswert – auch beim feinsten Schritt. */
    @Test
    fun erhoehenUndSenkenHebenSichAuf() {
        var weight = 20.0
        repeat(4) { weight = increaseWeight(weight, 0.625) }
        repeat(4) { weight = decreaseWeight(weight, 0.625) }
        assertEquals("20 Kg", weight.toWeightLabel(UNIT))
    }

    @Test
    fun derFeinsteSchrittKommtVollstaendigAn() {
        // Viermal 0,625 kg ergeben genau 2,5 kg – und zwar auf dem Weg dorthin ohne einen
        // einzigen gerundeten Zwischenstand, sonst zeigte die Liste eine andere Zahl als die,
        // mit der weitergerechnet wird.
        assertEquals(0.625, parseProgressionStep("0,625"), 0.0)
        var weight = 20.0
        val labels = List(4) {
            weight = increaseWeight(weight, 0.625)
            weight.toWeightLabel(UNIT)
        }
        assertEquals(listOf("20,625 Kg", "21,25 Kg", "21,875 Kg", "22,5 Kg"), labels)
    }
}
