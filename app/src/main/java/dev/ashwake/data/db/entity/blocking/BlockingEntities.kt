package dev.ashwake.data.db.entity.blocking

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Правило блокировки (п. 7). По умолчанию выключено. */
@Entity(tableName = "blocking_rules")
data class BlockingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = false,
    val conditionType: String,
    val refRoutineId: Long? = null,
    val timeMinute: Int? = null,
    val createdAt: Long
)

@Entity(
    tableName = "blocked_apps",
    primaryKeys = ["ruleId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockingRuleEntity::class,
            parentColumns = ["id"], childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlockedAppEntity(
    val ruleId: Long,
    val packageName: String
)

/**
 * Лог экстренных обходов.
 *
 * Обход возможен всегда — запирать человека в собственном телефоне нельзя.
 * Но он не бесследен: запись остаётся, и по ней видно, работает правило
 * или превратилось в формальность.
 */
@Entity(tableName = "bypass_log", indices = [Index("at")])
data class BypassLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val packageName: String,
    val ruleId: Long? = null,
    val delaySeconds: Int
)
