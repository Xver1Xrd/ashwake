package dev.ashwake.online.session

import dev.ashwake.online.crypto.SecureVault
import dev.ashwake.online.model.OnlineSession
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineSessionStore @Inject constructor(private val vault: SecureVault) {
    fun save(session: OnlineSession, username: String) {
        vault.put(SESSION, JSONObject().apply {
            put("access", session.accessToken)
            put("refresh", session.refreshToken)
            put("user", session.userId)
            put("device", session.deviceId)
            put("expires", session.expiresAtEpochSeconds)
            put("username", username)
        }.toString().toByteArray())
        vault.put(DEVICE, JSONObject().apply {
            put("device", session.deviceId)
            put("username", username)
        }.toString().toByteArray())
    }

    fun load(): StoredSession? = vault.get(SESSION)?.let { bytes ->
        runCatching {
            val root = JSONObject(String(bytes))
            StoredSession(
                OnlineSession(
                    accessToken = root.getString("access"),
                    refreshToken = root.getString("refresh"),
                    userId = root.getString("user"),
                    deviceId = root.getString("device"),
                    expiresAtEpochSeconds = root.getLong("expires")
                ),
                root.getString("username")
            )
        }.getOrNull()
    }

    fun clear() = vault.remove(SESSION)

    fun registration(): DeviceRegistration? = vault.get(DEVICE)?.let { bytes ->
        runCatching {
            val root = JSONObject(String(bytes))
            DeviceRegistration(root.getString("device"), root.getString("username"))
        }.getOrNull()
    }

    data class StoredSession(val session: OnlineSession, val username: String)
    data class DeviceRegistration(val deviceId: String, val username: String)

    private companion object {
        const val SESSION = "online.session.v1"
        const val DEVICE = "online.device.v1"
    }
}
