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
    val progressionStepKg: Double = DEFAULT_PROGRESSION_STEP_KG,
    /**
     * Richtung der Progression: `true` heißt, der Pfeil in der Liste *senkt* das Gewicht um den
     * Schritt, statt es zu erhöhen.
     *
     * Steht bei den geteilten Werten und nicht an der einzelnen Zeile, aus demselben Grund wie
     * der Schritt selbst: Beides beschreibt, wie sich das gemeinsame Gewicht bewegt – und das
     * gilt an allen Tagen, an denen die Übung vorkommt.
     */
    val progressionDown: Boolean = false,
    /**
     * Freier Hinweis wie „Sitz 4, Polster 2“ oder ein Technik-Stichwort; `null`, solange keiner
     * eingetragen ist. Hängt am Namen, weil er zum Gerät und zur Übung gehört, nicht zum Tag.
     */
    val note: String? = null,
    /**
     * Werden die Sätze dieser Übung einzeln protokolliert (siehe [SetLog])? Vorgabe: nein – für
     * jede Übung ohne diesen Schalter bleibt alles, wie es war.
     */
    val logSets: Boolean = false
)
