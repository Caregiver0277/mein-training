package de.beispiel.meintraining.ui

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** „Cardio eintragen“: was in den Feldern steht und was beim Speichern ankommt. */
class CardioEntryFormTest {

    private fun cardio(targets: CardioTargets) = ExerciseItem(
        id = 1,
        dayId = 1,
        name = "Crosstrainer",
        variation = null,
        sets = null,
        repsMin = null,
        repsMax = null,
        position = 0,
        supersetId = null,
        weightKg = null,
        progressionStepKg = DEFAULT_PROGRESSION_STEP_KG,
        kind = ExerciseKind.CARDIO,
        cardio = targets
    )

    @Test
    fun vorbelegtMitDenZielen() {
        val ziele = CardioTargets(durationMin = 22.5, distanceKm = 5.0, intensity = 8.0, intensityUnit = IntensityUnit.LEVEL)
        val form = CardioLogDialogState(cardio(ziele)).initialForm()
        assertEquals("22:30", form.duration)
        assertEquals("5", form.distance)
        assertEquals("8", form.intensity)
        assertEquals(IntensityUnit.LEVEL, form.intensityUnit)
        assertEquals("", form.incline)
        // Bestätigen genügt: Gespeichert werden genau die Ziele.
        assertEquals(ziele.values, form.toValues())
    }

    @Test
    fun eineEinheitVonHeuteGehtVorDenZielen() {
        val ziele = CardioTargets(durationMin = 20.0, intensity = 6.0)
        val heute = CardioLog(
            id = 7,
            exerciseName = "Crosstrainer",
            dayId = 1,
            performedAt = 0,
            durationMin = 21.0,
            inclinePercent = 2.5
        )
        val form = CardioLogDialogState(cardio(ziele), todaysLog = heute).initialForm()
        assertEquals("21", form.duration)
        assertEquals("", form.intensity)
        assertEquals("2,5", form.incline)
        // Ohne Tempo in der Einheit gilt die Einheit der Übung, falls man eins nachträgt.
        assertEquals(IntensityUnit.KMH, form.intensityUnit)
    }

    @Test
    fun speichernBrauchtEineLesbareDauerUndEinenWert() {
        assertFalse(CardioEntryForm().canSave)
        assertFalse(CardioEntryForm(duration = "7:75", distance = "3").canSave)
        assertTrue(CardioEntryForm(distance = "3,4").canSave)
        val werte = CardioEntryForm(duration = "7,5", intensity = "6", intensityUnit = IntensityUnit.KMH).toValues()
        assertEquals(CardioValues(durationMin = 7.5, intensity = 6.0, intensityUnit = IntensityUnit.KMH), werte)
        // Ohne Tempo keine Einheit dazu.
        assertNull(CardioEntryForm(duration = "20").toValues().intensityUnit)
    }
}
