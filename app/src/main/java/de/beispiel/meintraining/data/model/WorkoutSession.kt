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
    val completedAt: Long
)
