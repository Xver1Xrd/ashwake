package dev.ashwake.online.api

import dev.ashwake.BuildConfig
import dev.ashwake.online.model.FriendRequest
import dev.ashwake.online.model.Friendship
import dev.ashwake.online.model.OnlineDevice
import dev.ashwake.online.model.OnlineProfile
import dev.ashwake.online.model.OnlineSession
import dev.ashwake.online.model.OnlineSpace
import dev.ashwake.online.model.Partnership
import dev.ashwake.online.model.RemoteRecord
import dev.ashwake.online.model.SpaceMember
import dev.ashwake.online.model.SyncMutation
import dev.ashwake.online.model.SyncPage
import dev.ashwake.online.model.SyncPushResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class ApiException(val code: Int, message: String) : Exception(message)

data class LoginChallenge(val id: String, val value: String)
data class KeyEnvelope(val spaceId: String, val deviceId: String, val keyVersion: Int, val envelope: String)
data class BackupUpload(val id: String, val uploadUrl: String, val headers: Map<String, String>)
data class CloudBackup(
    val id: String,
    val size: Long,
    val sha256: String,
    val formatVersion: Int,
    val createdAt: String
)

@Singleton
class OnlineApi @Inject constructor() {
    val configured: Boolean get() = BuildConfig.API_BASE_URL.startsWith("https://") ||
        (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.startsWith("http://"))

    suspend fun register(
        username: String,
        displayName: String,
        deviceName: String,
        publicKeyPem: String,
        encryptionPublicKeyPem: String
    ): OnlineSession = request("POST", "/v1/auth/register", body = JSONObject().apply {
        put("username", username)
        put("display_name", displayName)
        put("device_name", deviceName)
        put("public_key_pem", publicKeyPem)
        put("encryption_public_key_pem", encryptionPublicKeyPem)
    }).toSession()

    suspend fun challenge(username: String, deviceId: String): LoginChallenge =
        request("POST", "/v1/auth/challenge", body = JSONObject().apply {
            put("username", username)
            put("device_id", deviceId)
        }).let { LoginChallenge(it.getString("challenge_id"), it.getString("challenge")) }

    suspend fun verify(challenge: LoginChallenge, signature: String): OnlineSession =
        request("POST", "/v1/auth/verify", body = JSONObject().apply {
            put("challenge_id", challenge.id)
            put("challenge", challenge.value)
            put("signature", signature)
        }).toSession()

    suspend fun refresh(refreshToken: String): OnlineSession =
        request("POST", "/v1/auth/refresh", body = JSONObject().put("refresh_token", refreshToken)).toSession()

    suspend fun setRecovery(tokenHashHex: String, encryptedBundle: String, token: String) {
        requestUnit("PUT", "/v1/auth/recovery", token, JSONObject().apply {
            put("token_hash", tokenHashHex)
            put("encrypted_bundle", encryptedBundle)
        })
    }

    suspend fun recover(
        username: String,
        recoveryToken: String,
        deviceName: String,
        publicKeyPem: String,
        encryptionPublicKeyPem: String
    ): Pair<OnlineSession, String> = request("POST", "/v1/auth/recover", body = JSONObject().apply {
        put("username", username)
        put("recovery_token", recoveryToken)
        put("device_name", deviceName)
        put("public_key_pem", publicKeyPem)
        put("encryption_public_key_pem", encryptionPublicKeyPem)
    }).let { it.getJSONObject("session").toSession() to it.getString("encrypted_bundle") }

