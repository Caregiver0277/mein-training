package de.beispiel.meintraining.ui

import de.beispiel.meintraining.data.model.ExerciseDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Übungen, die es schon gibt – so, wie das ViewModel sie dem Formular mitgibt. */
private val BEKANNT = listOf(
    ExerciseDefinition(name = "Rudern", weightKg = 40.0, progressionStepKg = 2.5, note = "Griff eng"),
    ExerciseDefinition(name = "Rudern eng", weightKg = 50.0, progressionStepKg = 2.5),
    ExerciseDefinition(name = "Curls", weightKg = 12.5, progressionStepKg = 1.25, logSets = true),
    ExerciseDefinition(
        name = "Klimmzugmaschine",
        weightKg = 30.0,
        progressionStepKg = 5.0,
        progressionDown = true
    )
)

/** Ein Tastendruck im Namensfeld – so, wie das Sheet ihn meldet. */
private fun ExerciseForm.tippeName(name: String) = withChange(copy(name = name), BEKANNT)

private fun ExerciseForm.tippeGewicht(weight: String) = withChange(copy(weight = weight), BEKANNT)

/** Das Formular, mit dem das Sheet eine vorhandene Übung öffnet (siehe `toForm`). */
private fun bearbeiten(name: String, weight: String) = ExerciseForm(
    id = 7L,
    name = name,
    weight = weight,
    originalName = name,
    matchedName = name
)

class ExerciseFormTest {

    // --- Name und Schreibweise ---------------------------------------------

    @Test
    fun einBekannterNameBringtSeineWerteMit() {
        val form = ExerciseForm().tippeName("Klimmzugmaschine")
        assertEquals("30", form.weight)
        assertEquals("5", form.progressionStep)
        assertTrue(form.progressionDown)
    }

    @Test
    fun dieSchreibweiseWirdAufDieGespeicherteAngeglichen() {
        assertEquals("Rudern", ExerciseForm().tippeName("rudern").name)
    }

    /**
     * Wer „Rudern breit“ neben einem vorhandenen „Rudern“ anlegt, tippt unterwegs „Rudern “.
     * Das Leerzeichen darf dabei nicht verschwinden – sonst entstünde „Rudernbreit“.
     */
    @Test
    fun dasLeerzeichenHinterEinemBekanntenNamenBleibtStehen() {
        val form = ExerciseForm().tippeName("Rudern").tippeName("Rudern ")
        assertEquals("Rudern ", form.name)
        assertEquals("Rudern breit", form.tippeName("Rudern b").tippeName("Rudern breit").name)
    }

    @Test
    fun beimAngleichenBleibenLeerzeichenAmRandStehen() {
        assertEquals("Rudern ", ExerciseForm().tippeName("rudern ").name)
    }

    // --- Eigene Werte ------------------------------------------------------

    /**
     * Beim Umbenennen von „Rudern eng“ in „Rudern breit“ kommt man an „Rudern“ vorbei. Dessen
     * Gewicht darf danach nicht stehen bleiben – gespeichert würde es sonst unter dem neuen Namen,
     * obwohl niemand das Feld angefasst hat.
     */
    @Test
    fun umbenennenUeberEineAndereUebungBehaeltDasEigeneGewicht() {
        val unterwegs = bearbeiten("Rudern eng", "50").tippeName("Rudern ").tippeName("Rudern")
        assertEquals("40", unterwegs.weight)

        val fertig = unterwegs.tippeName("Rudern b").tippeName("Rudern breit")
        assertEquals("50", fertig.weight)
        assertFalse(fertig.progressionDown)
    }

    @Test
    fun ohneTrefferKommenDieVorherGetipptenWerteZurueck() {
        val form = ExerciseForm().tippeGewicht("30").tippeName("Curls")
        assertEquals("12,5", form.weight)
        assertEquals("1,25", form.progressionStep)

        val weiter = form.tippeName("Curls x")
        assertEquals("30", weiter.weight)
        assertEquals("2,5", weiter.progressionStep)
    }

