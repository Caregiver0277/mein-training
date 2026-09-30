package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Eine Liste in Anzeigereihenfolge; die Kennungen sind der Einfachheit halber 1..n. */
private fun ids(count: Int): List<Long> = (1L..count).toList()

class SupersetsTest {

    @Test
    fun ohneSupersetsUeberlebtNichts() {
        val surviving = survivingSupersetMembers(ids(3), listOf(null, null, null))
        assertEquals(emptySet<Long>(), surviving)
    }

    @Test
    fun einZusammenhaengenderBlockBleibtBestehen() {
        // 1 | [2 3] | 4
        val surviving = survivingSupersetMembers(ids(4), listOf(null, 7L, 7L, null))
        assertEquals(setOf(2L, 3L), surviving)
    }

    @Test
    fun einDazwischengeschobenerEintragTrenntDenBlock() {
        // [1] 2 [3 4] – der längere Lauf gewinnt, die einzelne 1 fällt heraus.
        val surviving = survivingSupersetMembers(ids(4), listOf(7L, null, 7L, 7L))
        assertEquals(setOf(3L, 4L), surviving)
    }

    @Test
    fun beiGleichLangenLaeufenGewinntDerObere() {
        // [1 2] 3 [4 5] – zwei gleich lange Läufe; der erste bleibt.
        val surviving = survivingSupersetMembers(ids(5), listOf(7L, 7L, null, 7L, 7L))
        assertEquals(setOf(1L, 2L), surviving)
    }

    @Test
    fun einUebrigGebliebenerEintragLoestDasSupersetAuf() {
        // Nur noch ein Mitglied nebeneinander: zu wenig für ein Superset.
        val surviving = survivingSupersetMembers(ids(3), listOf(7L, null, 7L))
        assertEquals(emptySet<Long>(), surviving)
    }

    @Test
    fun mehrereSupersetsStoerenSichNicht() {
        // [1 2] [3 4 5] – zwei Gruppen direkt untereinander.
        val surviving = survivingSupersetMembers(ids(5), listOf(7L, 7L, 8L, 8L, 8L))
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), surviving)
    }

    @Test
    fun einZerrissenesSupersetVerliertNurDieAusreisser() {
        // [1 2 3] 4 [5] – der Dreierblock bleibt, die abgetrennte 5 nicht.
        val surviving = survivingSupersetMembers(ids(5), listOf(7L, 7L, 7L, null, 7L))
        assertEquals(setOf(1L, 2L, 3L), surviving)
    }

    @Test
    fun eineLeereListeIstInOrdnung() {
        assertEquals(emptySet<Long>(), survivingSupersetMembers(emptyList(), emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ungleichLangeListenSindEinProgrammierfehler() {
        survivingSupersetMembers(ids(2), listOf(null))
    }

    // --- Ausgeblendete Zeilen beim Umsortieren ------------------------------

    /**
     * Sichtbar war „C | [A B]“, dahinter ein ausgeblendetes X. Wird C nach oben geschoben,
     * bekommen die sichtbaren Zeilen die Plätze 0, 1 und 3 – X bliebe auf Platz 2, mitten im
     * Superset, und das Aufräumen löste es auf. Stattdessen rückt X hinter den Block.
     */
    @Test
    fun eineAusgeblendeteZeileTrenntKeinSuperset() {
        val supersetOf = mapOf(3L to null, 1L to 7L, 9L to null, 2L to 7L)
        val arranged = keepSupersetBlocksTogether(
            orderedIds = listOf(3L, 1L, 9L, 2L),
            supersetIds = listOf(null, 7L, null, 7L),
            pinned = setOf(9L)
        )

        assertEquals(listOf(3L, 1L, 2L, 9L), arranged)
        assertEquals(
            setOf(1L, 2L),
            survivingSupersetMembers(arranged, arranged.map { supersetOf.getValue(it) })
        )
    }

    @Test
    fun ohneSupersetBleibtEineAusgeblendeteZeileAnIhremPlatz() {
        val arranged = keepSupersetBlocksTogether(
            orderedIds = listOf(3L, 9L, 1L),
            supersetIds = listOf(null, null, null),
            pinned = setOf(9L)
        )
        assertEquals(listOf(3L, 9L, 1L), arranged)
    }

    /** Ein ausgeblendetes Mitglied gehört in seinen Block und bleibt darin stehen. */
    @Test
    fun einAusgeblendetesMitgliedBleibtImBlock() {
        val arranged = keepSupersetBlocksTogether(
            orderedIds = listOf(1L, 9L, 2L),
            supersetIds = listOf(7L, 7L, 7L),
            pinned = setOf(9L)
        )
        assertEquals(listOf(1L, 9L, 2L), arranged)
    }

    /** Zwischen zwei *verschiedenen* Supersets trennt die Zeile nichts und bleibt stehen. */
    @Test
    fun zwischenZweiSupersetsBleibtDieZeileStehen() {
        val arranged = keepSupersetBlocksTogether(
            orderedIds = listOf(1L, 2L, 9L, 3L, 4L),
            supersetIds = listOf(7L, 7L, null, 8L, 8L),
            pinned = setOf(9L)
        )
        assertEquals(listOf(1L, 2L, 9L, 3L, 4L), arranged)
    }

    @Test
    fun mehrereZurueckgestellteZeilenBehaltenIhreReihenfolge() {
        val arranged = keepSupersetBlocksTogether(
            orderedIds = listOf(1L, 9L, 8L, 2L, 3L),
            supersetIds = listOf(7L, null, null, 7L, null),
            pinned = setOf(9L, 8L)
        )
        assertEquals(listOf(1L, 2L, 9L, 8L, 3L), arranged)
    }
}
