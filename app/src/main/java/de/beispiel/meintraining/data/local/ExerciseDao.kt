package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.beispiel.meintraining.data.model.Exercise
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import kotlinx.coroutines.flow.Flow

/**
 * Übung samt geteilten Werten.
 *
 * Bewusst ein LEFT JOIN: Zu jeder Übung *sollte* es eine [ExerciseDefinition][
 * de.beispiel.meintraining.data.model.ExerciseDefinition] geben, und das Repository hält das
 * auch ein. Wäre der Verbund streng, ließe ein einziger vergessener Eintrag die Übung aber
 * spurlos aus der Liste verschwinden – schlimmer als eine Zeile ohne Gewicht.
 */
private const val ITEM_COLUMNS = """
    e.id AS id, e.dayId AS dayId, e.name AS name, e.variation AS variation,
    e.sets AS sets, e.repsMin AS repsMin, e.repsMax AS repsMax, e.position AS position,
    e.supersetId AS supersetId,
    d.weightKg AS weightKg,
    COALESCE(d.progressionStepKg, $DEFAULT_PROGRESSION_STEP_KG) AS progressionStepKg
"""

@Dao
interface ExerciseDao {

    /**
     * Alle Übungen aller Tage – Grundlage für Trainingsliste, Statistiken und Einstellungen.
     *
     * Die Sortierung nach `dayId` zuerst hält die Reihenfolge auch dann richtig, wenn die
     * Oberfläche daraus die Übungen eines einzelnen Tages herausfiltert.
     */
    @Query(
        """
        SELECT $ITEM_COLUMNS
        FROM Exercise e
        LEFT JOIN ExerciseDefinition d ON d.name = e.name
        ORDER BY e.dayId ASC, e.position ASC, e.id ASC
        """
    )
    fun observeAll(): Flow<List<ExerciseItem>>

    @Query(
        """
        SELECT $ITEM_COLUMNS
        FROM Exercise e
        LEFT JOIN ExerciseDefinition d ON d.name = e.name
        WHERE e.id = :id
        """
    )
    suspend fun findById(id: Long): ExerciseItem?

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun findEntityById(id: Long): Exercise?

    /** Alle Übungen als Rohdaten – für die Sicherung. */
    @Query("SELECT * FROM Exercise ORDER BY dayId ASC, position ASC, id ASC")
    suspend fun listAll(): List<Exercise>

    /** Die Übungen eines Tages in Anzeigereihenfolge – Grundlage fürs Sortieren und Gruppieren. */
    @Query("SELECT * FROM Exercise WHERE dayId = :dayId ORDER BY position ASC, id ASC")
    suspend fun listByDay(dayId: Int): List<Exercise>

    /**
     * Wie oft die Übung noch vorkommt – über alle Tage hinweg.
     *
     * Nach einem Umbenennen die Frage, ob der alte Name überhaupt noch jemandem gehört; ist er
     * es nicht, wandert sein Gewichtsverlauf mit (siehe [WeightLogDao.renameExercise]).
     */
    @Query("SELECT COUNT(*) FROM Exercise WHERE name = :name")
    suspend fun countByName(name: String): Int

    /** Nächste freie Sortierposition innerhalb eines Tages. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM Exercise WHERE dayId = :dayId")
    suspend fun nextPosition(dayId: Int): Int

    /** Nächste freie Superset-Kennung; sie gilt über alle Tage hinweg. */
    @Query("SELECT COALESCE(MAX(supersetId), 0) + 1 FROM Exercise")
    suspend fun nextSupersetId(): Long

    @Query("UPDATE Exercise SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE Exercise SET supersetId = :supersetId WHERE id = :id")
    suspend fun updateSuperset(id: Long, supersetId: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise): Long

    /** Der ganze Bestand auf einmal – für das Einspielen einer Sicherung. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Update
    suspend fun update(exercise: Exercise)

    @Query("DELETE FROM Exercise WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Die Tage, an denen eine der Übungen vorkommt – dort müssen danach die Supersets aufgeräumt werden. */
    @Query("SELECT DISTINCT dayId FROM Exercise WHERE name IN (:names)")
    suspend fun listDayIdsForNames(names: Collection<String>): List<Int>

    /** Entfernt die Übungen von allen Trainingstagen. */
    @Query("DELETE FROM Exercise WHERE name IN (:names)")
    suspend fun deleteByNames(names: Collection<String>)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM Exercise")
    suspend fun deleteAll()
}
