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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.engine.character.EffectKeys
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.buildCharacterLayers
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
    onBack: () -> Unit = {},
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
        buildCharacterLayers(displayed.values.toList(), catalog.paletteTints)
    }

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = state.profile.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.character_nastroyki))
                    }
                    Text(
                        "${state.wallet.coins} ◈",
                        style = AshTheme.type.title3,
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
                        .background(AshTheme.colors.surface1)
                ) {
                    PixelCharacter(
                        layers = layers,
                        modifier = Modifier.fillMaxSize(),
                        reduceMotion = state.profile.reduceMotion
                    )
                    if (layers.isEmpty()) {
                        Text(
                            stringResource(R.string.character_nadente_chto_nibud_iz_magazina_nizhe),
                            style = AshTheme.type.callout,
                            color = AshTheme.colors.text2,
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
                    TextAction(
                        text = stringResource(R.string.character_sohranit_portret),
                        onClick = viewModel::savePortrait
                    )
                    TextAction(
                        text = stringResource(R.string.character_podelitsya),
                        onClick = viewModel::sharePortrait
                    )
                }
            }

            item {
                HorizontalDivider()
                Text(stringResource(R.string.character_magazin), style = AshTheme.type.title3)
                ShopFilters(viewModel, filter)
            }

            val visibleItems = viewModel.visibleItems()
            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.character_po_etim_filtram_nichego_net),
                        style = AshTheme.type.callout,
                        color = AshTheme.colors.text2
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
            Text(stringResource(R.string.character_uroven_1_s, level), style = AshTheme.type.headline)
            Text(
                "$xp XP",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
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
        Text(stringResource(R.string.character_harakteristiki), style = AshTheme.type.headline)
        Text(
            stringResource(R.string.character_rastut_ot_povedeniya_a_ne_za_monety),
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
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
                            stringResource(stat.stat.titleRes) + " " + (stat.value + bonus) +
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
        Text(stringResource(R.string.character_aktivnye_bonusy), style = AshTheme.type.headline)
        equipment.activeSets.forEach { active ->
            Text(
                "Сет «${active.set.title}» — ${active.pieces} част${if (active.pieces == 1) "ь" else "и"}: " +
                    active.tiers.joinToString("/") { "$it" },
                style = AshTheme.type.footnote,
                color = Moss
            )
        }
        equipment.effects.entries.sortedBy { it.key }.forEach { (key, value) ->
            Text(
                "${effectTitle(key)}: ${formatEffect(key, value)}",
                style = AshTheme.type.footnote,
                color = if (value < 0) Ember else AshTheme.colors.text2
            )
        }
        if (equipment.blockedItems.isNotEmpty()) {
            Text(
                stringResource(R.string.character_ne_rabotayut_iz_za_trebovaniy) +
                    equipment.blockedItems.joinToString { it.name },
                style = AshTheme.type.footnote,
                color = AshTheme.colors.danger
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
        Text(stringResource(R.string.character_obrazy), style = AshTheme.type.subhead)
        (0..2).forEach { index ->
            AssistChip(
                onClick = { viewModel.applyPreset(index) },
                label = { Text("${index + 1}") }
            )
            TextAction(
                text = stringResource(R.string.character_sohr),
                onClick = { viewModel.savePreset(index) }
            )
        }
        TextAction(
            text = stringResource(R.string.character_snyat_vse),
            onClick = viewModel::unequipAll
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopFilters(viewModel: CharacterViewModel, filter: ShopFilter) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipButton(
                        text = stringResource(R.string.character_vse_sloty),
                        selected = filter.slot == null,
                        onClick = { viewModel.setSlotFilter(null) }
                    )
        EquipSlot.entries.filter { it.isUserFacing }.forEach { slot ->
            ChipButton(
                        text = slot.title,
                        selected = filter.slot == slot,
                        onClick = { viewModel.setSlotFilter(if (filter.slot == slot) null else slot) }
                    )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Rarity.entries.forEach { rarity ->
            ChipButton(
                        text = rarity.title,
                        selected = filter.rarity == rarity,
                        onClick = {
                    viewModel.setRarityFilter(if (filter.rarity == rarity) null else rarity)
                }
                    )
        }
        ChipButton(
                        text = stringResource(R.string.character_tolko_moi),
                        selected = filter.onlyOwned,
                        onClick = viewModel::toggleOwnedOnly
                    )
        ChipButton(
                        text = stringResource(R.string.character_po_karmanu),
                        selected = filter.onlyAffordable,
                        onClick = viewModel::toggleAffordable
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
            .background(AshTheme.colors.surface1)
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
                    style = AshTheme.type.callout,
                    fontWeight = if (equipped) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.slot.title} · ${item.rarity.title} · ${item.style.title}",
                    style = AshTheme.type.footnote,
                    color = rarityColor(item.rarity)
                )
            }
            when {
                equipped -> Text(
                    stringResource(R.string.character_nadeto),
                    style = AshTheme.type.footnote,
                    color = Moss
                )
                owned -> TextAction(text = stringResource(R.string.character_nadet), onClick = onEquip)
                item.price == null -> Text(
                    stringResource(R.string.character_za_dostizhenie),
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
                else -> PrimaryButton(text = "${item.price} ◈", onClick = onBuy)
            }
        }

        if (item.effects.isNotEmpty()) {
            // Названия эффектов собираются через map, а не внутри joinToString:
            // joinToString не inline, и composable-вызов из его лямбды не сделать
            val effects = item.effects.map { "${effectTitle(it.key)} ${formatEffect(it.key, it.value)}" }
            Text(
                effects.joinToString(" · "),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
        }
        if (missing.isNotEmpty()) {
            // Предмет виден в магазине, но не надевается — с подписью, чего не хватает
            // Названия собираются через map: joinToString не inline, и
            // composable-вызов из его лямбды не сделать
            val lack = missing.entries.map { "${stringResource(it.key.titleRes)} +${it.value}" }
            Text(
                stringResource(R.string.character_nuzhno) + lack.joinToString(),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.danger
            )
        }
        if (item.lore.isNotBlank()) {
            Text(
                item.lore,
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
        }
        if (owned && item.price != 0) {
            TextAction(text = stringResource(R.string.character_uluchshit_1_s, upgradeCost), onClick = onUpgrade)
        }
    }
}


@Composable
private fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Steel
    Rarity.UNCOMMON -> Moss
    Rarity.RARE -> Color(0xFF6A8CD8)
    Rarity.EPIC -> Color(0xFF9E6ACF)
    Rarity.LEGENDARY -> Gold
    Rarity.RELIC -> Ember
}

@Composable
private fun effectTitle(key: String): String = when (EffectKeys.baseKey(key)) {
    EffectKeys.COIN_MULT -> stringResource(R.string.character_monety)
    EffectKeys.COIN_MULT_SPHERE -> stringResource(R.string.character_monety_za_1_s, EffectKeys.parameter(key)?.lowercase().orEmpty())
    EffectKeys.COIN_MULT_MORNING -> stringResource(R.string.character_monety_utrom)
    EffectKeys.COIN_MULT_NIGHT -> stringResource(R.string.character_monety_nochyu)
    EffectKeys.XP_MULT -> stringResource(R.string.character_opyt)
    EffectKeys.SCORE_DECAY_SLOW -> stringResource(R.string.character_stoykost_score)
    EffectKeys.FREEZE_CAP -> stringResource(R.string.detail_zamorozki)
    EffectKeys.STREAK_SHIELD -> stringResource(R.string.character_schit_serii)
    EffectKeys.PUNCTUAL_BONUS -> stringResource(R.string.character_za_punktualnost)
    EffectKeys.EARLY_BONUS -> stringResource(R.string.character_za_rannee_vypolnenie)
    EffectKeys.COMBO_BONUS -> stringResource(R.string.character_za_kombo)
    EffectKeys.ABSTINENCE_COIN -> stringResource(R.string.character_za_den_otkaza)
    EffectKeys.CRAVING_WARD -> stringResource(R.string.character_za_perezhdennuyu_tyagu)
    EffectKeys.FOCUS_COIN -> stringResource(R.string.character_za_pomodoro)
    EffectKeys.ROUTINE_BONUS -> stringResource(R.string.character_za_rutinu)
    EffectKeys.TASK_PRIORITY_BONUS -> stringResource(R.string.character_za_srochnye_zadachi)
    EffectKeys.LOOT_LUCK -> stringResource(R.string.character_udacha_v_nagradah)
    EffectKeys.REROLL_CHEST -> stringResource(R.string.character_perebrosy_nagrady)
    EffectKeys.OVERDUE_RELIEF -> stringResource(R.string.character_smyagchenie_prosrochki)
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
