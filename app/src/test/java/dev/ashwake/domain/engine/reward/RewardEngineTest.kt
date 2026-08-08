package dev.ashwake.domain.engine.reward

import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.Sphere
import dev.ashwake.domain.engine.character.EffectKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class RewardEngineTest {

    private val config = RewardConfig.DEFAULT
    private val engine = RewardEngine(config)

    @Test
    fun `монеты за задачу зависят от приоритета`() {
        val p1 = engine.reward(RewardContext(RewardSource.TASK_DONE, priority = Priority.P1))
        val p4 = engine.reward(RewardContext(RewardSource.TASK_DONE, priority = Priority.P4))

        assertEquals(config.taskCoinsByPriority[Priority.P1], p1.coins)
        assertTrue(p1.coins > p4.coins)
    }

    @Test
    fun `закрытие залежавшейся задачи даёт бонус`() {
        val normal = engine.reward(RewardContext(RewardSource.TASK_DONE, priority = Priority.P3))
        val stale = engine.reward(
            RewardContext(RewardSource.TASK_DONE, priority = Priority.P3, postponeCount = 5)
        )
        assertEquals(normal.coins + config.staleTaskBonus, stale.coins)
    }

    @Test
    fun `привычка с высоким score приносит больше`() {
        val low = engine.reward(RewardContext(RewardSource.HABIT_DONE, habitScore = 0f))
        val high = engine.reward(RewardContext(RewardSource.HABIT_DONE, habitScore = 1f))

        assertTrue("score должен влиять на награду", high.coins > low.coins)
    }

    @Test
    fun `минимум даёт меньше полного выполнения`() {
        val full = engine.reward(RewardContext(RewardSource.HABIT_DONE, habitScore = 1f))
        val minimum = engine.reward(RewardContext(RewardSource.HABIT_MINIMUM))
        assertTrue(minimum.coins < full.coins)
    }

    @Test
    fun `множители экипировки складываются аддитивно`() {
        val reward = engine.reward(
            RewardContext(
                source = RewardSource.TASK_DONE,
                priority = Priority.P1,
                equipmentEffects = mapOf(EffectKeys.COIN_MULT to 50f, EffectKeys.XP_MULT to 0f)
            )
        )
        assertEquals(1.5f, reward.multiplier, 0.001f)
        assertEquals((config.taskCoinsByPriority[Priority.P1]!! * 1.5f).toInt(), reward.coins)
    }

    @Test
    fun `сферный множитель применяется только к своей сфере`() {
        val effects = mapOf("${EffectKeys.COIN_MULT_SPHERE}:SPORT" to 30f)
        val sport = engine.reward(
            RewardContext(RewardSource.HABIT_DONE, sphere = Sphere.SPORT,
                habitScore = 1f, equipmentEffects = effects)
        )
        val study = engine.reward(
            RewardContext(RewardSource.HABIT_DONE, sphere = Sphere.STUDY,
                habitScore = 1f, equipmentEffects = effects)
        )
        assertEquals(1.3f, sport.multiplier, 0.001f)
        assertEquals(1f, study.multiplier, 0.001f)
    }

    @Test
    fun `утренний множитель работает до десяти утра`() {
        val effects = mapOf(EffectKeys.COIN_MULT_MORNING to 20f)
        val morning = engine.reward(
            RewardContext(RewardSource.TASK_DONE, priority = Priority.P2,
                time = LocalTime.of(8, 0), equipmentEffects = effects)
        )
        val noon = engine.reward(
            RewardContext(RewardSource.TASK_DONE, priority = Priority.P2,
                time = LocalTime.of(13, 0), equipmentEffects = effects)
        )
        assertEquals(1.2f, morning.multiplier, 0.001f)
        assertEquals(1f, noon.multiplier, 0.001f)
    }

    @Test
    fun `потолок множителя монет не превышается`() {
        val reward = engine.reward(
            RewardContext(
                source = RewardSource.TASK_DONE,
                priority = Priority.P1,
                equipmentEffects = mapOf(EffectKeys.COIN_MULT to 500f)
            )
        )
        // Потолок +200% → множитель 3.0
        assertEquals(3f, reward.multiplier, 0.001f)
    }

    @Test
    fun `отрицательный множитель не уводит награду в минус`() {
        val reward = engine.reward(
            RewardContext(
                source = RewardSource.TASK_DONE,
                priority = Priority.P1,
                equipmentEffects = mapOf(EffectKeys.COIN_MULT to -500f)
            )
        )
        assertTrue(reward.coins >= 0)
        assertTrue(reward.multiplier >= 0f)
    }

    @Test
    fun `безупречная рутина даёт бонус`() {
        val normal = engine.reward(RewardContext(RewardSource.ROUTINE_DONE))
        val flawless = engine.reward(RewardContext(RewardSource.ROUTINE_DONE, flawless = true))
        assertEquals(normal.coins + config.routineFlawlessBonus, flawless.coins)
    }

    @Test
    fun `списание возвращает отрицательные монеты`() {
        val charge = engine.charge(150, RewardSource.PURCHASE)
        assertEquals(-150, charge.coins)
        assertEquals(0, charge.xp)
    }

    @Test
    fun `уровень растёт с накоплением опыта`() {
        assertEquals(1, engine.levelForXp(0))
        assertTrue(engine.levelForXp(10_000) > engine.levelForXp(500))
    }

    @Test
    fun `прогресс до следующего уровня в пределах нуля и единицы`() {
        listOf(0L, 50L, 500L, 5_000L).forEach { xp ->
            val progress = engine.progressToNextLevel(xp)
            assertTrue("xp=$xp progress=$progress", progress in 0f..1f)
        }
    }

    @Test
    fun `покупка и корректировка сами по себе монет не приносят`() {
        assertEquals(0, engine.reward(RewardContext(RewardSource.PURCHASE)).coins)
        assertEquals(0, engine.reward(RewardContext(RewardSource.ADJUST)).coins)
    }
}
