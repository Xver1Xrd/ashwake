package dev.ashwake.data.repository.character

import androidx.room.withTransaction
import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.Stat
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.entity.character.AppearancePresetEntity
import dev.ashwake.data.db.entity.character.AppearancePresetItemEntity
import dev.ashwake.data.db.entity.character.CharacterProfileEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.LedgerTransactionEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.StatEventEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.engine.character.StatProgressCalculator
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardEngine
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.character.Bulk
import dev.ashwake.domain.model.character.CharacterProfile
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.OwnedItem
import dev.ashwake.domain.model.character.StatValue
import dev.ashwake.domain.model.character.Wallet
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.character.RewardScope
import dev.ashwake.domain.repository.character.PurchaseResult
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
    private val catalogLoader: CatalogLoader,
    private val equipmentEngine: EquipmentEngine,
    private val statCalculator: StatProgressCalculator,
    private val rewardEngine: RewardEngine,
    private val clock: AppClock
) : CharacterRepository {

    override fun observeState(): Flow<CharacterState> = combine(
        dao.observeProfile(),
        dao.observeWallet(),
        dao.observeStats(),
        dao.observeOwned(),
        dao.observeEquipped()
    ) { profile, wallet, stats, owned, equipped ->
        buildState(profile, wallet, stats, owned, equipped)
    }

    override suspend fun state(): CharacterState = buildState(
        dao.profile(),
        dao.wallet(),
        dao.observeStats().first(),
        dao.observeOwned().first(),
        dao.equipped()
    )

    private suspend fun buildState(
        profile: CharacterProfileEntity?,
        wallet: WalletEntity?,
        stats: List<CharacterStatEntity>,
        owned: List<OwnedItemEntity>,
        equipped: List<EquippedItemEntity>
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
            equipment = equipment
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

    override suspend fun upgrade(itemId: String, cost: Int): PurchaseResult = db.withTransaction {
        val owned = dao.owned(itemId) ?: return@withTransaction PurchaseResult.NotForSale
        if (owned.upgradeLevel >= MAX_UPGRADE) return@withTransaction PurchaseResult.NotForSale
        val wallet = walletOrCreate()
        if (wallet.coins < cost) return@withTransaction PurchaseResult.NotEnoughCoins

        applyCoins(-cost.toLong(), "UPGRADE", itemId, 1f)
        dao.setUpgradeLevel(itemId, owned.upgradeLevel + 1)
        PurchaseResult.Success
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
            statCalculator.pointsFor(source, sphere).forEach { (stat, points) ->
                addStatPoints(stat, points, source.name, refId)
            }
        }
    }

    /**
     * Отмена начисления.
     *
     * Считается не «сколько полагалось», а сколько по этому событию сейчас
     * реально висит в журнале: множители экипировки на момент начисления
     * могли быть другими, а отменить надо ровно выданное. Сумма включает и
     * прошлые отмены, поэтому второй вызов подряд снимает ноль и ничего
     * не портит — а именно так и выглядит частое нажатие чекбокса.
     */
    override suspend fun revokeReward(scope: RewardScope, refId: String) {
        db.withTransaction {
            val ledgerSources = scope.ledgerSources()
            val statSources = scope.statSources()

            val coins = dao.netLedgerAmount(refId, CURRENCY_COIN, ledgerSources)
            if (coins != 0L) applyCoins(-coins, scope.revokeSource(), refId, 1f)

            val xp = dao.netLedgerAmount(refId, CURRENCY_XP, ledgerSources)
            if (xp != 0L) applyXp(-xp, scope.revokeSource(), refId)

            dao.netStatPoints(refId, statSources).forEach { row ->
                if (row.points == 0) return@forEach
                val stat = runCatching { Stat.valueOf(row.stat) }.getOrNull() ?: return@forEach
                addStatPoints(stat, -row.points, scope.revokeSource(), refId)
            }
        }
    }

    /**
     * Общий путь изменения характеристики: и начисление, и отмена.
     *
     * Итог не уходит ниже нуля — иначе отмена события, начисленного до
     * восстановления из бэкапа, увела бы характеристику в минус, а
     * `sqrt` от отрицательного не считается.
     */
    private suspend fun addStatPoints(stat: Stat, points: Int, source: String, refId: String?) {
        val existing = dao.stat(stat.name)
        val total = ((existing?.points ?: 0L) + points).coerceAtLeast(0L)
        dao.upsertStat(
            CharacterStatEntity(
                stat = stat.name,
                points = total,
                value = statCalculator.valueOf(total)
            )
        )
        dao.insertStatEvent(
            StatEventEntity(
                at = clock.now().toEpochMilli(),
                stat = stat.name,
                points = points,
                source = source,
                refId = refId
            )
        )
    }

    override suspend fun ensureBuiltinData() {
        db.withTransaction {
            val firstRun = dao.profile() == null
            if (firstRun) dao.upsertProfile(CharacterProfileEntity())
            walletOrCreate()
            Stat.entries.forEach { stat ->
                if (dao.stat(stat.name) == null) {
                    dao.upsertStat(CharacterStatEntity(stat = stat.name))
                }
            }
            if (firstRun) grantStarterKit()
        }
    }

    /**
     * Стартовый комплект.
     *
     * Голая фигура на главном экране — плохая первая встреча: непонятно, что
     * это вообще персонаж и зачем он тут. Поэтому при первом запуске человек
     * получает одетого героя и небольшой кошелёк — хватает на пару вещей из
     * магазина, чтобы попробовать, как работает покупка, но не настолько,
     * чтобы обесценить всё, что зарабатывается делами.
     *
     * Предметы кладутся во владение с источником `STARTER`: по нему видно,
     * что они не куплены и не выданы за достижение.
     */
    private suspend fun grantStarterKit() {
        val catalog = catalogLoader.load()
        val now = clock.now().toEpochMilli()

        STARTER_ITEMS.forEach { itemId ->
            val item = catalog.item(itemId) ?: return@forEach
            if (dao.owned(itemId) == null) {
                dao.insertOwned(
                    OwnedItemEntity(itemId = itemId, acquiredAt = now, source = "STARTER")
                )
            }
            dao.equip(EquippedItemEntity(slot = item.slot.name, itemId = itemId))
        }

        applyCoins(STARTER_COINS, "STARTER", null, 1f)
    }

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
                currency = CURRENCY_COIN,
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
                currency = CURRENCY_XP,
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

    /**
     * Источники, которыми начисляет событие. Знание о том, что закрытие
     * задачи трогает три источника очков, живёт здесь и только здесь.
     */
    private fun RewardScope.ledgerSources(): List<String> = when (this) {
        RewardScope.TASK -> listOf(RewardSource.TASK_DONE.name, revokeSource())
        RewardScope.HABIT -> listOf(
            RewardSource.HABIT_DONE.name,
            RewardSource.HABIT_MINIMUM.name,
            revokeSource()
        )
    }

    private fun RewardScope.statSources(): List<String> = when (this) {
        RewardScope.TASK -> listOf(
            StatSource.TASK_DONE.name,
            StatSource.TASK_ON_TIME.name,
            StatSource.STALE_TASK_CLOSED.name,
            revokeSource()
        )

        RewardScope.HABIT -> listOf(
            StatSource.HABIT_DONE.name,
            StatSource.HABIT_MINIMUM.name,
            StatSource.STREAK_DAY.name,
            revokeSource()
        )
    }

    /**
     * Источник встречной записи. У каждого вида события свой, чтобы отмена
     * задачи не попала в сумму по привычке с тем же номером.
     */
    private fun RewardScope.revokeSource(): String = when (this) {
        RewardScope.TASK -> "TASK_REWARD_REVOKED"
        RewardScope.HABIT -> "HABIT_REWARD_REVOKED"
    }

    private companion object {
        const val MAX_UPGRADE = 10

        const val CURRENCY_COIN = "COIN"
        const val CURRENCY_XP = "XP"

        /**
         * Комплект первого запуска: волосы, лицо и повседневная одежда.
         * Ни доспехов, ни оружия — их человек должен захотеть сам, иначе
         * магазину нечего предложить.
         */
        val STARTER_ITEMS = listOf(
            "hair_short",
            "face_clean",
            "under_shirt",
            "chest_hoodie",
            "legs_jeans",
            "boots_sneakers",
            "main_mug"
        )

        /** Стартовый кошелёк: примерно две недорогие вещи. */
        const val STARTER_COINS = 400L
    }
}
