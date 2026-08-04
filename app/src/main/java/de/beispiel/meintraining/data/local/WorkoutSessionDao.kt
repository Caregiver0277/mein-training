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

    /** Alle abgehakten Trainings – für die Sicherung. */
    @Query("SELECT * FROM WorkoutSession ORDER BY completedAt ASC, id ASC")
    suspend fun listAll(): List<WorkoutSession>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Insert
    suspend fun insertAll(sessions: List<WorkoutSession>)

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Nimmt das jüngste Abhaken eines Trainingstages zurück.
     *
     * Der Umweg über die Unterabfrage ist nötig, weil SQLite `DELETE … ORDER BY … LIMIT` nur
     * mit einer Übersetzungsoption kennt, auf die sich hier niemand verlassen soll.
     */
    @Query(
        """
        DELETE FROM WorkoutSession WHERE id = (
            SELECT id FROM WorkoutSession WHERE dayId = :dayId
            ORDER BY completedAt DESC, id DESC LIMIT 1
        )
        """
    )
    suspend fun deleteLatestForDay(dayId: Int)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM WorkoutSession")
    suspend fun deleteAll()
}
