package de.beispiel.meintraining.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private const val DAYS = 4

private val TODAY: LocalDate = LocalDate.of(2026, 8, 5)
private val YESTERDAY: LocalDate = TODAY.minusDays(1)

/** Abgehakte Tage von gestern – die typische Vorgeschichte einer laufenden Runde. */
private fun past(vararg dayIds: Int) = dayIds.map { RotationEntry(it, YESTERDAY) }

private fun today(vararg dayIds: Int) = dayIds.map { RotationEntry(it, TODAY) }

/** Mittag des Tages als Zeitstempel – für alles, wo es auf die Rundenschnitte ankommt. */
private fun at(date: LocalDate): Long =
    date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Ein Training mit Zeitstempel, damit Schnitte davor und dahinter liegen können. */
private fun entry(dayId: Int, date: LocalDate) =
    RotationEntry(dayId = dayId, date = date, completedAt = at(date))

class RotationTest {

    @Test
    fun ohneTrainingIstNichtsAbgehakt() {
        assertTrue(completedDaysInRotation(emptyList(), DAYS, TODAY).isEmpty())
    }

    @Test
    fun abgehakteTageSammelnSich() {
        assertEquals(setOf(1, 2), completedDaysInRotation(past(1, 2), DAYS, TODAY))
    }

    @Test
    fun eineGesternGeschlosseneRundeIstAbgeraeumt() {
        assertTrue(completedDaysInRotation(past(1, 2, 3, 4), DAYS, TODAY).isEmpty())
    }

    @Test
    fun dieHeuteGeschlosseneRundeBleibtStehen() {
        assertEquals(
            setOf(1, 2, 3, 4),
            completedDaysInRotation(past(1, 2, 3) + today(4), DAYS, TODAY)
        )
    }

    @Test
    fun nachDemNaechstenTrainingBeginntDieRundeTrotzdemNeu() {
        // Volle Runde von heute, dann noch ein Training am selben Abend.
        assertEquals(
            setOf(1),
            completedDaysInRotation(past(1, 2, 3) + today(4, 1), DAYS, TODAY)
        )
    }

    @Test
    fun dieNaechsteRundeZaehltWiederHoch() {
        assertEquals(setOf(1), completedDaysInRotation(past(1, 2, 3, 4, 1), DAYS, TODAY))
    }

    @Test
    fun derselbeTagZweimalSchliesstDieRundeNicht() {
        // Tag 2 doppelt abgehakt: Tag 3 und 4 fehlen weiterhin.
        assertEquals(setOf(1, 2), completedDaysInRotation(past(1, 2, 2), DAYS, TODAY))
    }

    @Test
    fun reihenfolgeDerTageIstEgal() {
        assertEquals(setOf(3, 1), completedDaysInRotation(past(3, 1), DAYS, TODAY))
        assertTrue(completedDaysInRotation(past(3, 1, 4, 2), DAYS, TODAY).isEmpty())
    }

    @Test
    fun eintraegeAusDerZukunftRaeumenNichtAb() {
        // Zeitumstellung oder eingelesene Sicherung: lieber stehen lassen als verschlucken.
        val morgen = TODAY.plusDays(1)
        assertEquals(
            setOf(1, 2, 3, 4),
            completedDaysInRotation(past(1, 2, 3) + RotationEntry(4, morgen), DAYS, TODAY)
        )
    }

    @Test
    fun beiEinemTagProRundeBleibtDerHeutigeHakenStehen() {
        assertEquals(setOf(1), completedDaysInRotation(today(1), 1, TODAY))
        assertTrue(completedDaysInRotation(past(1), 1, TODAY).isEmpty())
    }

    @Test
    fun ohneTageGibtEsKeineRunde() {
        assertTrue(completedDaysInRotation(today(1), 0, TODAY).isEmpty())
    }

    @Test
    fun verborgeneTageMachenDieRundeNichtVoll() {
        // Nach einer verkürzten Runde stehen Einträge auf Tag 5 und 6 im Verlauf: Sie zählen
        // nicht mit, sonst stünde die Runde als voll da, während der Bildschirm zwei Haken zeigt.
        val entries = past(1, 2) + past(5, 6)
        assertEquals(setOf(1, 2), completedDaysInRotation(entries, DAYS, TODAY))
    }

    @Test
    fun naechsterTagLaeuftImKreis() {
        assertEquals(2, nextDayId(1, DAYS))
        assertEquals(4, nextDayId(3, DAYS))
        assertEquals(1, nextDayId(DAYS, DAYS))
    }

    // --- Runden von Hand abschließen ---------------------------------------

    @Test
    fun einSchnittSchliesstDieLaufendeRundeAb() {
        // Drei Tage geschafft, der vierte fällt aus: Der Pfeil zieht den Schnitt.
        val entries = listOf(
            entry(1, TODAY.minusDays(4)),
            entry(2, TODAY.minusDays(3)),
            entry(3, TODAY.minusDays(2))
        )
        val cuts = listOf(at(TODAY.minusDays(1)))
        assertTrue(completedDaysInRotation(entries, DAYS, TODAY, cuts).isEmpty())
    }

    @Test
    fun nachDemSchnittZaehltNurNochWasDanachKommt() {
        val entries = listOf(
            entry(1, TODAY.minusDays(4)),
            entry(2, TODAY.minusDays(3)),
            entry(1, TODAY)
        )
        val cuts = listOf(at(TODAY.minusDays(2)))
        assertEquals(setOf(1), completedDaysInRotation(entries, DAYS, TODAY, cuts))
    }

