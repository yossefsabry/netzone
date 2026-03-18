package com.netzone.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val uid: Int,
    val protocol: String = "TCP",
    val sourceAddress: String? = null,
    val sourcePort: Int? = null,
    val destinationAddress: String? = null,
    val destinationPort: Int? = null,
    val blocked: Boolean = true
)

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 500")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT DISTINCT packageName FROM logs WHERE timestamp > :since")
    fun getRecentPackageNames(since: Long): Flow<List<String>>

    @Insert
    suspend fun insert(log: LogEntry)

    @Query("DELETE FROM logs")
    suspend fun clearLogs()
}
