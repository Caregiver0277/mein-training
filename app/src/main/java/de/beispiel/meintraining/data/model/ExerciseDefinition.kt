package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG

/**
 * Die geteilten Werte einer Übung. Der Name ist der Schlüssel: Dieselbe Übung an mehreren
 * Tagen greift auf denselben Eintrag zu, deshalb wirkt eine Gewichtsänderung überall.
 *
 * Sobald kein Trainingstag den Namen mehr verwendet, wird der Eintrag aufgeräumt.
 */
@Entity
data class ExerciseDefinition(
    @PrimaryKey val name: String,
    val weightKg: Double? = null,
    val progressionStepKg: Double = DEFAULT_PROGRESSION_STEP_KG
)
