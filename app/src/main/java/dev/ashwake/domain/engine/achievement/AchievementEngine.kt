package dev.ashwake.domain.engine.achievement

import dev.ashwake.domain.model.character.MaterialType
import javax.inject.Inject

/** Счётчик, за которым следит достижение. Значение — из снимка базы. */
enum class AchievementCounter {
    TASKS_DONE,
    TASKS_DONE_TODAY,
    HABITS_DONE,
    CRAVINGS_RESISTED,
    FOCUS_MINUTES,
    ROUTINES_DONE,
    RITUALS_DONE,
    ABSTINENCE_DAYS,
    COINS_EARNED,
    ITEMS_OWNED,
    LEVEL
}

/** Определение достижения из assets/catalog/achievements.json. */
data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val counter: AchievementCounter,
    val target: Long,
    val rewardCoins: Int = 0,
    val rewardXp: Int = 0,
    val rewardMaterial: MaterialType? = null,
    val rewardMaterialAmount: Int = 1
) {
    val progressLabel: String get() = "$counter ${target}ед."
}

/**
 * Все счётчики одним снимком. Строится в репозитории из базы —
 * движку важен только сам факт «сколько уже накоплено».
 */
data class AchievementSnapshot(
    val tasksDone: Long = 0,
    val tasksDoneToday: Long = 0,
    val habitsDone: Long = 0,
    val cravingsResisted: Long = 0,
    val focusMinutes: Long = 0,
    val routinesDone: Long = 0,
    val ritualsDone: Long = 0,
    val abstinenceDays: Long = 0,
    val coinsEarned: Long = 0,
    val itemsOwned: Long = 0,
    val level: Long = 1
) {
    fun valueOf(counter: AchievementCounter): Long = when (counter) {
        AchievementCounter.TASKS_DONE -> tasksDone
        AchievementCounter.TASKS_DONE_TODAY -> tasksDoneToday
        AchievementCounter.HABITS_DONE -> habitsDone
        AchievementCounter.CRAVINGS_RESISTED -> cravingsResisted
        AchievementCounter.FOCUS_MINUTES -> focusMinutes
        AchievementCounter.ROUTINES_DONE -> routinesDone
        AchievementCounter.RITUALS_DONE -> ritualsDone
        AchievementCounter.ABSTINENCE_DAYS -> abstinenceDays
        AchievementCounter.COINS_EARNED -> coinsEarned
        AchievementCounter.ITEMS_OWNED -> itemsOwned
        AchievementCounter.LEVEL -> level
    }
}

/**
 * Проверка условий достижений (п. 16.9).
 *
 * Чистый класс: каталог, снимок и список открытых на входе, список
 * достижений, которые надо открыть, на выходе. База не трогается.
 */
class AchievementEngine @Inject constructor() {

    fun progress(def: AchievementDefinition, snapshot: AchievementSnapshot): Float {
        val value = snapshot.valueOf(def.counter)
        return (value.toFloat() / def.target).coerceIn(0f, 1f)
    }

    fun isUnlocked(def: AchievementDefinition, snapshot: AchievementSnapshot): Boolean =
        snapshot.valueOf(def.counter) >= def.target

    /**
     * Достижения, условия которых выполнены, но которые ещё не открыты.
     * @param definitions каталог; @param unlockedIds уже открытые id.
     */
    fun newlyUnlocked(
        definitions: List<AchievementDefinition>,
        snapshot: AchievementSnapshot,
        unlockedIds: Set<String>
    ): List<AchievementDefinition> =
        definitions
            .filter { it.id !in unlockedIds }
            .filter { isUnlocked(it, snapshot) }
}
