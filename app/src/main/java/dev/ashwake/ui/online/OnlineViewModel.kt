package dev.ashwake.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.online.model.OnlineProfile
import dev.ashwake.online.repository.OnlineRepository
import dev.ashwake.online.repository.OnlineSnapshot
import dev.ashwake.online.sync.OnlineSyncEngine
import dev.ashwake.online.backup.CloudBackupManager
import dev.ashwake.online.backup.CloudRestorePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineUiState(
    val snapshot: OnlineSnapshot,
    val loading: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val recoveryInput: String = "",
    val recoveryCode: String? = null,
    val profileDisplayName: String = "",
    val profileBio: String = "",
    val profileDiscoverable: Boolean = true,
    val profileAllowFriendRequests: Boolean = true,
    val search: String = "",
    val searchResult: OnlineProfile? = null,
    val cloudRestorePreview: CloudRestorePreview? = null,
    val message: String? = null
)

@HiltViewModel
class OnlineViewModel @Inject constructor(
    private val repository: OnlineRepository,
    private val sync: OnlineSyncEngine,
    private val cloudBackups: CloudBackupManager
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnlineUiState(OnlineSnapshot(configured = repository.configured))
    )
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    init {
        if (repository.configured && repository.hasSession()) refresh()
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value) }
    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun setRecoveryInput(value: String) = _state.update { it.copy(recoveryInput = value) }
    fun setProfileDisplayName(value: String) = _state.update { it.copy(profileDisplayName = value) }
    fun setProfileBio(value: String) = _state.update { it.copy(profileBio = value) }
    fun setProfileDiscoverable(value: Boolean) = _state.update { it.copy(profileDiscoverable = value) }
    fun setProfileAllowFriendRequests(value: Boolean) =
        _state.update { it.copy(profileAllowFriendRequests = value) }
    fun setSearch(value: String) = _state.update { it.copy(search = value, searchResult = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun register() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching {
                require(_state.value.username.isNotBlank() && _state.value.displayName.isNotBlank()) {
                    "Введите username и имя"
                }
                repository.register(_state.value.username, _state.value.displayName)
            }.onSuccess { snapshot ->
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        loading = false,
                        recoveryCode = repository.consumeNewRecoveryCode(),
                        profileDisplayName = snapshot.profile?.displayName.orEmpty(),
                        profileBio = snapshot.profile?.bio.orEmpty(),
                        profileDiscoverable = snapshot.profile?.discoverable ?: true,
                        profileAllowFriendRequests = snapshot.profile?.allowFriendRequests ?: true
                    )
                }
            }.onFailure(::failure)
        }
    }

    fun signIn() = action {
        require(_state.value.username.isNotBlank()) { "Введите username" }
        repository.signIn(_state.value.username)
    }

    fun recover() = action {
        require(_state.value.username.isNotBlank() && _state.value.recoveryInput.isNotBlank()) {
            "Введите username и код восстановления"
        }
        repository.recover(_state.value.username, _state.value.recoveryInput)
    }

    fun refresh() = action { repository.loadSnapshot() }

    fun saveProfile() = mutate("Профиль сохранён") {
        val state = _state.value
        repository.updateProfile(
            state.profileDisplayName,
            state.profileBio,
            state.profileDiscoverable,
            state.profileAllowFriendRequests
        )
    }

    fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { repository.search(_state.value.search) }
                .onSuccess { result -> _state.update { it.copy(loading = false, searchResult = result) } }
                .onFailure { error -> failure(error) }
        }
    }

    fun addFriend() = mutate("Заявка отправлена") { repository.addFriend(_state.value.search) }
    fun invitePartner() = mutate("Приглашение партнёру отправлено") { repository.invitePartner(_state.value.search) }
    fun acceptFriend(id: String) = mutate("Заявка принята") { repository.acceptFriend(id) }
    fun declineFriend(id: String) = mutate("Заявка отклонена") { repository.declineFriend(id) }
    fun removeFriend(userId: String) = mutate("Пользователь удалён из друзей") {
        repository.removeFriend(userId)
    }
    fun blockUser(userId: String) = mutate("Пользователь заблокирован") {
        repository.blockUser(userId)
    }
    fun revokeDevice(deviceId: String) = mutate("Доступ устройства отозван") {
        repository.revokeDevice(deviceId)
    }
    fun acceptPartner(id: String) = mutate("Пара создана") { repository.acceptPartner(id) }
    fun endPartnership(id: String) = mutate("Связь пары завершена") { repository.endPartnership(id) }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { sync.sync() }
                .onSuccess {
                    val snapshot = repository.loadSnapshot()
                    _state.update { it.copy(snapshot = snapshot, loading = false, message = "Синхронизация завершена") }
                }
                .onFailure(::failure)
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { cloudBackups.upload() }
                .onSuccess { message -> _state.update { it.copy(loading = false, message = message) } }
                .onFailure(::failure)
        }
    }

    fun previewCloudRestore() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { cloudBackups.previewLatest() }
                .onSuccess { preview ->
                    _state.update { it.copy(loading = false, cloudRestorePreview = preview) }
                }
                .onFailure(::failure)
        }
    }

    fun confirmCloudRestore() {
        val preview = _state.value.cloudRestorePreview ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { cloudBackups.restore(preview) }
                .onSuccess { message ->
                    _state.update { it.copy(loading = false, cloudRestorePreview = null, message = message) }
                }
                .onFailure(::failure)
        }
    }

    fun cancelCloudRestore() = _state.update { it.copy(cloudRestorePreview = null) }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { repository.signOut() }
                .onSuccess {
                    _state.value = OnlineUiState(OnlineSnapshot(configured = repository.configured))
                }
                .onFailure(::failure)
        }
    }

    private fun mutate(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching {
                block()
                repository.loadSnapshot()
            }.onSuccess { snapshot ->
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        loading = false,
                        message = message,
                        searchResult = null,
                        profileDisplayName = snapshot.profile?.displayName.orEmpty(),
                        profileBio = snapshot.profile?.bio.orEmpty(),
                        profileDiscoverable = snapshot.profile?.discoverable ?: true,
                        profileAllowFriendRequests = snapshot.profile?.allowFriendRequests ?: true
                    )
                }
            }.onFailure(::failure)
        }
    }

    private fun action(block: suspend () -> OnlineSnapshot) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { block() }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            loading = false,
                            profileDisplayName = snapshot.profile?.displayName.orEmpty(),
                            profileBio = snapshot.profile?.bio.orEmpty(),
                            profileDiscoverable = snapshot.profile?.discoverable ?: true,
                            profileAllowFriendRequests = snapshot.profile?.allowFriendRequests ?: true
                        )
                    }
                }
                .onFailure(::failure)
        }
    }

    private fun failure(error: Throwable) {
        _state.update { it.copy(loading = false, message = error.message ?: "Неизвестная ошибка") }
    }
}
