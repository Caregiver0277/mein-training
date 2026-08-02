package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ein Trainingstag (1..4). */
@Entity
data class TrainingDay(
    @PrimaryKey val id: Int,
    val name: String
)

/** Anzahl der fest vorgegebenen Trainingstage. */
const val DAY_COUNT = 4

/** Standardmäßig ausgewählter Tag. */
const val FIRST_DAY_ID = 1
