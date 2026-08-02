package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOLERANCE = 0.001

class WeightsTest {

    @Test
    fun ohneKoerpergewichtsuebungZaehltNurDasEingetrageneGewicht() {
        assertEquals(
            60.0,
            effectiveWeightKg(weightKg = 60.0, usesBodyweight = false, bodyweightKg = 75.0)!!,
            TOLERANCE
        )
    }

    @Test
    fun beiKoerpergewichtKommtDasEigeneGewichtDazu() {
        assertEquals(
            80.0,
            effectiveWeightKg(weightKg = 5.0, usesBodyweight = true, bodyweightKg = 75.0)!!,
            TOLERANCE
        )
    }

    @Test
    fun reinesKoerpergewichtIstDasKoerpergewicht() {
        assertEquals(
            75.0,
            effectiveWeightKg(weightKg = null, usesBodyweight = true, bodyweightKg = 75.0)!!,
            TOLERANCE
        )
    }

    @Test
    fun ohneHinterlegtesKoerpergewichtWirdNichtGeraten() {
        assertEquals(
            5.0,
            effectiveWeightKg(weightKg = 5.0, usesBodyweight = true, bodyweightKg = null)!!,
            TOLERANCE
        )
        assertNull(effectiveWeightKg(weightKg = null, usesBodyweight = true, bodyweightKg = null))
    }

    @Test
    fun ohneGewichtBleibtEsLeer() {
        assertNull(effectiveWeightKg(weightKg = null, usesBodyweight = false, bodyweightKg = 75.0))
    }
}
