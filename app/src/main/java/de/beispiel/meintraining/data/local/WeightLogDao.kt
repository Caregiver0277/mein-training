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

    /** Der komplette Verlauf – für die Sicherung. */
    @Query("SELECT * FROM WeightLog ORDER BY recordedAt ASC, id ASC")
    suspend fun listAll(): List<WeightLog>

    /** Liefert die Kennung des neuen Eintrags – nur über sie lässt er sich gezielt zurücknehmen. */
    @Insert
    suspend fun insert(log: WeightLog): Long

    @Insert
    suspend fun insertAll(logs: List<WeightLog>)

    /**
     * Schreibt den Verlauf einer umbenannten Übung auf den neuen Namen um.
     *
     * Ohne das bliebe er unter dem alten Namen stehen: Der Graph zeigte eine Kurve, die am
     * Namenswechsel abbricht, daneben eine neue mit einem einzigen Punkt – und der Zuwachs
     * (siehe [de.beispiel.meintraining.util.exerciseGains]) fiele für beide weg, weil er
     * mindestens zwei Punkte braucht.
     */
    @Query("UPDATE WeightLog SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)

    /** Entfernt einen einzelnen Punkt aus dem Verlauf – von Hand im Tracking gelöscht. */
    @Query("DELETE FROM WeightLog WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Löscht den kompletten Verlauf der Übungen. */
    @Query("DELETE FROM WeightLog WHERE exerciseName IN (:names)")
    suspend fun deleteByNames(names: Collection<String>)

    /** Nur für das vollständige Zurücksetzen der App. */
    @Query("DELETE FROM WeightLog")
    suspend fun deleteAll()
}
