package dev.ashwake.data.repository.character

import androidx.room.withTransaction
import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.Stat
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.dao.routines.RoutineDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.character.AchievementEntity
import dev.ashwake.data.db.entity.character.AppearancePresetEntity
import dev.ashwake.data.db.entity.character.AppearancePresetItemEntity
import dev.ashwake.data.db.entity.character.CharacterProfileEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.DailyChestEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.LedgerTransactionEntity
import dev.ashwake.data.db.entity.character.MaterialInventoryEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.StatEventEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.domain.engine.achievement.AchievementSnapshot
import dev.ashwake.domain.engine.abstinence.AbstinenceCalculator
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.engine.character.MaterialCost
import dev.ashwake.domain.engine.character.ChestReward
import dev.ashwake.domain.engine.character.StatProgressCalculator
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardEngine
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.character.AchievementState
import dev.ashwake.domain.model.character.Bulk
import dev.ashwake.domain.model.character.CharacterProfile
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.MaterialCount
import dev.ashwake.domain.model.character.MaterialType
import dev.ashwake.domain.model.character.OwnedItem
import dev.ashwake.domain.model.character.StatValue
import dev.ashwake.domain.model.character.Wallet
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.character.ChestState
import dev.ashwake.domain.repository.character.PurchaseResult
import dev.ashwake.domain.repository.character.UpgradeResult
import dev.ashwake.domain.repository.character.MAX_UPGRADE_LEVEL
import dev.ashwake.data.db.mapper.abstinence.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: CharacterDao,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val abstinenceDao: AbstinenceDao,
    private val ritualDao: RitualDao,
    private val routineDao: RoutineDao,
    private val focusDao: FocusDao,
    private val catalogLoader: CatalogLoader,
    private val equipmentEngine: EquipmentEngine,
    private val statCalculator: StatProgressCalculator,
    private val abstinenceCalculator: AbstinenceCalculator,
    private val rewardEngine: RewardEngine,
    private val clock: AppClock
) : CharacterRepository {

    override fun observeState(): Flow<CharacterState> = combine(
        dao.observeProfile(),
        dao.observeWallet(),
        dao.observeStats(),
        dao.observeOwned(),
        dao.observeEquipped(),
        dao.observeMaterials(),
        dao.observeAchievements()
    ) { values ->
        buildState(
            profile = values[0] as CharacterProfileEntity?,
            wallet = values[1] as WalletEntity?,
            stats = values[2] as List<CharacterStatEntity>,
            owned = values[3] as List<OwnedItemEntity>,
            equipped = values[4] as List<EquippedItemEntity>,
            materials = values[5] as List<MaterialInventoryEntity>,
            achievements = values[6] as List<AchievementEntity>
        )
    }

    override suspend fun state(): CharacterState = buildState(
        dao.profile(),
        dao.wallet(),
        dao.observeStats().first(),
        dao.observeOwned().first(),
        dao.equipped(),
        dao.observeMaterials().first(),
        dao.observeAchievements().first()
    )

    private suspend fun buildState(
        profile: CharacterProfileEntity?,
        wallet: WalletEntity?,
        stats: List<CharacterStatEntity>,
        owned: List<OwnedItemEntity>,
        equipped: List<EquippedItemEntity>,
        materials: List<MaterialInventoryEntity>,
        achievements: List<AchievementEntity>
    ): CharacterState {
        val catalog = catalogLoader.load()
        val ownedById = owned.associateBy { it.itemId }

        // Уровень апгрейда живёт во владении, а не в каталоге: каталог общий
        val equippedItems = equipped.mapNotNull { row ->
            catalog.item(row.itemId)?.let { item ->
                item.copy(upgradeLevel = ownedById[row.itemId]?.upgradeLevel ?: 0)
            }
        }

        val statValues = Stat.entries.map { stat ->
            val row = stats.firstOrNull { it.stat == stat.name }
            StatValue(stat, row?.points ?: 0L, row?.value ?: 0)
        }
        val statMap = statValues.associate { it.stat to it.value }

        val equipment = equipmentEngine.compute(
            equipped = equippedItems,
            affixes = catalog.affixes,
            sets = catalog.sets,
            stats = statMap
        )
        val xp = wallet?.xp ?: 0L

        return CharacterState(
            profile = profile?.toDomain() ?: CharacterProfile(),
            wallet = Wallet(wallet?.coins ?: 0L, xp, rewardEngine.levelForXp(xp)),
            level = rewardEngine.levelForXp(xp),
            levelProgress = rewardEngine.progressToNextLevel(xp),
            stats = statValues,
            equipped = equippedItems.associateBy { it.slot },
            owned = owned.map { OwnedItem(it.id, it.itemId, it.upgradeLevel, it.favorite, it.source) },
            equipment = equipment,
            materials = materials.mapNotNull { row ->
                MaterialType.entries.firstOrNull { it.name == row.materialId }
                    ?.let { MaterialCount(it, row.amount) }
            },
            achievements = achievements.map {
                AchievementState(id = it.id, unlockedAt = it.unlockedAt, progress = it.progress)
            }
        )
    }

    override suspend fun updateProfile(profile: CharacterProfile) {
        dao.upsertProfile(profile.toEntity())
    }

    override suspend fun equip(itemId: String): Boolean = db.withTransaction {
        val catalog = catalogLoader.load()
        val item = catalog.item(itemId) ?: return@withTransaction false
        // Бесплатные слоты (волосы, лицо) надеваются без покупки
        if (item.price != 0 && dao.owned(itemId) == null) return@withTransaction false
        dao.equip(EquippedItemEntity(slot = item.slot.name, itemId = itemId))
        true
    }

    override suspend fun unequip(slot: EquipSlot) = dao.unequip(slot.name)

    override suspend fun unequipAll() = dao.unequipAll()

    override suspend fun buy(itemId: String): PurchaseResult = db.withTransaction {
        val catalog = catalogLoader.load()
        val item = catalog.item(itemId) ?: return@withTransaction PurchaseResult.NotForSale
        val price = item.price ?: return@withTransaction PurchaseResult.NotForSale
        if (dao.owned(itemId) != null) return@withTransaction PurchaseResult.AlreadyOwned

        val stats = currentStatMap()
        val missing = equipmentEngine.missingRequirements(item, stats)
        // Требования проверяются до списания: предмет, который нельзя надеть,
        // не должен молча съедать монеты
        if (missing.isNotEmpty()) {
            return@withTransaction PurchaseResult.RequirementsNotMet(missing)
        }

        val wallet = walletOrCreate()
        if (wallet.coins < price) return@withTransaction PurchaseResult.NotEnoughCoins

        applyCoins(-price.toLong(), "PURCHASE", itemId, 1f)
        dao.insertOwned(
            OwnedItemEntity(
                itemId = itemId,
                acquiredAt = clock.now().toEpochMilli(),
                source = "SHOP"
            )
        )
        PurchaseResult.Success
    }

    override suspend fun upgrade(itemId: String, coinCost: Int): UpgradeResult =
        db.withTransaction {
            val catalog = catalogLoader.load()
            val owned = dao.owned(itemId) ?: return@withTransaction UpgradeResult.NotForSale
            if (owned.upgradeLevel >= MAX_UPGRADE_LEVEL) return@withTransaction UpgradeResult.NotForSale
            val item = catalog.item(itemId) ?: return@withTransaction UpgradeResult.NotForSale

            // Материалы проверяются до монет: списывать одно без другого нельзя
            val required = MaterialCost.forRarity(item.rarity)
            val missing = MaterialCost.missing(required, currentMaterials())
            if (missing.isNotEmpty()) {
                return@withTransaction UpgradeResult.NotEnoughMaterials(missing)
            }

            val wallet = walletOrCreate()
            if (wallet.coins < coinCost) return@withTransaction UpgradeResult.NotEnoughCoins

            applyCoins(-coinCost.toLong(), "UPGRADE", itemId, 1f)
            spendMaterialsQuietly(required)
            dao.setUpgradeLevel(itemId, owned.upgradeLevel + 1)
            UpgradeResult.Success
        }

    override suspend fun savePreset(index: Int, name: String) {
        db.withTransaction {
            val presetId = dao.upsertPreset(
                AppearancePresetEntity(id = index.toLong(), name = name, position = index)
            ).let { if (it == -1L) index.toLong() else it }

            dao.clearPresetItems(presetId)
            dao.insertPresetItems(
                dao.equipped().map {
                    AppearancePresetItemEntity(presetId, it.slot, it.itemId)
                }
            )
        }
    }

    override suspend fun applyPreset(presetId: Long) {
        db.withTransaction {
            val items = dao.presetItems(presetId)
            if (items.isEmpty()) return@withTransaction
            dao.unequipAll()
            items.forEach { dao.equip(EquippedItemEntity(it.slot, it.itemId)) }
        }
    }

    /**
     * Начисление за событие.
     *
     * Множители экипировки подставляются здесь, а не в вызывающем коде:
     * иначе каждый экран считал бы бонусы по-своему и баланс разъехался бы.
     */
    override suspend fun grantReward(context: RewardContext, refId: String?) {
        db.withTransaction {
            val effects = state().equipment?.effects.orEmpty()
            val reward = rewardEngine.reward(context.copy(equipmentEffects = effects))
            if (reward.isEmpty) return@withTransaction

            if (reward.coins != 0) {
                applyCoins(
                    reward.coins.toLong(), context.source.name, refId, reward.multiplier
                )
            }
            if (reward.xp != 0) {
                applyXp(reward.xp.toLong(), context.source.name, refId)
            }
        }
    }

    override suspend fun grantStatPoints(source: StatSource, sphere: Sphere?, refId: String?) {
        db.withTransaction {
            val now = clock.now().toEpochMilli()
            statCalculator.pointsFor(source, sphere).forEach { (stat, points) ->
                val existing = dao.stat(stat.name)
                val total = (existing?.points ?: 0L) + points
                dao.upsertStat(
                    CharacterStatEntity(
                        stat = stat.name,
                        points = total,
                        value = statCalculator.valueOf(total)
                    )
                )
                dao.insertStatEvent(
                    StatEventEntity(
                        at = now, stat = stat.name, points = points,
                        source = source.name, refId = refId
                    )
                )
            }
        }
    }

    override suspend fun ensureBuiltinData() {
        db.withTransaction {
            if (dao.profile() == null) dao.upsertProfile(CharacterProfileEntity())
            walletOrCreate()
            Stat.entries.forEach { stat ->
                if (dao.stat(stat.name) == null) {
                    dao.upsertStat(CharacterStatEntity(stat = stat.name))
                }
            }
            MaterialType.entries.forEach { type ->
                if (dao.material(type.name) == null) {
                    dao.upsertMaterial(MaterialInventoryEntity(materialId = type.name))
                }
            }
        }
    }

    // --- материалы ----------------------------------------------------------

    override fun observeMaterials(): Flow<List<MaterialCount>> =
        dao.observeMaterials().map { rows ->
            rows.mapNotNull { row ->
                MaterialType.entries.firstOrNull { it.name == row.materialId }
                    ?.let { MaterialCount(it, row.amount) }
            }
        }

    override suspend fun grantMaterial(type: MaterialType, amount: Int) {
        if (amount <= 0) return
        db.withTransaction {
            val row = dao.material(type.name) ?: MaterialInventoryEntity(type.name)
            dao.upsertMaterial(row.copy(amount = row.amount + amount))
        }
    }

    override suspend fun spendMaterials(required: Map<MaterialType, Int>): Boolean =
        db.withTransaction {
            val missing = MaterialCost.missing(required, currentMaterials())
            if (missing.isNotEmpty()) return@withTransaction false
            spendMaterialsQuietly(required)
            true
        }

    private suspend fun spendMaterialsQuietly(required: Map<MaterialType, Int>) {
        required.forEach { (type, amount) ->
            val row = dao.material(type.name) ?: return@forEach
            dao.upsertMaterial(row.copy(amount = (row.amount - amount).coerceAtLeast(0)))
        }
    }

    private suspend fun currentMaterials(): Map<MaterialType, Int> =
        MaterialType.entries.associateWith { type ->
            dao.material(type.name)?.amount ?: 0
        }

    // --- достижения ---------------------------------------------------------

    override fun observeAchievements(): Flow<List<AchievementState>> =
        dao.observeAchievements().map { rows ->
            rows.map { AchievementState(id = it.id, unlockedAt = it.unlockedAt, progress = it.progress) }
        }

    override suspend fun achievementSnapshot(): AchievementSnapshot {
        val dayStart = clock.today().toEpochDay().toLong()
        val nowMillis = clock.now().toEpochMilli()

        // Чистые дни отказа: сумма по всем отказам, как считает движок отказов
        val abstinences = abstinenceDao.allAbstinences()
        val attemptsByAbstinence = abstinenceDao.allAttempts().groupBy { it.abstinenceId }
        val cleanDays = abstinences.sumOf { item ->
            val attempts = attemptsByAbstinence[item.id].orEmpty().map { it.toDomain() }
            val abstinence = item.toDomain(attempts)
            abstinenceCalculator.stats(abstinence, clock.now()).totalCleanDays
        }

        return AchievementSnapshot(
            tasksDone = taskDao.countDone(),
            tasksDoneToday = taskDao.countDoneSince(dayStartMillis(dayStart)),
            habitsDone = habitDao.countDoneEntries(),
            cravingsResisted = abstinenceDao.countResistedCravings(),
            focusMinutes = focusDao.totalFocusSeconds() / 60,
            routinesDone = routineDao.countCompletedSessions(),
            ritualsDone = ritualDao.countReviews(),
            abstinenceDays = cleanDays,
            coinsEarned = dao.coinsEarned(),
            itemsOwned = dao.countOwned(),
            level = rewardEngine.levelForXp(dao.wallet()?.xp ?: 0L).toLong()
        )
    }

    override suspend fun unlockAchievement(id: String, at: Long): Boolean =
        db.withTransaction {
            val existing = dao.achievement(id)
            if (existing?.unlockedAt != null) return@withTransaction false
            dao.upsertAchievement(
                AchievementEntity(
                    id = id,
                    unlockedAt = existing?.unlockedAt ?: at,
                    progress = 1f
                )
            )
            true
        }

    // --- ежедневный сундук -------------------------------------------------

    override fun observeChest(epochDay: Int): Flow<ChestState> =
        dao.observeChest(epochDay).map { row ->
            if (row?.openedAt == null) {
                ChestState(opened = false)
            } else {
                ChestState(opened = true, reward = rewardFromJson(row.rewardJson))
            }
        }

    override suspend fun chestState(epochDay: Int): ChestState {
        val row = dao.chest(epochDay)
        return if (row?.openedAt == null) {
            ChestState(opened = false)
        } else {
            ChestState(opened = true, reward = rewardFromJson(row.rewardJson))
        }
    }

    override suspend fun applyChest(reward: ChestReward, epochDay: Int) {
        if (reward.isEmpty) return
        db.withTransaction {
            if (dao.chest(epochDay)?.openedAt != null) return@withTransaction
            val at = clock.now().toEpochMilli()
            dao.upsertChest(
                DailyChestEntity(
                    date = epochDay,
                    openedAt = at,
                    rewardJson = rewardToJson(reward)
                )
            )
            // Монеты сундука тоже проходят через движок: множители экипировки
            // применяются к ним как ко всем остальным начислениям
            grantReward(
                RewardContext(
                    source = RewardSource.CHEST,
                    time = clock.now().atZone(clock.zone()).toLocalTime(),
                    flatCoins = reward.coins
                ),
                refId = "chest:$epochDay"
            )
            reward.materials.forEach { grantMaterial(it.type, it.amount) }
            reward.itemId?.let { itemId ->
                dao.insertOwned(
                    OwnedItemEntity(itemId = itemId, acquiredAt = at, source = "CHEST")
                )
            }
        }
    }

    private fun rewardToJson(reward: ChestReward): String {
        val json = org.json.JSONObject().apply {
            put("coins", reward.coins)
            put("materials", org.json.JSONArray().apply {
                reward.materials.forEach { put(org.json.JSONObject().apply {
                    put("id", it.type.name)
                    put("amount", it.amount)
                }) }
            })
            reward.itemId?.let { put("itemId", it) }
        }
        return json.toString()
    }

    private fun rewardFromJson(json: String?): ChestReward? {
        if (json == null) return null
        return runCatching {
            val root = org.json.JSONObject(json)
            val materials = root.optJSONArray("materials")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.getJSONObject(index)
                    MaterialType.entries.firstOrNull { it.name == item.getString("id") }
                        ?.let { MaterialCount(it, item.getInt("amount")) }
                }
            }.orEmpty()
            ChestReward(
                coins = root.optInt("coins", 0),
                materials = materials,
                itemId = root.optString("itemId").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    private fun dayStartMillis(epochDay: Long): Long =
        java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(clock.zone()).toInstant().toEpochMilli()

    // --- вспомогательное ----------------------------------------------------

    private suspend fun currentStatMap(): Map<Stat, Int> =
        Stat.entries.associateWith { dao.stat(it.name)?.value ?: 0 }

    private suspend fun walletOrCreate(): WalletEntity =
        dao.wallet() ?: WalletEntity().also { dao.upsertWallet(it) }

    private suspend fun applyCoins(
        amount: Long,
        source: String,
        refId: String?,
        multiplier: Float
    ) {
        val wallet = walletOrCreate()
        val balance = (wallet.coins + amount).coerceAtLeast(0L)
        dao.upsertWallet(wallet.copy(coins = balance))
        dao.insertTransaction(
            LedgerTransactionEntity(
                at = clock.now().toEpochMilli(),
                currency = "COIN",
                amount = amount,
                source = source,
                refId = refId,
                multiplierApplied = multiplier,
                balanceAfter = balance
            )
        )
    }

    private suspend fun applyXp(amount: Long, source: String, refId: String?) {
        val wallet = walletOrCreate()
        val xp = (wallet.xp + amount).coerceAtLeast(0L)
        dao.upsertWallet(wallet.copy(xp = xp, level = rewardEngine.levelForXp(xp)))
        dao.insertTransaction(
            LedgerTransactionEntity(
                at = clock.now().toEpochMilli(),
                currency = "XP",
                amount = amount,
                source = source,
                refId = refId,
                balanceAfter = xp
            )
        )
    }

    private fun CharacterProfileEntity.toDomain() = CharacterProfile(
        name = name,
        body = Bulk.valueOf(body),
        skinToneId = skinToneId,
        hairStyleId = hairStyleId,
        hairColorId = hairColorId,
        faceId = faceId,
        reduceMotion = reduceMotion,
        decayEnabled = decayEnabled,
        parallaxEnabled = parallaxEnabled
    )

    private fun CharacterProfile.toEntity() = CharacterProfileEntity(
        name = name,
        body = body.name,
        skinToneId = skinToneId,
        hairStyleId = hairStyleId,
        hairColorId = hairColorId,
        faceId = faceId,
        reduceMotion = reduceMotion,
        decayEnabled = decayEnabled,
        parallaxEnabled = parallaxEnabled
    )
}
