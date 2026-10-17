package dev.ashwake.online.model

data class OnlineSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val deviceId: String,
    val expiresAtEpochSeconds: Long
)

data class OnlineProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarKey: String? = null,
    val discoverable: Boolean = true,
    val allowFriendRequests: Boolean = true
)

data class OnlineDevice(
    val id: String,
    val name: String,
    val publicKeyPem: String,
    val encryptionPublicKeyPem: String
)

data class FriendRequest(
    val id: String,
    val sender: OnlineProfile,
    val recipient: OnlineProfile,
    val state: String
)

data class Friendship(val id: String, val profile: OnlineProfile)

data class Partnership(
    val id: String,
    val inviter: OnlineProfile,
    val invitee: OnlineProfile,
    val state: String,
    val spaceId: String?
)

data class OnlineSpace(
    val id: String,
    val type: String,
    val ownerId: String,
    val encryptedName: String?,
    val keyVersion: Int,
    val role: String
)

data class SpaceMember(val profile: OnlineProfile, val role: String)

data class SyncMutation(
    val spaceId: String,
    val recordId: String,
    val recordType: String,
    val keyVersion: Int,
    val baseRevision: Long,
    val ciphertext: String,
    val deleted: Boolean
)

data class SyncPushResult(
    val recordId: String,
    val accepted: Boolean,
    val revision: Long?,
    val conflictRevision: Long?
)

data class RemoteRecord(
    val spaceId: String,
    val recordId: String,
    val recordType: String,
    val keyVersion: Int,
    val revision: Long,
    val ciphertext: String,
    val deleted: Boolean
)

data class SyncPage(val cursor: Long, val hasMore: Boolean, val records: List<RemoteRecord>)
