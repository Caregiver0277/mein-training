package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine eingetragene Cardio-Einheit – ein Eintrag pro Einheit, nicht pro Satz.
 *
 * Aufgebaut wie das Satz-Protokoll ([SetLog]): Der Eintrag hängt am Namen und überlebt das
 * Löschen der Zeile, und Trainingstag ([dayId]) und [variation] halten auseinander, was sich den
 * Namen teilt – „Rad (Intervalle)“ und „Rad (locker)“ haben jede ihre eigenen Einheiten. Eine
 * Einheit ist damit ein Name samt Variation an einem Trainingstag und einem Kalendertag.
 *
 * Aus diesen Einträgen entstehen die Kurven im Tracking; die Zielwerte selbst werden nicht wie
 * das Gewicht als Verlauf mitgeschrieben.
 *
 * Jeder Wert ist optional, aber nicht alle zugleich (siehe [CardioValues.isEmpty]). Die
 * [intensityUnit] steht an jedem Eintrag selbst, damit Tempo und Stufe nie in einer Kurve
 * landen – auch wenn die Übung ihre Einheit später wechselt.
 */
@Entity(indices = [Index("exerciseName"), Index("performedAt")])
data class CardioLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseName: String,
    /** Die Variation der Zeile; `null` ohne Variation. */
    val variation: String? = null,
    val dayId: Int,
    /** Zeitpunkt des Eintrags in Millisekunden seit 1970. */
    val performedAt: Long,
    val durationMin: Double? = null,
    val distanceKm: Double? = null,
    val intensity: Double? = null,
    /** `null`, solange es kein [intensity] gibt. */
    val intensityUnit: IntensityUnit? = null,
    val inclinePercent: Double? = null
) {
    val values: CardioValues
        get() = CardioValues(durationMin, distanceKm, intensity, intensityUnit, inclinePercent)
}
