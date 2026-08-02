package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein Trainingstag. Die Kennung zählt ab [FIRST_DAY_ID] durch; wie viele Tage die Runde hat,
 * steht in den Einstellungen.
 */
@Entity
data class TrainingDay(
    @PrimaryKey val id: Int,
    val name: String
)

/** Anzahl der Trainingstage, solange nichts anderes eingestellt ist. */
const val DEFAULT_DAY_COUNT = 4

/**
 * Grenzen für die einstellbare Anzahl. Mehr als eine Woche ergibt keine Runde mehr, und
 * unter einem Tag bliebe nichts übrig.
 */
const val MIN_DAY_COUNT = 1
const val MAX_DAY_COUNT = 7

/** Standardmäßig ausgewählter Tag. */
const val FIRST_DAY_ID = 1
