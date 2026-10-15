package dev.ashwake.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(
    onBack: () -> Unit,
    viewModel: OnlineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль и совместная работа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.snapshot.configured) {
                Text("Онлайн-сервер ещё не подключён", style = MaterialTheme.typography.titleLarge)
                Text(
                    "После развёртывания API соберите приложение с параметром " +
                        "-Pashwake.apiBaseUrl=https://api.ваш-домен.ru. До этого все данные остаются локальными."
                )
                return@Column
            }

            if (!state.snapshot.signedIn) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text("@username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::setDisplayName,
                    label = { Text("Имя в профиле") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = viewModel::register, enabled = !state.loading) {
                    Text("Создать профиль")
                }
                TextButton(onClick = viewModel::signIn, enabled = !state.loading) {
                    Text("Войти на этом устройстве")
                }
                OutlinedTextField(
                    value = state.recoveryInput,
                    onValueChange = viewModel::setRecoveryInput,
                    label = { Text("Код восстановления") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = viewModel::recover, enabled = !state.loading) {
                    Text("Восстановить на новом устройстве")
                }
                Text(
                    "Ключ входа создаётся на устройстве и не отправляется на сервер. " +
                        "Регистрация включает зашифрованную синхронизацию локальных задач.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                state.snapshot.profile?.let { profile ->
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text("@${profile.username}", color = MaterialTheme.colorScheme.primary)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Профиль", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.profileDisplayName,
                            onValueChange = viewModel::setProfileDisplayName,
                            label = { Text("Отображаемое имя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.profileBio,
                            onValueChange = viewModel::setProfileBio,
                            label = { Text("О себе") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        SettingSwitch(
                            "Показывать профиль в точном поиске",
                            state.profileDiscoverable,
                            viewModel::setProfileDiscoverable
                        )
                        SettingSwitch(
                            "Разрешить заявки в друзья",
                            state.profileAllowFriendRequests,
                            viewModel::setProfileAllowFriendRequests
                        )
                        Button(onClick = viewModel::saveProfile, enabled = !state.loading) {
                            Text("Сохранить профиль")
                        }
                    }
                }
                state.recoveryCode?.let { code ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Сохраните код восстановления", style = MaterialTheme.typography.titleMedium)
                            SelectionContainer { Text(code) }
                            Text(
                                "Он показывается один раз. Сохраните его в менеджере паролей: " +
                                    "без этого кода и доверенного устройства расшифровать данные нельзя.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::syncNow, enabled = !state.loading) { Text("Синхронизировать") }
                    OutlinedButton(onClick = viewModel::backupNow, enabled = !state.loading) { Text("Бэкап") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::previewCloudRestore, enabled = !state.loading) {
                        Text("Восстановить копию")
                    }
                    OutlinedButton(onClick = viewModel::signOut, enabled = !state.loading) { Text("Выйти") }
                }
                state.cloudRestorePreview?.let { preview ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Восстановить последнюю копию?", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${preview.contents.tasks} задач, ${preview.contents.habits} привычек, " +
                                    "${preview.contents.abstinences} отказов. Текущие локальные данные будут заменены."
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::confirmCloudRestore, enabled = !state.loading) {
                                    Text("Восстановить")
                                }
                                TextButton(onClick = viewModel::cancelCloudRestore) { Text("Отмена") }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Устройства", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Отзовите потерянный телефон: его refresh-токены перестанут работать.",
                    style = MaterialTheme.typography.bodySmall
                )
                state.snapshot.devices.forEach { device ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            device.name + if (device.id == state.snapshot.currentDeviceId) " · текущее" else "",
                            modifier = Modifier.weight(1f)
                        )
                        if (device.id != state.snapshot.currentDeviceId) {
                            TextButton(onClick = { viewModel.revokeDevice(device.id) }) { Text("Отозвать") }
                        }
                    }
                }

                HorizontalDivider()
                Text("Найти человека", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    label = { Text("Точный @username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = viewModel::search, enabled = state.search.isNotBlank() && !state.loading) {
                    Text("Найти")
                }
                state.searchResult?.let { result ->
                    ProfileCard("${result.displayName} · @${result.username}") {
                        TextButton(onClick = viewModel::addFriend) { Text("Добавить в друзья") }
                        TextButton(onClick = viewModel::invitePartner) { Text("Предложить стать парой") }
                    }
                }

                if (state.snapshot.requests.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Заявки в друзья", style = MaterialTheme.typography.titleMedium)
                    state.snapshot.requests.forEach { request ->
                        val incoming = request.recipient.id == state.snapshot.profile?.id
                        ProfileCard(if (incoming) "@${request.sender.username}" else "Ожидает @${request.recipient.username}") {
                            if (incoming) {
                                TextButton(onClick = { viewModel.acceptFriend(request.id) }) { Text("Принять") }
                                TextButton(onClick = { viewModel.declineFriend(request.id) }) { Text("Отклонить") }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Друзья", style = MaterialTheme.typography.titleMedium)
                if (state.snapshot.friends.isEmpty()) Text("Пока никого")
                state.snapshot.friends.forEach { friend ->
                    ProfileCard("${friend.profile.displayName} · @${friend.profile.username}") {
                        TextButton(onClick = { viewModel.removeFriend(friend.profile.id) }) { Text("Удалить") }
                        TextButton(onClick = { viewModel.blockUser(friend.profile.id) }) { Text("Блокировать") }
                    }
                }

                HorizontalDivider()
                Text("Пара", style = MaterialTheme.typography.titleMedium)
                val partnership = state.snapshot.partnership
                if (partnership == null) {
                    Text("Связь не настроена")
                } else {
                    val me = state.snapshot.profile?.id
                    val other = if (partnership.inviter.id == me) partnership.invitee else partnership.inviter
                    Text("@${other.username} · ${partnership.state}")
                    if (partnership.state == "pending" && partnership.invitee.id == me) {
                        Button(onClick = { viewModel.acceptPartner(partnership.id) }) { Text("Принять приглашение") }
                    }
                    TextButton(onClick = { viewModel.endPartnership(partnership.id) }) { Text("Завершить связь") }
                }

                HorizontalDivider()
                Text("Пространства задач", style = MaterialTheme.typography.titleMedium)
                state.snapshot.spaces.forEach { space ->
                    Text(
                        when (space.type) {
                            "personal" -> "Личное"
                            "couple" -> "Мы"
                            else -> "Общее пространство"
                        } + " · ${space.role}"
                    )
                }
            }

            if (state.loading) CircularProgressIndicator()
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::clearMessage) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProfileCard(title: String, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row { actions() }
        }
    }
}
