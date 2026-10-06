package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein protokollierter Satz – nur für Übungen, bei denen das Protokoll eingeschaltet ist (siehe
 * [ExerciseDefinition.logSets]).
 *
 * Wie der Gewichtsverlauf hängt das Protokoll am Namen und überlebt das Löschen der Zeile; legt
 * man die Übung neu an, ist die Vorgeschichte wieder da. Anders als das Gewicht hängen Sätze und
 * Wiederholungen aber an der Zeile: Dieselbe Übung kann an einem Tag „3 x 4-6“ und an einem
 * anderen „3 x 8-12“ haben. Deshalb steht neben dem Namen auch der Trainingstag ([dayId]) und
 * die [variation] – „Trizeps (Seil)“ und „Trizeps (Stange)“ teilen sich den Namen, können am
 * selben Tag stehen und haben trotzdem jede ihre eigenen Sätze. Eine Zeile ist damit eindeutig
 * genug, ohne an ihrer Kennung zu hängen, die beim Kopieren, Löschen und Wiederherstellen wechselt.
 *
 * Eine Einheit sind die Sätze eines Namens, einer Variation und eines Trainingstages am selben
 * Kalendertag.
 */
@Entity(indices = [Index("exerciseName"), Index("performedAt")])
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseName: String,
    /** Die Variation der Zeile wie „Seil“; `null` ohne Variation. */
    val variation: String? = null,
    val dayId: Int,
    /** Zeitpunkt des Satzes in Millisekunden seit 1970. */
    val performedAt: Long,
    /** Nummer des Satzes in seiner Einheit, ab 1; Zusatzsätze zählen hinter den geplanten weiter. */
    val setNumber: Int,
    val reps: Int,
    /** Die bewegte Last; `null` bei einer Übung ohne Gewicht. */
    val weightKg: Double? = null
)
