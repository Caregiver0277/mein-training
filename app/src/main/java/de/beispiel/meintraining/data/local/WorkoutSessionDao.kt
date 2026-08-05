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
     * Das jüngste Abhaken eines Trainingstages; `null`, wenn der Tag noch nie dran war.
     *
     * Grundlage fürs Zurücknehmen: Gelöscht wird anschließend genau diese Zeile über ihre
     * Kennung, statt noch einmal blind „die jüngste“ zu treffen.
     */
    @Query(
        "SELECT * FROM WorkoutSession WHERE dayId = :dayId ORDER BY completedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestForDay(dayId: Int): WorkoutSession?

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM WorkoutSession")
    suspend fun deleteAll()
}
