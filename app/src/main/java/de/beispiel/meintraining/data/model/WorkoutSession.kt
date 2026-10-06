package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein abgehaktes Training. Jeder Druck auf den grünen Haken schreibt eine Zeile.
 *
 * Aus diesen Einträgen entsteht der Verlauf und daraus wiederum der Deload-Zyklus:
 * Nur wer regelmäßig trainiert, sammelt Ermüdung an.
 */
@Entity(indices = [Index("completedAt")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Int,
    /** Zeitpunkt des Abhakens in Millisekunden seit 1970. */
    val completedAt: Long,
    /**
     * Beginn des Trainings in Millisekunden seit 1970; zusammen mit [completedAt] ergibt das die
     * Dauer. `null`, wenn sie nicht bekannt ist: bei Einträgen aus der Zeit davor, bei einem
     * Training ohne erkennbaren Beginn und bei einer unplausiblen Dauer (siehe
     * [de.beispiel.meintraining.util.plausibleStart]).
     */
    val startedAt: Long? = null
)
