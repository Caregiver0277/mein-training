package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Eintrag im Gewichtsverlauf. Jede Gewichtsänderung einer Übung schreibt hier eine Zeile;
 * daraus entsteht der Graph im Tracking.
 *
 * Der Verlauf hängt – wie das Gewicht selbst – am Namen und bleibt erhalten, auch wenn die
 * Übung an keinem Tag mehr eingetragen ist. Legt man sie später erneut an, ist die
 * Vorgeschichte wieder da.
 */
@Entity(indices = [Index("exerciseName")])
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseName: String,
    val weightKg: Double,
    /** Zeitpunkt der Änderung in Millisekunden seit 1970. */
    val recordedAt: Long
)
