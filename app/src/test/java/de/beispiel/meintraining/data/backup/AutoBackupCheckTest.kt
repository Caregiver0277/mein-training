package de.beispiel.meintraining.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

private const val TARGET = "content://com.android.externalstorage.documents/document/sicherung.json"

class AutoBackupCheckTest {

    @Test
    fun ausgeschaltetGibtEsNichtsZuTun() {
        assertEquals(AutoBackupCheck.OFF, autoBackupCheck(false, TARGET, emptySet()))
        assertEquals(AutoBackupCheck.OFF, autoBackupCheck(false, null, emptySet()))
    }

    @Test
    fun mitZugriffWirdDerAuftragSichergestellt() {
        assertEquals(AutoBackupCheck.SCHEDULE, autoBackupCheck(true, TARGET, setOf(TARGET)))
    }

    /** Der Fall nach einem Handywechsel: Schalter und Ziel kamen mit, die Berechtigung nicht. */
    @Test
    fun ohneZugriffIstDieSicherungTot() {
        assertEquals(AutoBackupCheck.ACCESS_LOST, autoBackupCheck(true, TARGET, emptySet()))
        assertEquals(
            AutoBackupCheck.ACCESS_LOST,
            autoBackupCheck(true, TARGET, setOf("content://anderer.anbieter/andere-datei.json"))
        )
    }

    @Test
    fun ohneZielMussEbenfallsNeuGewaehltWerden() {
        assertEquals(AutoBackupCheck.ACCESS_LOST, autoBackupCheck(true, null, setOf(TARGET)))
    }
}
