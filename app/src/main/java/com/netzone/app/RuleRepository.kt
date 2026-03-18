package com.netzone.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Smart cache for Rules to avoid constant database reads and O(N*M) filtering.
 */
class RuleRepository(private val dao: RuleDao) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // In-memory cache of rules as a Map for O(1) lookups during filtering
    private val _rulesMap = MutableStateFlow<Map<String, Rule>>(emptyMap())
    val rulesMap: StateFlow<Map<String, Rule>> = _rulesMap.asStateFlow()

    init {
        // Sync cache with database updates reactively
        dao.getAllRules()
            .onEach { rules ->
                _rulesMap.value = rules.associateBy { it.packageName }
            }
            .launchIn(scope)
    }

    fun getAllRulesFlow(): Flow<List<Rule>> = dao.getAllRules()

    suspend fun getAllRules(): List<Rule> = dao.getAllRulesList()

    suspend fun updateRule(rule: Rule) {
        dao.insertRule(rule)
        // Flow will automatically update the map
    }

    fun getRuleFromCache(packageName: String): Rule? = _rulesMap.value[packageName]
    
    companion object {
        @Volatile
        private var INSTANCE: RuleRepository? = null

        fun getInstance(dao: RuleDao): RuleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleRepository(dao).also { INSTANCE = it }
            }
        }
    }
}