    suspend fun profile(token: String): OnlineProfile =
        request("GET", "/v1/profile", token).toProfile()

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        discoverable: Boolean,
        allowFriendRequests: Boolean,
        token: String
    ): OnlineProfile = request("PATCH", "/v1/profile", token, JSONObject().apply {
        put("display_name", displayName)
        put("bio", bio)
        put("discoverable", discoverable)
        put("allow_friend_requests", allowFriendRequests)
    }).toProfile()

    suspend fun search(username: String, token: String): OnlineProfile =
        request("GET", "/v1/profiles/${username.removePrefix("@")}", token).toProfile()

    suspend fun sendFriendRequest(username: String, token: String): FriendRequest =
        request("POST", "/v1/friends/requests", token, JSONObject().put("username", username.removePrefix("@"))).toFriendRequest()

    suspend fun friendRequests(token: String): List<FriendRequest> =
        requestArray("GET", "/v1/friends/requests", token).objects().map(JSONObject::toFriendRequest)

    suspend fun acceptFriendRequest(id: String, token: String) {
        request("POST", "/v1/friends/requests/$id/accept", token)
    }

    suspend fun declineFriendRequest(id: String, token: String) {
        requestUnit("POST", "/v1/friends/requests/$id/decline", token)
    }

    suspend fun friends(token: String): List<Friendship> =
        requestArray("GET", "/v1/friends", token).objects().map {
            Friendship(it.getString("friendship_id"), it.getJSONObject("profile").toProfile())
        }

    suspend fun removeFriend(userId: String, token: String) {
        requestUnit("DELETE", "/v1/friends/$userId", token)
    }

    suspend fun blockUser(userId: String, token: String) {
        requestUnit("POST", "/v1/blocks/$userId", token)
    }

    suspend fun invitePartner(username: String, token: String): Partnership =
        request("POST", "/v1/partnerships", token, JSONObject().put("username", username.removePrefix("@"))).toPartnership()

    suspend fun partnership(token: String): Partnership? =
        requestNullable("GET", "/v1/partnerships/current", token)?.toPartnership()

    suspend fun acceptPartnership(id: String, token: String): Partnership =
        request("POST", "/v1/partnerships/$id/accept", token).toPartnership()

    suspend fun endPartnership(id: String, token: String) {
        requestUnit("DELETE", "/v1/partnerships/$id", token)
    }

    suspend fun spaces(token: String): List<OnlineSpace> =
        requestArray("GET", "/v1/spaces", token).objects().map(JSONObject::toSpace)

    suspend fun members(spaceId: String, token: String): List<SpaceMember> =
        requestArray("GET", "/v1/spaces/$spaceId/members", token).objects().map {
            SpaceMember(it.getJSONObject("profile").toProfile(), it.getString("role"))
        }

    suspend fun devices(username: String, token: String): List<OnlineDevice> =
        requestArray("GET", "/v1/profiles/${username.removePrefix("@")}/devices", token).objects().map {
            OnlineDevice(
                it.getString("id"),
                it.getString("name"),
                it.getString("public_key_pem"),
                it.getString("encryption_public_key_pem")
            )
        }

    suspend fun revokeDevice(deviceId: String, token: String) {
        requestUnit("DELETE", "/v1/devices/$deviceId", token)
    }

    suspend fun putEnvelope(spaceId: String, deviceId: String, keyVersion: Int, envelope: String, token: String) {
        requestUnit("PUT", "/v1/spaces/$spaceId/envelopes", token, JSONObject().apply {
            put("device_id", deviceId)
            put("key_version", keyVersion)
            put("envelope", envelope)
        })
    }

    suspend fun envelope(spaceId: String, token: String): KeyEnvelope =
        request("GET", "/v1/spaces/$spaceId/envelope", token).let {
            KeyEnvelope(it.getString("space_id"), it.getString("device_id"), it.getInt("key_version"), it.getString("envelope"))
        }

    suspend fun push(mutations: List<SyncMutation>, token: String): List<SyncPushResult> {
        val body = JSONObject().put("mutations", JSONArray().apply {
            mutations.forEach { mutation ->
                put(JSONObject().apply {
                    put("space_id", mutation.spaceId)
                    put("record_id", mutation.recordId)
                    put("record_type", mutation.recordType)
                    put("key_version", mutation.keyVersion)
                    put("base_revision", mutation.baseRevision)
                    put("ciphertext", mutation.ciphertext)
                    put("deleted", mutation.deleted)
                })
            }
        })
        return requestArray("POST", "/v1/sync/push", token, body).objects().map {
            SyncPushResult(
                recordId = it.getString("record_id"),
                accepted = it.getBoolean("accepted"),
                revision = it.optLongOrNull("revision"),
                conflictRevision = it.optLongOrNull("conflict_revision")
            )
        }
    }

    suspend fun pull(cursor: Long, token: String): SyncPage =
        request("GET", "/v1/sync/pull?cursor=$cursor", token).let { root ->
            SyncPage(
                cursor = root.getLong("cursor"),
                hasMore = root.getBoolean("has_more"),
                records = root.getJSONArray("records").objects().map {
                    RemoteRecord(
                        spaceId = it.getString("space_id"),
                        recordId = it.getString("record_id"),
                        recordType = it.getString("record_type"),
                        keyVersion = it.getInt("key_version"),
                        revision = it.getLong("revision"),
                        ciphertext = it.getString("ciphertext"),
                        deleted = it.getBoolean("deleted")
                    )
                }
            )
        }

    suspend fun createBackup(size: Long, sha256: String, token: String): BackupUpload =
        request("POST", "/v1/backups", token, JSONObject().apply {
            put("size", size)
            put("sha256", sha256)
            put("format_version", 1)
        }).let { root ->
            val headers = root.getJSONObject("required_headers")
            BackupUpload(
                id = root.getString("id"),
                uploadUrl = root.getString("upload_url"),
                headers = headers.keys().asSequence().associateWith(headers::getString)
            )
        }

    suspend fun uploadBackup(upload: BackupUpload, payload: ByteArray) = withContext(Dispatchers.IO) {
        val connection = (URL(upload.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setFixedLengthStreamingMode(payload.size)
            upload.headers.forEach(::setRequestProperty)
        }
        try {
            connection.outputStream.use { it.write(payload) }
            if (connection.responseCode !in 200..299) {
                throw ApiException(connection.responseCode, "Хранилище отклонило бэкап")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun completeBackup(id: String, token: String) {
        request("POST", "/v1/backups/$id/complete", token)
    }

    suspend fun backups(token: String): List<CloudBackup> =
        requestArray("GET", "/v1/backups", token).objects().map {
            CloudBackup(
                id = it.getString("id"),
                size = it.getLong("size"),
                sha256 = it.getString("sha256"),
                formatVersion = it.getInt("format_version"),
                createdAt = it.getString("created_at")
            )
        }

    suspend fun downloadBackup(id: String, token: String): ByteArray {
        val url = request("GET", "/v1/backups/$id/download", token).getString("download_url")
        return withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                if (connection.responseCode !in 200..299) {
                    throw ApiException(connection.responseCode, "Не удалось скачать облачный бэкап")
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun logout(refreshToken: String) {
        requestUnit("POST", "/v1/auth/logout", body = JSONObject().put("refresh_token", refreshToken))
    }

    private suspend fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject = execute(method, path, token, body).let { text ->
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private suspend fun requestNullable(method: String, path: String, token: String): JSONObject? =
        execute(method, path, token, null).let { if (it.isBlank() || it == "null") null else JSONObject(it) }

    private suspend fun requestArray(method: String, path: String, token: String, body: JSONObject? = null): JSONArray =
        JSONArray(execute(method, path, token, body))

    private suspend fun requestUnit(method: String, path: String, token: String? = null, body: JSONObject? = null) {
        execute(method, path, token, body)
    }

    private suspend fun execute(method: String, path: String, token: String?, body: JSONObject?): String =
        withContext(Dispatchers.IO) {
            check(configured) { "API не настроен: задайте -Pashwake.apiBaseUrl=https://api.example.ru" }
            val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(response).optString("detail") }.getOrNull()
                    throw ApiException(code, message?.takeIf { it.isNotBlank() } ?: "Ошибка сервера: $code")
                }
                response
            } finally {
                connection.disconnect()
            }
        }
}

private fun JSONObject.toSession() = OnlineSession(
    accessToken = getString("access_token"),
    refreshToken = getString("refresh_token"),
    userId = getString("user_id"),
    deviceId = getString("device_id"),
    expiresAtEpochSeconds = Instant.now().epochSecond + getLong("expires_in")
)

private fun JSONObject.toProfile() = OnlineProfile(
    id = getString("id"),
    username = getString("username"),
    displayName = getString("display_name"),
    bio = optString("bio"),
    avatarKey = optString("avatar_key").takeUnless { isNull("avatar_key") },
    discoverable = optBoolean("discoverable", true),
    allowFriendRequests = optBoolean("allow_friend_requests", true)
)

private fun JSONObject.toFriendRequest() = FriendRequest(
    id = getString("id"),
    sender = getJSONObject("sender").toProfile(),
    recipient = getJSONObject("recipient").toProfile(),
    state = getString("state")
)

private fun JSONObject.toPartnership() = Partnership(
    id = getString("id"),
    inviter = getJSONObject("inviter").toProfile(),
    invitee = getJSONObject("invitee").toProfile(),
    state = getString("state"),
    spaceId = optString("space_id").takeUnless { isNull("space_id") }
)

private fun JSONObject.toSpace() = OnlineSpace(
    id = getString("id"),
    type = getString("type"),
    ownerId = getString("owner_id"),
    encryptedName = optString("encrypted_name").takeUnless { isNull("encrypted_name") },
    keyVersion = getInt("key_version"),
    role = getString("role")
)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (isNull(name) || !has(name)) null else getLong(name)