    @Test
    fun einSchnittGenauAufDemLetztenTrainingLaesstEsInDerAltenRunde() {
        // So setzt startNextRotation den Schnitt: hinter dem jüngsten Eintrag, mindestens im
        // Jetzt. Der Eintrag selbst gehört noch zur abgeschlossenen Runde.
        val letztes = entry(3, TODAY)
        val entries = listOf(entry(1, TODAY.minusDays(2)), entry(2, TODAY.minusDays(1)), letztes)
        assertTrue(
            completedDaysInRotation(entries, DAYS, TODAY, listOf(letztes.completedAt)).isEmpty()
        )
    }

    @Test
    fun mehrereSchnitteHintereinanderErgebenKeineLeerenRunden() {
        val entries = listOf(entry(1, TODAY.minusDays(3)), entry(2, TODAY))
        val cuts = listOf(
            at(TODAY.minusDays(2)),
            at(TODAY.minusDays(2)) + 1,
            at(TODAY.minusDays(1))
        )
        val runden = rotations(entries, DAYS, TODAY, cuts)
        assertEquals(2, runden.size)
        assertEquals(setOf(1), runden.first().completedDayIds)
        assertEquals(setOf(2), runden.last().completedDayIds)
    }

    @Test
    fun einSchnittLaesstSichZurueckziehenSolangeNichtTrainiertWurde() {
        val entries = listOf(entry(1, TODAY.minusDays(1)))
        val schnitt = at(TODAY)
        assertTrue(canUndoRotationCut(entries, listOf(schnitt)))

        // Sobald in der neuen Runde etwas steht, führt der Weg vorwärts.
        assertFalse(canUndoRotationCut(entries + entry(2, TODAY.plusDays(1)), listOf(schnitt)))
        // Und ohne Schnitt gibt es nichts zurückzunehmen.
        assertFalse(canUndoRotationCut(entries, emptyList()))
    }

    @Test
    fun zurueckgezogenerSchnittStelltDieVorigeRundeWiederHer() {
        val entries = listOf(
            entry(1, TODAY.minusDays(3)),
            entry(2, TODAY.minusDays(2)),
            entry(3, TODAY.minusDays(1))
        )
        val cuts = listOf(at(TODAY))
        assertTrue(completedDaysInRotation(entries, DAYS, TODAY, cuts).isEmpty())
        assertEquals(setOf(1, 2, 3), completedDaysInRotation(entries, DAYS, TODAY, emptyList()))
    }

    // --- Nachgetragene Trainings -------------------------------------------

    @Test
    fun einNachgetragenesTrainingLandetInDerRundeSeinesZeitpunkts() {
        // Tag 2 wurde vergessen und jetzt für vorgestern nachgetragen: Er gehört in die
        // laufende Runde und steht dort als abgehakt.
        val entries = listOf(
            entry(1, TODAY.minusDays(3)),
            entry(2, TODAY.minusDays(2)),
            entry(3, TODAY.minusDays(1))
        )
        assertEquals(setOf(1, 2, 3), completedDaysInRotation(entries, DAYS, TODAY))
    }

    @Test
    fun einNachgetragenesTrainingSchliesstSeineRundeUndOeffnetDieNaechste() {
        // Der fehlende vierte Tag von vorgestern: Die Runde ist damit rückwirkend voll und
        // abgelaufen, das Training von gestern beginnt die nächste.
        val entries = listOf(
            entry(1, TODAY.minusDays(4)),
            entry(2, TODAY.minusDays(3)),
            entry(3, TODAY.minusDays(2)),
            entry(4, TODAY.minusDays(2)),
            entry(1, TODAY.minusDays(1))
        )
        val runden = rotations(entries, DAYS, TODAY)
        assertEquals(2, runden.size)
        assertEquals(setOf(1, 2, 3, 4), runden.first().completedDayIds)
        assertTrue(runden.first().isFull)
        assertEquals(setOf(1), runden.last().completedDayIds)
    }

    @Test
    fun jedeRundeKenntIhreEintraege() {
        val entries = listOf(
            entry(1, TODAY.minusDays(5)),
            entry(2, TODAY.minusDays(4)),
            entry(3, TODAY.minusDays(3)),
            entry(4, TODAY.minusDays(2)),
            entry(1, TODAY.minusDays(1))
        )
        val runden = rotations(entries, DAYS, TODAY)
        assertEquals(listOf(0, 1, 2, 3), runden.first().entryIndices)
        assertEquals(listOf(4), runden.last().entryIndices)
    }

    @Test
    fun dieLaufendeRundeStehtImmerAmEnde() {
        // Auch wenn sie leer ist: Ohne Verlauf, nach einem Schnitt und nach einer abgelaufenen
        // vollen Runde gibt es genau eine laufende Runde, und sie ist die letzte.
        assertTrue(rotations(emptyList(), DAYS, TODAY).last().isEmpty)
        assertTrue(rotations(past(1, 2, 3, 4), DAYS, TODAY).last().isEmpty)
        val entries = listOf(entry(1, TODAY.minusDays(1)))
        assertTrue(rotations(entries, DAYS, TODAY, listOf(at(TODAY))).last().isEmpty)
        assertFalse(rotations(entries, DAYS, TODAY).last().isEmpty)
    }
}