    /** Ein Name, der eben noch passte und nun nicht mehr, räumt beim Anlegen das Gewicht wieder ab. */
    @Test
    fun einNeuerNameErbtNichtDasGewichtEinerAnderenUebung() {
        val form = ExerciseForm().tippeName("Curls").tippeName("Curls am Kabel")
        assertEquals("", form.weight)
    }

    /** Was von Hand geändert wurde, gehört dem Nutzer – auch wenn der Name danach weiterläuft. */
    @Test
    fun vonHandGeaenderteWerteBleibenBeimWeitertippenStehen() {
        val form = ExerciseForm().tippeName("Curls").tippeGewicht("15")
        assertEquals("15", form.tippeName("Curls ").weight)
        assertEquals("15", form.tippeName("Curls x").weight)
    }

    /**
     * Beim Bearbeiten: Gewicht von Hand geändert, dann am Namen etwas ausprobiert und zurück –
     * die Änderung bleibt, statt vom gespeicherten Stand überschrieben zu werden.
     */
    @Test
    fun zurueckZumEigenenNamenLaesstDieAenderungStehen() {
        val form = bearbeiten("Curls", "12,5").tippeGewicht("15")
        assertEquals("15", form.tippeName("Curls ").weight)
        assertEquals("15", form.tippeName("Curlsx").tippeName("Curls").weight)
    }

    @Test
    fun einWechselZuEinerAnderenUebungUeberschreibtAuchVonHandGeaendertes() {
        val form = bearbeiten("Curls", "12,5").tippeGewicht("15").tippeName("Rudern")
        assertEquals("40", form.weight)
        // Und zurück: Die eigene Eingabe ist nicht verloren.
        assertEquals("15", form.tippeName("Rudernx").weight)
    }

    // --- Notiz --------------------------------------------------------------

    /** Die Notiz hängt am Namen wie das Gewicht: Ein bekannter Name bringt seine mit. */
    @Test
    fun einBekannterNameBringtSeineNotizMit() {
        assertEquals("Griff eng", ExerciseForm().tippeName("Rudern").note)
    }

    /** Und sie geht wieder, wenn der Name nicht mehr passt – die eigene kommt zurück. */
    @Test
    fun ohneTrefferKommtDieEigeneNotizZurueck() {
        val form = ExerciseForm()
            .let { it.withChange(it.copy(note = "Bank Stufe 3"), BEKANNT) }
            .tippeName("Rudern")
        assertEquals("Griff eng", form.note)
        assertEquals("Bank Stufe 3", form.tippeName("Rudern x").note)
    }

    // --- Sätze protokollieren -----------------------------------------------

    /** Der Schalter hängt am Namen wie die Notiz: Ein bekannter Name bringt seinen mit. */
    @Test
    fun einBekannterNameBringtSeinenProtokollSchalterMit() {
        assertTrue(ExerciseForm().tippeName("Curls").logSets)
        assertFalse(ExerciseForm().tippeName("Rudern").logSets)
    }

    /** Von Hand umgelegt, bleibt er beim Umbenennen stehen – er ist dann der eigene. */
    @Test
    fun einVonHandUmgelegterSchalterBleibtBeimUmbenennen() {
        val form = bearbeiten(name = "Curls", weight = "12,5")
            .let { it.withChange(it.copy(logSets = true), BEKANNT) }
            .tippeName("Curls KH")
        assertTrue(form.logSets)
    }

    /** Ohne Treffer kommt der eigene Stand zurück – auch für den Schalter. */
    @Test
    fun ohneTrefferKommtDerEigeneSchalterZurueck() {
        val form = ExerciseForm().tippeName("Curls")
        assertTrue(form.logSets)
        assertFalse(form.tippeName("Curlsx").logSets)
    }

    // --- Übrige Felder -----------------------------------------------------

    @Test
    fun saetzeUndWiederholungenBleibenUnangetastet() {
        val form = ExerciseForm(sets = "3", repsMin = "8", repsMax = "12").tippeName("Curls")
        assertEquals("3", form.sets)
        assertEquals("8", form.repsMin)
        assertEquals("12", form.repsMax)
    }
}
