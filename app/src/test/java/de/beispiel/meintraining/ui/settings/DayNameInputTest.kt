package de.beispiel.meintraining.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayNameInputTest {

    @Test
    fun geschriebeneNamenWerdenAusgetragen() {
        val pending = mapOf(1 to "Push", 2 to "Pull")
        assertTrue(withoutWrittenNames(pending, written = pending).isEmpty())
    }

    @Test
    fun einSeitdemGeaenderterNameBleibtOffen() {
        val written = mapOf(1 to "Pus", 2 to "Pull")
        val pending = mapOf(1 to "Push", 2 to "Pull")
        assertEquals(mapOf(1 to "Push"), withoutWrittenNames(pending, written))
    }

    @Test
    fun einSeitdemNeuGetippterTagBleibtOffen() {
        val written = mapOf(1 to "Push")
        val pending = mapOf(1 to "Push", 3 to "Beine")
        assertEquals(mapOf(3 to "Beine"), withoutWrittenNames(pending, written))
    }

    /** Der Fehler von früher: Nach dem Zurücksetzen holte die nächste Umbenennung alte Namen zurück. */
    @Test
    fun nachDemSchreibenKommtNurDieNeueUmbenennungHinterher() {
        val first = mapOf(1 to "Push", 2 to "Pull")
        val afterWrite = withoutWrittenNames(first, written = first)
        // Später, etwa nach „Alle Daten löschen“, wird nur Tag 3 umbenannt.
        assertEquals(mapOf(3 to "Beine"), afterWrite + (3 to "Beine"))
    }
}
