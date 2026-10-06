package de.beispiel.meintraining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.beispiel.meintraining.data.model.SetLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SetLogDao {

    /** Das ganze Protokoll, ältester Satz zuerst – für Verlauf und Auswertung. */
    @Query("SELECT * FROM SetLog ORDER BY performedAt ASC, setNumber ASC, id ASC")
    fun observeAll(): Flow<List<SetLog>>

    /**
     * Das Protokoll einer Übung samt Variation, über alle Trainingstage – „Letztes Mal“ greift
     * auf einen anderen Tag zurück, wenn dieser noch keine Einheit hat.
     *
     * `IS` statt `=`, damit auch „keine Variation“ (`null`) passt.
     */
    @Query(
        "SELECT * FROM SetLog WHERE exerciseName = :name AND variation IS :variation " +
            "ORDER BY performedAt ASC, setNumber ASC, id ASC"
    )
    fun observeByExercise(name: String, variation: String?): Flow<List<SetLog>>

    /** Das ganze Protokoll – für die Sicherung. */
    @Query("SELECT * FROM SetLog ORDER BY performedAt ASC, id ASC")
    suspend fun listAll(): List<SetLog>

    /**
     * Wie oft Satz [setNumber] dieser Übung an diesem Trainingstag zwischen [from] und [until]
     * schon protokolliert ist – also in der Einheit dieses Kalendertages.
     */
    @Query(
        "SELECT COUNT(*) FROM SetLog WHERE exerciseName = :name AND variation IS :variation " +
            "AND dayId = :dayId AND setNumber = :setNumber " +
            "AND performedAt >= :from AND performedAt < :until"
    )
    suspend fun countInUnit(
        name: String,
        variation: String?,
        dayId: Int,
        setNumber: Int,
        from: Long,
        until: Long
    ): Int

    @Query("SELECT * FROM SetLog WHERE id = :id")
    suspend fun findById(id: Long): SetLog?

    @Insert
    suspend fun insert(log: SetLog): Long

    @Insert
    suspend fun insertAll(logs: List<SetLog>)

    /** Korrigiert einen gespeicherten Satz. */
    @Update
    suspend fun update(log: SetLog)

    @Query("DELETE FROM SetLog WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Nimmt das Protokoll einer umbenannten Übung mit – wie [WeightLogDao.renameExercise]. */
    @Query("UPDATE SetLog SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)

    /** Löscht das komplette Protokoll der Übungen. */
    @Query("DELETE FROM SetLog WHERE exerciseName IN (:names)")
    suspend fun deleteByNames(names: Collection<String>)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM SetLog")
    suspend fun deleteAll()
}
