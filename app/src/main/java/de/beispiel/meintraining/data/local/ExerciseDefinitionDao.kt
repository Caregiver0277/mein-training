package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.beispiel.meintraining.data.model.ExerciseDefinition
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDefinitionDao {

    @Query("SELECT * FROM ExerciseDefinition ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ExerciseDefinition>>

    @Query("SELECT * FROM ExerciseDefinition WHERE name = :name")
    suspend fun find(name: String): ExerciseDefinition?

    @Query("SELECT * FROM ExerciseDefinition WHERE usesBodyweight = 1")
    suspend fun listBodyweightExercises(): List<ExerciseDefinition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(definition: ExerciseDefinition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(definitions: List<ExerciseDefinition>)

    /** Ändert das Gewicht der Übung an allen Tagen gleichzeitig. */
    @Query("UPDATE ExerciseDefinition SET weightKg = :weightKg WHERE name = :name")
    suspend fun updateWeight(name: String, weightKg: Double?)

    @Query("DELETE FROM ExerciseDefinition WHERE name = :name")
    suspend fun deleteByName(name: String)

    /** Entfernt Übungen, die an keinem Tag mehr vorkommen. */
    @Query("DELETE FROM ExerciseDefinition WHERE name NOT IN (SELECT name FROM Exercise)")
    suspend fun deleteOrphans()
}
