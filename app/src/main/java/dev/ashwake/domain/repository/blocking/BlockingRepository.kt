package dev.ashwake.domain.repository.blocking

import dev.ashwake.domain.engine.blocking.BlockingContext
import dev.ashwake.domain.model.blocking.BlockingRule
import dev.ashwake.domain.model.blocking.BlockingVerdict
import dev.ashwake.domain.model.blocking.BypassRecord
import kotlinx.coroutines.flow.Flow

interface BlockingRepository {

    fun observeRules(): Flow<List<BlockingRule>>

    suspend fun upsertRule(rule: BlockingRule): Long

    suspend fun deleteRule(id: Long)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Есть ли хоть одно включённое правило: по нему решается, поднимать ли сервис. */
    suspend fun hasEnabledRules(): Boolean

    /** Контекст на сейчас: невыполненные утренние привычки, пройденные рутины, обход. */
    suspend fun currentContext(): BlockingContext

    suspend fun verdictFor(packageName: String): BlockingVerdict

    /** Экстренный обход с записью в лог (п. 7). */
    suspend fun registerBypass(packageName: String, ruleId: Long?, delaySeconds: Int)

    fun observeBypasses(): Flow<List<BypassRecord>>
}
