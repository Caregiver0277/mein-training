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

    @Query("SELECT COUNT(*) FROM TrainingDay")
    suspend fun count(): Int

    @Query("UPDATE TrainingDay SET name = :name WHERE id = :id")
    suspend fun updateName(id: Int, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<TrainingDay>)
}
