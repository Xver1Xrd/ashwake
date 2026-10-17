package dev.ashwake.online.repository

import android.os.Build
import dev.ashwake.online.api.OnlineApi
import dev.ashwake.online.crypto.DeviceIdentity
import dev.ashwake.online.crypto.SpaceCrypto
import dev.ashwake.online.crypto.RecoveryManager
import dev.ashwake.online.model.FriendRequest
import dev.ashwake.online.model.Friendship
import dev.ashwake.online.model.OnlineProfile
import dev.ashwake.online.model.OnlineDevice
import dev.ashwake.online.model.OnlineSession
import dev.ashwake.online.model.OnlineSpace
import dev.ashwake.online.model.Partnership
import dev.ashwake.online.session.OnlineSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class OnlineSnapshot(
    val configured: Boolean,
    val signedIn: Boolean = false,
    val profile: OnlineProfile? = null,
    val friends: List<Friendship> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val partnership: Partnership? = null,
    val spaces: List<OnlineSpace> = emptyList(),
    val devices: List<OnlineDevice> = emptyList(),
    val currentDeviceId: String? = null
)

@Singleton
class OnlineRepository @Inject constructor(
    private val api: OnlineApi,
    private val identity: DeviceIdentity,
    private val sessions: OnlineSessionStore,
    private val crypto: SpaceCrypto,
    private val recovery: RecoveryManager
) {
    private val refreshMutex = Mutex()
    private val _spaces = MutableStateFlow<List<OnlineSpace>>(emptyList())
    val spaces: StateFlow<List<OnlineSpace>> = _spaces.asStateFlow()
    private var newRecoveryCode: String? = null

    val configured: Boolean get() = api.configured

    fun hasSession(): Boolean = sessions.load() != null

    suspend fun register(username: String, displayName: String): OnlineSnapshot {
        identity.ensureCreated()
        val session = api.register(
            username = normalize(username),
            displayName = displayName.trim(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            publicKeyPem = identity.signingPublicPem(),
            encryptionPublicKeyPem = identity.encryptionPublicPem()
        )
        sessions.save(session, normalize(username))
        val snapshot = loadSnapshot()
        val code = recovery.createCode()
        api.setRecovery(recovery.tokenHash(code), recovery.encryptBundle(code, snapshot.spaces), token())
        newRecoveryCode = code
        return snapshot
    }

    suspend fun signIn(username: String): OnlineSnapshot {
        identity.ensureCreated()
        val registration = sessions.registration()
            ?: error("Устройство ещё не связано с профилем; используйте код восстановления")
        val normalized = normalize(username)
        require(registration.username == normalized) {
            "Это устройство связано с другим профилем; используйте код восстановления"
        }
        val challenge = api.challenge(normalized, registration.deviceId)
        val session = api.verify(challenge, identity.sign(challenge.value))
        sessions.save(session, normalized)
        return loadSnapshot()
    }

    suspend fun recover(username: String, code: String): OnlineSnapshot {
        identity.ensureCreated()
        val (session, bundle) = api.recover(
            username = normalize(username),
            recoveryToken = code.trim(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            publicKeyPem = identity.signingPublicPem(),
            encryptionPublicKeyPem = identity.encryptionPublicPem()
        )
        recovery.restoreBundle(code.trim(), bundle)
        sessions.save(session, normalize(username))
        return loadSnapshot()
    }

    suspend fun loadSnapshot(): OnlineSnapshot {
        if (!configured) return OnlineSnapshot(configured = false)
        val token = accessToken()
        val ownProfile = api.profile(token)
        val spaces = api.spaces(token)
        _spaces.value = spaces
        prepareKeys(spaces, token)
        recovery.storedCode()?.let { code ->
            api.setRecovery(recovery.tokenHash(code), recovery.encryptBundle(code, spaces), token)
        }
        return OnlineSnapshot(
            configured = true,
            signedIn = true,
            profile = ownProfile,
            friends = api.friends(token),
            requests = api.friendRequests(token),
            partnership = api.partnership(token),
            spaces = spaces,
            devices = api.devices(ownProfile.username, token),
            currentDeviceId = sessions.load()?.session?.deviceId
        )
    }

    suspend fun search(username: String): OnlineProfile = api.search(normalize(username), accessToken())

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        discoverable: Boolean,
        allowFriendRequests: Boolean
    ) {
        require(displayName.trim().isNotEmpty()) { "Имя не может быть пустым" }
        api.updateProfile(
            displayName.trim(),
            bio.trim(),
            discoverable,
            allowFriendRequests,
            accessToken()
        )
    }

    suspend fun addFriend(username: String) {
        api.sendFriendRequest(normalize(username), accessToken())
    }

    suspend fun acceptFriend(id: String) = api.acceptFriendRequest(id, accessToken())

    suspend fun declineFriend(id: String) = api.declineFriendRequest(id, accessToken())

    suspend fun removeFriend(userId: String) = api.removeFriend(userId, accessToken())

    suspend fun blockUser(userId: String) = api.blockUser(userId, accessToken())

    suspend fun revokeDevice(deviceId: String) = api.revokeDevice(deviceId, accessToken())

    suspend fun invitePartner(username: String) {
        api.invitePartner(normalize(username), accessToken())
    }

    suspend fun acceptPartner(id: String) {
        api.acceptPartnership(id, accessToken())
    }

    suspend fun endPartnership(id: String) = api.endPartnership(id, accessToken())

    suspend fun signOut() {
        val refreshToken = sessions.load()?.session?.refreshToken
        try {
            if (refreshToken != null) api.logout(refreshToken)
        } finally {
            sessions.clear()
            _spaces.value = emptyList()
        }
    }

    fun consumeNewRecoveryCode(): String? = newRecoveryCode.also { newRecoveryCode = null }

    suspend fun session(): OnlineSession = sessions.load()?.session ?: error("Войдите в профиль")

    suspend fun token(): String = accessToken()

    private suspend fun accessToken(): String {
        val stored = sessions.load() ?: error("Войдите в профиль")
        if (stored.session.expiresAtEpochSeconds > Instant.now().epochSecond + 30) {
            return stored.session.accessToken
        }
        return refreshMutex.withLock {
            val latest = sessions.load() ?: error("Войдите в профиль")
            if (latest.session.expiresAtEpochSeconds > Instant.now().epochSecond + 30) {
                latest.session.accessToken
            } else {
                val refreshed = api.refresh(latest.session.refreshToken)
                sessions.save(refreshed, latest.username)
                refreshed.accessToken
            }
        }
    }

    private suspend fun prepareKeys(spaces: List<OnlineSpace>, token: String) {
        spaces.forEach { space ->
            if (crypto.key(space.id, space.keyVersion) != null) return@forEach
            val downloaded = runCatching { api.envelope(space.id, token) }.getOrNull()
            if (downloaded != null) {
                crypto.unwrap(space.id, space.keyVersion, downloaded.envelope)
                return@forEach
            }
            if (space.role != "owner") return@forEach

            crypto.createKey(space.id, space.keyVersion)
            api.members(space.id, token).forEach { membership ->
                api.devices(membership.profile.username, token).forEach { device ->
                    val envelope = crypto.wrap(space.id, space.keyVersion, device.encryptionPublicKeyPem)
                    api.putEnvelope(space.id, device.id, space.keyVersion, envelope, token)
                }
            }
        }
    }

    private fun normalize(value: String): String = value.trim().removePrefix("@").lowercase()
}
