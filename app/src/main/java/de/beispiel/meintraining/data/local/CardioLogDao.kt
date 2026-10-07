package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.beispiel.meintraining.data.model.CardioLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CardioLogDao {

    /** Alle Einheiten, älteste zuerst – für Tracking, Verlauf und Auswertung. */
    @Query("SELECT * FROM CardioLog ORDER BY performedAt ASC, id ASC")
    fun observeAll(): Flow<List<CardioLog>>

    /**
     * Die Einheiten einer Übung samt Variation, über alle Trainingstage – wie
     * [SetLogDao.observeByExercise]; `IS` statt `=`, damit auch „keine Variation“ (`null`) passt.
     */
    @Query(
        "SELECT * FROM CardioLog WHERE exerciseName = :name AND variation IS :variation " +
            "ORDER BY performedAt ASC, id ASC"
    )
    fun observeByExercise(name: String, variation: String?): Flow<List<CardioLog>>

    /** Alle Einheiten – für die Sicherung. */
    @Query("SELECT * FROM CardioLog ORDER BY performedAt ASC, id ASC")
    suspend fun listAll(): List<CardioLog>

    /** Wie viele Einheiten dieser Übung an diesem Trainingstag zwischen [from] und [until] stehen. */
    @Query(
        "SELECT COUNT(*) FROM CardioLog WHERE exerciseName = :name AND variation IS :variation " +
            "AND dayId = :dayId AND performedAt >= :from AND performedAt < :until"
    )
    suspend fun countInUnit(name: String, variation: String?, dayId: Int, from: Long, until: Long): Int

    @Query("SELECT * FROM CardioLog WHERE id = :id")
    suspend fun findById(id: Long): CardioLog?

    @Insert
    suspend fun insert(log: CardioLog): Long

    @Insert
    suspend fun insertAll(logs: List<CardioLog>)

    /** Korrigiert eine eingetragene Einheit. */
    @Update
    suspend fun update(log: CardioLog)

    @Query("DELETE FROM CardioLog WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Nimmt die Einheiten einer umbenannten Übung mit – wie [WeightLogDao.renameExercise]. */
    @Query("UPDATE CardioLog SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)

    /** Löscht alle Einheiten der Übungen. */
    @Query("DELETE FROM CardioLog WHERE exerciseName IN (:names)")
    suspend fun deleteByNames(names: Collection<String>)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM CardioLog")
    suspend fun deleteAll()
}
