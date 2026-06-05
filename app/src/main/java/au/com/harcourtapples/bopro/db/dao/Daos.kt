package au.com.harcourtapples.bopro.db.dao

import androidx.room.*
import au.com.harcourtapples.bopro.db.entities.LocalCount
import au.com.harcourtapples.bopro.db.entities.LocalSession

@Dao
interface LocalSessionDao {
    @Query("SELECT * FROM local_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<LocalSession>

    @Query("SELECT * FROM local_sessions WHERE id = :id")
    suspend fun getById(id: Int): LocalSession?

    @Query("SELECT * FROM local_sessions WHERE synced = 0 ORDER BY startedAt ASC")
    suspend fun getUnsynced(): List<LocalSession>

    @Insert  suspend fun insert(session: LocalSession): Long
    @Update  suspend fun update(session: LocalSession)
    @Delete  suspend fun delete(session: LocalSession)
}

@Dao
interface LocalCountDao {
    @Query("SELECT * FROM local_counts WHERE sessionId = :sessionId ORDER BY scannedAt DESC")
    suspend fun getBySession(sessionId: Int): List<LocalCount>

    @Query("SELECT * FROM local_counts WHERE sessionId = :sessionId AND synced = 0")
    suspend fun getUnsyncedForSession(sessionId: Int): List<LocalCount>

    @Query("SELECT COUNT(*) FROM local_counts WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Int): Int

    @Query("SELECT * FROM local_counts WHERE sessionId = :sessionId AND barcode = :barcode LIMIT 1")
    suspend fun getBySessionAndBarcode(sessionId: Int, barcode: String): LocalCount?

    @Insert  suspend fun insert(count: LocalCount): Long
    @Update  suspend fun update(count: LocalCount)

    @Query("DELETE FROM local_counts WHERE id = :id")
    suspend fun deleteById(id: Int)
}
