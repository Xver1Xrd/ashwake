package dev.ashwake.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshTheme

/** Профиль, друзья, пара и облачная копия в общей дизайн-системе Ashwake. */
@Composable
fun OnlineScreen(
    onBack: () -> Unit,
    viewModel: OnlineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AshNavBar(title = "Аккаунт и синхронизация", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when {
                !state.snapshot.configured -> AccountPreview()
                !state.snapshot.signedIn -> SignInContent(state, viewModel)
                else -> SignedInContent(state, viewModel)
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            state.message?.let { message ->
                ListGroup(header = "Сообщение") {
                    ListRow(
                        title = message,
                        titleColor = colors.danger,
                        trailing = {
                            TextAction(text = "Закрыть", onClick = viewModel::clearMessage)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountPreview() {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    ListGroup(
        header = "Аккаунт — предпросмотр",
        footer = "Это интерактивная заглушка: профиль сохраняется только на этом экране до подключения сервера."
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AshTextField(value = username, onValueChange = { username = it }, label = "@username")
            AshTextField(value = displayName, onValueChange = { displayName = it }, label = "Имя в профиле")
            SettingSwitch(label = "Показывать профиль в поиске", checked = visible, onCheckedChange = { visible = it })
            PrimaryButton(text = "Проверить профиль", enabled = username.isNotBlank() && displayName.isNotBlank()) {
                message = "Профиль @$username готов к подключению"
            }
            message?.let { Text(it, style = AshTheme.type.footnote, color = AshTheme.colors.success) }
        }
    }
}

@Composable
private fun SignInContent(state: OnlineUiState, viewModel: OnlineViewModel) {
    ListGroup(
        header = "Новый профиль",
        footer = "Ключ шифрования создаётся на телефоне и не отправляется на сервер."
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AshTextField(
                value = state.username,
                onValueChange = viewModel::setUsername,
                label = "@username"
            )
            AshTextField(
                value = state.displayName,
                onValueChange = viewModel::setDisplayName,
                label = "Имя в профиле"
            )
            PrimaryButton(
                text = "Создать профиль",
                enabled = !state.loading,
                onClick = viewModel::register
            )
            SecondaryButton(
                text = "Войти на этом устройстве",
                enabled = !state.loading,
                onClick = viewModel::signIn
            )
        }
    }

    ListGroup(
        header = "Восстановление",
        footer = "Используйте код, сохранённый при регистрации на другом устройстве."
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AshTextField(
                value = state.recoveryInput,
                onValueChange = viewModel::setRecoveryInput,
                label = "Код восстановления"
            )
            SecondaryButton(
                text = "Восстановить на новом устройстве",
                enabled = !state.loading,
                onClick = viewModel::recover
            )
        }
    }
}

@Composable
private fun SignedInContent(state: OnlineUiState, viewModel: OnlineViewModel) {
    state.snapshot.profile?.let { profile ->
        ListGroup(header = "Профиль") {
            ListRow(
                title = profile.displayName,
                subtitle = "@${profile.username}"
            )
            ListDivider()
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AshTextField(
                    value = state.profileDisplayName,
                    onValueChange = viewModel::setProfileDisplayName,
                    label = "Отображаемое имя"
                )
                AshTextField(
                    value = state.profileBio,
                    onValueChange = viewModel::setProfileBio,
                    label = "О себе",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
            }
            ListDivider()
            SettingSwitch(
                label = "Показывать профиль в точном поиске",
                checked = state.profileDiscoverable,
                onCheckedChange = viewModel::setProfileDiscoverable
            )
            ListDivider()
            SettingSwitch(
                label = "Разрешить заявки в друзья",
                checked = state.profileAllowFriendRequests,
                onCheckedChange = viewModel::setProfileAllowFriendRequests
            )
            Column(Modifier.padding(16.dp)) {
                PrimaryButton(
                    text = "Сохранить профиль",
                    enabled = !state.loading,
                    onClick = viewModel::saveProfile
                )
            }
        }
    }

    state.recoveryCode?.let { code ->
        ListGroup(
            header = "Код восстановления",
            footer = "Он показывается один раз. Сохраните его в менеджере паролей."
        ) {
            SelectionContainer {
                Text(
                    text = code,
                    style = AshTheme.type.body,
                    color = AshTheme.colors.text,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    ListGroup(
        header = "Синхронизация и копии",
        footer = "Перед отправкой данные и вложения шифруются на телефоне."
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Синхронизировать",
                enabled = !state.loading,
                onClick = viewModel::syncNow
            )
            SecondaryButton(
                text = "Создать облачный бэкап",
                enabled = !state.loading,
                onClick = viewModel::backupNow
            )
            SecondaryButton(
                text = "Восстановить последнюю копию",
                enabled = !state.loading,
                onClick = viewModel::previewCloudRestore
            )
            SecondaryButton(
                text = "Выйти",
                enabled = !state.loading,
                textColor = AshTheme.colors.danger,
                onClick = viewModel::signOut
            )
        }
    }

    state.cloudRestorePreview?.let { preview ->
        ListGroup(header = "Восстановление копии") {
            ListRow(
                title = "Заменить локальные данные?",
                subtitle = "${preview.contents.tasks} задач, ${preview.contents.habits} привычек, " +
                    "${preview.contents.abstinences} отказов"
            )
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = "Восстановить",
                    enabled = !state.loading,
                    onClick = viewModel::confirmCloudRestore
                )
                SecondaryButton(text = "Отмена", onClick = viewModel::cancelCloudRestore)
            }
        }
    }

    ListGroup(
        header = "Устройства",
        footer = "У потерянного телефона можно отозвать доступ к аккаунту."
    ) {
        state.snapshot.devices.forEachIndexed { index, device ->
            ListRow(
                title = device.name,
                subtitle = if (device.id == state.snapshot.currentDeviceId) "Текущее устройство" else null,
                trailing = if (device.id == state.snapshot.currentDeviceId) null else {
                    { TextAction(text = "Отозвать", onClick = { viewModel.revokeDevice(device.id) }) }
                }
            )
            if (index != state.snapshot.devices.lastIndex) ListDivider()
        }
    }

    ListGroup(header = "Найти человека") {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AshTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                label = "Точный @username"
            )
            PrimaryButton(
                text = "Найти",
                enabled = state.search.isNotBlank() && !state.loading,
                onClick = viewModel::search
            )
        }
    }

    state.searchResult?.let { result ->
        ProfileCard(title = result.displayName, subtitle = "@${result.username}") {
            TextAction(text = "В друзья", onClick = viewModel::addFriend)
            TextAction(text = "Добавить пару", onClick = viewModel::invitePartner)
        }
    }

    if (state.snapshot.requests.isNotEmpty()) {
        ListGroup(header = "Заявки в друзья") {
            state.snapshot.requests.forEachIndexed { index, request ->
                val incoming = request.recipient.id == state.snapshot.profile?.id
                ListRow(
                    title = if (incoming) "@${request.sender.username}" else "@${request.recipient.username}",
                    subtitle = if (incoming) "Хочет добавить вас в друзья" else "Ожидает ответа",
                    trailing = if (incoming) {
                        {
                            Row {
                                TextAction(text = "Принять", onClick = { viewModel.acceptFriend(request.id) })
                                TextAction(text = "Отклонить", onClick = { viewModel.declineFriend(request.id) })
                            }
                        }
                    } else null
                )
                if (index != state.snapshot.requests.lastIndex) ListDivider()
            }
        }
    }

    ListGroup(header = "Друзья") {
        if (state.snapshot.friends.isEmpty()) {
            ListRow(title = "Пока никого")
        } else {
            state.snapshot.friends.forEachIndexed { index, friend ->
                ListRow(
                    title = friend.profile.displayName,
                    subtitle = "@${friend.profile.username}",
                    trailing = {
                        Row {
                            TextAction(
                                text = "Удалить",
                                onClick = { viewModel.removeFriend(friend.profile.id) }
                            )
                            TextAction(
                                text = "Блокировать",
                                color = AshTheme.colors.danger,
                                onClick = { viewModel.blockUser(friend.profile.id) }
                            )
                        }
                    }
                )
                if (index != state.snapshot.friends.lastIndex) ListDivider()
            }
        }
    }

    val partnership = state.snapshot.partnership
    ListGroup(header = "Пара") {
        if (partnership == null) {
            ListRow(title = "Связь не настроена")
        } else {
            val me = state.snapshot.profile?.id
            val other = if (partnership.inviter.id == me) partnership.invitee else partnership.inviter
            ListRow(title = "@${other.username}", subtitle = partnership.state)
            if (partnership.state == "pending" && partnership.invitee.id == me) {
                ListDivider()
                Column(Modifier.padding(16.dp)) {
                    PrimaryButton(
                        text = "Принять приглашение",
                        onClick = { viewModel.acceptPartner(partnership.id) }
                    )
                }
            }
            ListDivider()
            Column(Modifier.padding(16.dp)) {
                SecondaryButton(
                    text = "Завершить связь",
                    textColor = AshTheme.colors.danger,
                    onClick = { viewModel.endPartnership(partnership.id) }
                )
            }
        }
    }

    ListGroup(header = "Пространства задач") {
        state.snapshot.spaces.forEachIndexed { index, space ->
            ListRow(
                title = when (space.type) {
                    "personal" -> "Личное"
                    "couple" -> "Мы"
                    else -> "Общее пространство"
                },
                subtitle = space.role
            )
            if (index != state.snapshot.spaces.lastIndex) ListDivider()
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListRow(
        title = label,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun ProfileCard(
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit
) {
    ListGroup {
        ListRow(title = title, subtitle = subtitle)
        ListDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            content = { actions() }
        )
    }
}
