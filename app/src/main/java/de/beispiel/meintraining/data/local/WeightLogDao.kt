package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.beispiel.meintraining.data.model.WeightLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightLogDao {

    @Query("SELECT * FROM WeightLog ORDER BY recordedAt ASC, id ASC")
    fun observeAll(): Flow<List<WeightLog>>

    @Insert
    suspend fun insert(log: WeightLog)

    /** Entfernt den jüngsten Eintrag einer Übung – für „Rückgängig“ nach einer Erhöhung. */
    @Query(
        """
        DELETE FROM WeightLog WHERE id = (
            SELECT id FROM WeightLog WHERE exerciseName = :name
            ORDER BY recordedAt DESC, id DESC LIMIT 1
        )
        """
    )
    suspend fun deleteLatest(name: String)

    /** Löscht den kompletten Verlauf einer Übung. */
    @Query("DELETE FROM WeightLog WHERE exerciseName = :name")
    suspend fun deleteByName(name: String)
}
