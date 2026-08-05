package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.beispiel.meintraining.data.model.TrainingDay
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDayDao {

    @Query("SELECT * FROM TrainingDay ORDER BY id ASC")
    fun observeAll(): Flow<List<TrainingDay>>

    @Query("SELECT * FROM TrainingDay ORDER BY id ASC")
    suspend fun listAll(): List<TrainingDay>

    /** Gibt es diesen Trainingstag? Prüfung, bevor ein Training darauf eingetragen wird. */
    @Query("SELECT * FROM TrainingDay WHERE id = :id")
    suspend fun findById(id: Int): TrainingDay?

    @Query("UPDATE TrainingDay SET name = :name WHERE id = :id")
    suspend fun updateName(id: Int, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<TrainingDay>)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM TrainingDay")
    suspend fun deleteAll()
}
