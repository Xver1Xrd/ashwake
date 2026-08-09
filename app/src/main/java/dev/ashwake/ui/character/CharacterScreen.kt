package dev.ashwake.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.engine.character.EffectKeys
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.PixelCharacter
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.Steel
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: CharacterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Предпросмотр подменяет надетый предмет в своём слоте — видно сразу при выделении
    val displayed = remember(state.equipped, preview) {
        val base = state.equipped.toMutableMap()
        preview?.let { base[it.slot] = it }
        base
    }

    val layers = remember(displayed, catalog) {
        buildLayers(displayed.values.toList(), catalog.paletteTints)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profile.name) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.character_nastroyki))
                    }
                    Text(
                        "${state.wallet.coins} ◈",
                        style = MaterialTheme.typography.titleMedium,
                        color = Gold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    PixelCharacter(
                        layers = layers,
                        modifier = Modifier.fillMaxSize(),
                        reduceMotion = state.profile.reduceMotion
                    )
                    if (layers.isEmpty()) {
                        Text(
                            "Наденьте что-нибудь из магазина ниже",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            item { LevelBlock(state.level, state.levelProgress, state.wallet.xp) }

            item { StatsBlock(state) }

            item { ActiveEffectsBlock(state) }

            item { PresetsRow(viewModel) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::savePortrait) { Text(stringResource(R.string.character_sohranit_portret)) }
                    TextButton(onClick = viewModel::sharePortrait) { Text(stringResource(R.string.character_podelitsya)) }
                }
            }

            item {
                HorizontalDivider()
                Text(stringResource(R.string.character_magazin), style = MaterialTheme.typography.titleMedium)
                ShopFilters(viewModel, filter)
            }

            val visibleItems = viewModel.visibleItems()
            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        "По этим фильтрам ничего нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visibleItems, key = { it.id }) { item ->
                ShopRow(
                    item = item,
                    owned = viewModel.isOwned(item),
                    equipped = state.equipped[item.slot]?.id == item.id,
                    missing = viewModel.missingRequirements(item),
                    tint = catalog.paletteTints[item.paletteId],
                    upgradeCost = viewModel.upgradeCost(item),
                    onPreview = { viewModel.preview(item) },
                    onEquip = { viewModel.equip(item) },
                    onBuy = { viewModel.buy(item) },
                    onUpgrade = { viewModel.upgrade(item) }
                )
            }
        }
    }
}

