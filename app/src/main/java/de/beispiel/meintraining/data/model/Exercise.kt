package de.beispiel.meintraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine Übung innerhalb eines Trainingstages.
 *
 * Gewicht und Progressionsschritt stehen bewusst *nicht* hier, sondern in
 * [ExerciseDefinition] – sie hängen am Namen und gelten an allen Tagen gleichzeitig.
 * Sätze, Wiederholungen und die [variation] gehören dagegen zu genau dieser Zeile.
 * Nullbare Werte bedeuten „noch nicht gesetzt“ und werden als „–“ dargestellt.
 */
@Entity(indices = [Index("dayId"), Index("name")])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Int,
    val name: String,
    /** Zusatz wie „Seil“ oder „Stange“, der hinter dem Namen in Klammern erscheint. */
    val variation: String? = null,
    val sets: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val position: Int = 0,
    /**
     * Übungen mit derselben Kennung bilden ein Superset und werden als Block dargestellt.
     * Die Mitglieder liegen immer direkt untereinander – zieht man eine heraus, verlässt
     * sie das Superset.
     */
    val supersetId: Long? = null
)
