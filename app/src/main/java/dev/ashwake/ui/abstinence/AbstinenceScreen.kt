package dev.ashwake.ui.abstinence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.abstinence.components.LiveCounter
import dev.ashwake.ui.abstinence.components.currencySymbol
import dev.ashwake.ui.abstinence.components.formatMoney
import dev.ashwake.ui.abstinence.editor.CreateAbstinenceDialog
import dev.ashwake.ui.theme.Gold
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbstinenceScreen(
    onOpen: (Long) -> Unit,
    viewModel: AbstinenceViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.abstinence_otkazy)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.abstinence_novyy_otkaz))
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.abstinence.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpen(item.abstinence.id) }
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(item.abstinence.name, style = MaterialTheme.typography.titleMedium)
                        LiveCounter(
                            duration = item.stats.current,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "рекорд ${item.stats.record.toDays()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "попытка №${item.stats.attemptNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.stats.savings?.let { savings ->
                            Text(
                                "сэкономлено ${formatMoney(savings.money)} " +
                                    currencySymbol(savings.currency) +
                                    " · не ${savings.units.roundToInt()} ${savings.unitName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showCreate) {
            CreateAbstinenceDialog(
                onCreate = { name, mode, startedAt, motivation, baseline, substitutes ->
                    viewModel.create(name, mode, startedAt, motivation, baseline, substitutes)
                    showCreate = false
                },
                onDismiss = { showCreate = false }
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.abstinence_schetchikov_poka_net), style = MaterialTheme.typography.titleMedium)
        Text(
            "Отказ отличается от привычки: его не надо делать, надо не делать. Счётчик идёт сам, вмешиваться нужно только при срыве",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