@Composable
private fun LevelBlock(level: Int, progress: Float, xp: Long) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Уровень $level", style = MaterialTheme.typography.titleSmall)
            Text(
                "$xp XP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsBlock(state: dev.ashwake.domain.repository.character.CharacterState) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.character_harakteristiki), style = MaterialTheme.typography.titleSmall)
        Text(
            "Растут от поведения, а не за монеты",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.stats.forEach { stat ->
                val bonus = state.equipment?.statBonuses?.get(stat.stat) ?: 0
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            statTitle(stat.stat) + " " + (stat.value + bonus) +
                                if (bonus > 0) " (+$bonus)" else ""
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveEffectsBlock(state: dev.ashwake.domain.repository.character.CharacterState) {
    val equipment = state.equipment ?: return
    if (equipment.effects.isEmpty() && equipment.activeSets.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.character_aktivnye_bonusy), style = MaterialTheme.typography.titleSmall)
        equipment.activeSets.forEach { active ->
            Text(
                "Сет «${active.set.title}» — ${active.pieces} част${if (active.pieces == 1) "ь" else "и"}: " +
                    active.tiers.joinToString("/") { "$it" },
                style = MaterialTheme.typography.labelMedium,
                color = Moss
            )
        }
        equipment.effects.entries.sortedBy { it.key }.forEach { (key, value) ->
            Text(
                "${effectTitle(key)}: ${formatEffect(key, value)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (value < 0) Ember else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (equipment.blockedItems.isNotEmpty()) {
            Text(
                "Не работают из-за требований: " +
                    equipment.blockedItems.joinToString { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PresetsRow(viewModel: CharacterViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.character_obrazy), style = MaterialTheme.typography.labelLarge)
        (0..2).forEach { index ->
            AssistChip(
                onClick = { viewModel.applyPreset(index) },
                label = { Text("${index + 1}") }
            )
            TextButton(onClick = { viewModel.savePreset(index) }) { Text(stringResource(R.string.character_sohr)) }
        }
        TextButton(onClick = viewModel::unequipAll) { Text(stringResource(R.string.character_snyat_vse)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopFilters(viewModel: CharacterViewModel, filter: ShopFilter) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = filter.slot == null,
            onClick = { viewModel.setSlotFilter(null) },
            label = { Text(stringResource(R.string.character_vse_sloty)) }
        )
        EquipSlot.entries.filter { it.isUserFacing }.forEach { slot ->
            FilterChip(
                selected = filter.slot == slot,
                onClick = { viewModel.setSlotFilter(if (filter.slot == slot) null else slot) },
                label = { Text(slot.title) }
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Rarity.entries.forEach { rarity ->
            FilterChip(
                selected = filter.rarity == rarity,
                onClick = {
                    viewModel.setRarityFilter(if (filter.rarity == rarity) null else rarity)
                },
                label = { Text(rarity.title) }
            )
        }
        FilterChip(
            selected = filter.onlyOwned,
            onClick = viewModel::toggleOwnedOnly,
            label = { Text(stringResource(R.string.character_tolko_moi)) }
        )
        FilterChip(
            selected = filter.onlyAffordable,
            onClick = viewModel::toggleAffordable,
            label = { Text(stringResource(R.string.character_po_karmanu)) }
        )
    }
}

@Composable
private fun ShopRow(
    item: EquipItem,
    owned: Boolean,
    equipped: Boolean,
    missing: Map<dev.ashwake.core.model.Stat, Int>,
    tint: Int?,
    upgradeCost: Int,
    onPreview: () -> Unit,
    onEquip: () -> Unit,
    onBuy: () -> Unit,
    onUpgrade: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPreview)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint?.let { Color(it) } ?: Steel)
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (equipped) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.slot.title} · ${item.rarity.title} · ${item.style.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = rarityColor(item.rarity)
                )
            }
            when {
                equipped -> Text(
                    "надето",
                    style = MaterialTheme.typography.labelMedium,
                    color = Moss
                )
                owned -> TextButton(onClick = onEquip) { Text(stringResource(R.string.character_nadet)) }
                item.price == null -> Text(
                    "за достижение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Button(onClick = onBuy) { Text("${item.price} ◈") }
            }
        }

        if (item.effects.isNotEmpty()) {
            Text(
                item.effects.joinToString(" · ") {
                    "${effectTitle(it.key)} ${formatEffect(it.key, it.value)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (missing.isNotEmpty()) {
            // Предмет виден в магазине, но не надевается — с подписью, чего не хватает
            Text(
                "нужно: " + missing.entries.joinToString { "${statTitle(it.key)} +${it.value}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (item.lore.isNotBlank()) {
            Text(
                item.lore,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (owned && item.price != 0) {
            TextButton(onClick = onUpgrade) { Text("Улучшить · $upgradeCost ◈") }
        }
    }
}

private fun buildLayers(items: List<EquipItem>, tints: Map<String, Int>): List<CharacterLayer> {
    val hidden = items.flatMap { it.hides }.toSet()
    return items
        .filterNot { it.slot in hidden }
        .sortedBy { it.layer }
        .map { item ->
            CharacterLayer(
                slot = item.slot,
                color = Color(tints[item.paletteId] ?: 0xFF6E7BA6.toInt()),
                label = item.slot.title,
                frames = item.frames
            )
        }
}

private fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Steel
    Rarity.UNCOMMON -> Moss
    Rarity.RARE -> Color(0xFF6A8CD8)
    Rarity.EPIC -> Color(0xFF9E6ACF)
    Rarity.LEGENDARY -> Gold
    Rarity.RELIC -> Ember
}

private fun effectTitle(key: String): String = when (EffectKeys.baseKey(key)) {
    EffectKeys.COIN_MULT -> "монеты"
    EffectKeys.COIN_MULT_SPHERE -> "монеты за ${EffectKeys.parameter(key)?.lowercase()}"
    EffectKeys.COIN_MULT_MORNING -> "монеты утром"
    EffectKeys.COIN_MULT_NIGHT -> "монеты ночью"
    EffectKeys.XP_MULT -> "опыт"
    EffectKeys.SCORE_DECAY_SLOW -> "стойкость score"
    EffectKeys.FREEZE_CAP -> "заморозки"
    EffectKeys.STREAK_SHIELD -> "щит серии"
    EffectKeys.PUNCTUAL_BONUS -> "за пунктуальность"
    EffectKeys.EARLY_BONUS -> "за раннее выполнение"
    EffectKeys.COMBO_BONUS -> "за комбо"
    EffectKeys.ABSTINENCE_COIN -> "за день отказа"
    EffectKeys.CRAVING_WARD -> "за переждённую тягу"
    EffectKeys.FOCUS_COIN -> "за помодоро"
    EffectKeys.ROUTINE_BONUS -> "за рутину"
    EffectKeys.TASK_PRIORITY_BONUS -> "за P1-задачи"
    EffectKeys.LOOT_LUCK -> "удача в наградах"
    EffectKeys.REROLL_CHEST -> "перебросы награды"
    EffectKeys.OVERDUE_RELIEF -> "смягчение просрочки"
    else -> key
}

private fun formatEffect(key: String, value: Float): String {
    val flat = EffectKeys.baseKey(key) in setOf(
        EffectKeys.FREEZE_CAP, EffectKeys.STREAK_SHIELD,
        EffectKeys.ABSTINENCE_COIN, EffectKeys.CRAVING_WARD,
        EffectKeys.FOCUS_COIN, EffectKeys.REROLL_CHEST
    )
    val sign = if (value > 0) "+" else ""
    return if (flat) "$sign${value.toInt()}" else "$sign${value.toInt()}%"
}
