package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.beispiel.meintraining.data.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM WorkoutSession ORDER BY completedAt DESC, id DESC")
    fun observeAll(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM WorkoutSession ORDER BY completedAt DESC, id DESC LIMIT 1")
    suspend fun latest(): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun deleteById(id: Long)
}
