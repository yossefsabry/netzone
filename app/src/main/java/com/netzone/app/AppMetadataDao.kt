package com.netzone.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata ORDER BY name ASC")
    fun getAllApps(): Flow<List<AppMetadata>>

    @Query("SELECT * FROM app_metadata ORDER BY name ASC")
    suspend fun getAllAppsList(): List<AppMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppMetadata>)

    @Query("DELETE FROM app_metadata")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(apps: List<AppMetadata>) {
        deleteAll()
        insertApps(apps)
    }
}
