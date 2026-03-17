package com.netzone.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun getAllRules(): Flow<List<Rule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: Rule)

    @Delete
    suspend fun deleteRule(rule: Rule)

    @Query("SELECT * FROM rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): Rule?
}
