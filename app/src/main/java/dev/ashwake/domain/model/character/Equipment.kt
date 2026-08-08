package dev.ashwake.domain.model.character

import dev.ashwake.core.model.Stat

/**
 * Слоты и z-порядок (п. 15.2). Шаг 10 — чтобы вставлять новое между.
 *
 * CLOAK и OFFHAND разбиты на back/front намеренно: плащ обязан обтекать тело,
 * иначе персонаж выглядит аппликацией.
 */
enum class EquipSlot(val layer: Int, val title: String) {
    BACKGROUND_FX(10, "Аура"),
    BACK_ITEM(20, "За спиной"),
    CLOAK_BACK(30, "Плащ сзади"),
    OFFHAND_BACK(40, "Левая рука сзади"),
    BODY(50, "Тело"),
    UNDERLAYER(60, "Поддоспешник"),
    BOOTS(70, "Обувь"),
    LEGS(80, "Ноги"),
    CHEST(90, "Корпус"),
    BELT(100, "Пояс"),
    GLOVES(110, "Перчатки"),
    HEAD_HAIR(120, "Волосы"),
    FACE(130, "Лицо"),
    HELMET(140, "Голова"),
    CLOAK_FRONT(150, "Плащ спереди"),
    MAINHAND(160, "Основная рука"),
    SHOULDERS(170, "Плечи"),
    FOREGROUND_FX(180, "Эффекты");

    /** Слоты, которые пользователь выбирает сам. Остальные — служебные части. */
    val isUserFacing: Boolean
        get() = this !in setOf(BODY, CLOAK_BACK, OFFHAND_BACK)
}

enum class Rarity(val budget: Int, val maxEffects: Int, val title: String) {
    COMMON(10, 3, "обычный"),
    UNCOMMON(20, 3, "необычный"),
    RARE(35, 3, "редкий"),
    EPIC(55, 3, "эпический"),
    LEGENDARY(80, 3, "легендарный"),
    RELIC(110, 4, "реликвия")
}

enum class Style(val title: String) {
    CASUAL("повседневное"), STREET("уличное"), HOME("домашнее"), SPORT("спорт"),
    WORK("рабочее"), TRAVELER("странник"), ARMOR("броня"), ROBE("роба"),
    RITUAL("ритуал"), CURSED("проклятое")
}

/** Объём корпуса и ног: от него зависит, какой кадр ширины берут перчатки и наплечники. */
enum class Bulk { SLIM, NORMAL, BULKY }

data class ItemEffect(val key: String, val value: Float)

/**
 * Предмет каталога.
 *
 * `baseSpriteId` — один файл на все палитры: цветовые варианты делаются
 * LUT-перекраской, а не отдельными спрайтами (п. 16.1).
 */
data class EquipItem(
    val id: String,
    val baseSpriteId: String,
    val slot: EquipSlot,
    val layer: Int = slot.layer,
    val frames: Int = 1,
    val hides: List<EquipSlot> = emptyList(),
    val dimsFace: Boolean = false,
    val paletteId: String = "default",
    val bulk: Bulk = Bulk.NORMAL,
    val style: Style = Style.CASUAL,
    val setTag: String? = null,
    val rarity: Rarity = Rarity.COMMON,
    /** null — не продаётся, выдаётся за достижение. */
    val price: Int? = null,
    val unlockCondition: String? = null,
    val requires: Map<Stat, Int> = emptyMap(),
    val effects: List<ItemEffect> = emptyList(),
    val prefixId: String? = null,
    val suffixId: String? = null,
    val upgradeLevel: Int = 0,
    val name: String,
    val lore: String = ""
)

/** Префикс или суффикс: меняет характеристики и имя, графику не трогает (п. 16.5.4). */
data class Affix(
    val id: String,
    val isPrefix: Boolean,
    val label: String,
    /** Множитель бюджета: «Потёртый» отнимает 20%, «Древний» добавляет. */
    val budgetMultiplier: Float = 1f,
    val priceMultiplier: Float = 1f,
    val effects: List<ItemEffect> = emptyList(),
    val statBonus: Map<Stat, Int> = emptyMap(),
    /** Стили, к которым аффикс применим. Пусто — к любым. */
    val styles: List<Style> = emptyList()
)

/** Сет: предметы с одним тегом. Бонусы за 2 / 4 / 6 частей (п. 16.5.5). */
data class ItemSet(
    val tag: String,
    val title: String,
    val style: Style,
    val bonus2: List<ItemEffect> = emptyList(),
    val bonus4: List<ItemEffect> = emptyList(),
    val bonus6: List<ItemEffect> = emptyList()
)

/** Палитра для LUT-перекраски: один спрайт даёт несколько предметов каталога. */
data class Palette(
    val id: String,
    val title: String,
    /** Исходный цвет → целевой. Ключи и значения — ARGB. */
    val mapping: Map<Int, Int> = emptyMap()
)

/** Предмет во владении: каталожная запись плюс то, что принадлежит игроку. */
data class OwnedItem(
    val id: Long = 0,
    val itemId: String,
    val upgradeLevel: Int = 0,
    val favorite: Boolean = false,
    val source: String = "SHOP"
)

data class CharacterProfile(
    val name: String = "Безымянный",
    val body: Bulk = Bulk.NORMAL,
    val skinToneId: String = "skin_02",
    val hairStyleId: String = "hair_short",
    val hairColorId: String = "hair_black",
    val faceId: String? = null,
    val reduceMotion: Boolean = false,
    val decayEnabled: Boolean = true,
    val parallaxEnabled: Boolean = true
)

data class Wallet(val coins: Long = 0, val xp: Long = 0, val level: Int = 1)

data class StatValue(val stat: Stat, val points: Long, val value: Int)
