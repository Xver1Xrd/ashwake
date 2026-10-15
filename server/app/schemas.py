from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


USERNAME_PATTERN = r"^[a-z][a-z0-9_]{2,31}$"


class Model(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class RegisterRequest(Model):
    username: str = Field(pattern=USERNAME_PATTERN)
    display_name: str = Field(min_length=1, max_length=80)
    device_name: str = Field(min_length=1, max_length=100)
    public_key_pem: str = Field(min_length=100, max_length=1000)
    encryption_public_key_pem: str = Field(min_length=100, max_length=1000)

    @field_validator("username")
    @classmethod
    def normalize_username(cls, value: str) -> str:
        return value.lower()


class ChallengeRequest(Model):
    username: str = Field(pattern=USERNAME_PATTERN)
    device_id: str


class ChallengeResponse(Model):
    challenge_id: str
    challenge: str
    expires_at: datetime


class VerifyRequest(Model):
    challenge_id: str
    challenge: str
    signature: str


class RefreshRequest(Model):
    refresh_token: str = Field(min_length=32)


class RecoverySetup(Model):
    token_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    encrypted_bundle: str = Field(min_length=32, max_length=1_000_000)


class RecoveryRequest(Model):
    username: str = Field(pattern=USERNAME_PATTERN)
    recovery_token: str = Field(min_length=40, max_length=200)
    device_name: str = Field(min_length=1, max_length=100)
    public_key_pem: str = Field(min_length=100, max_length=1000)
    encryption_public_key_pem: str = Field(min_length=100, max_length=1000)


class RecoveryResponse(Model):
    session: "TokenPair"
    encrypted_bundle: str


class TokenPair(Model):
    access_token: str
    refresh_token: str
    token_type: Literal["Bearer"] = "Bearer"
    expires_in: int
    user_id: str
    device_id: str


class Profile(Model):
    id: str
    username: str
    display_name: str
    bio: str
    avatar_key: str | None
    discoverable: bool
    allow_friend_requests: bool


class ProfileUpdate(Model):
    display_name: str | None = Field(None, min_length=1, max_length=80)
    bio: str | None = Field(None, max_length=240)
    discoverable: bool | None = None
    allow_friend_requests: bool | None = None


class DeviceView(Model):
    id: str
    name: str
    public_key_pem: str
    encryption_public_key_pem: str
    created_at: datetime
    last_seen_at: datetime
    revoked_at: datetime | None


class DeviceAdd(Model):
    name: str = Field(min_length=1, max_length=100)
    public_key_pem: str = Field(min_length=100, max_length=1000)
    encryption_public_key_pem: str = Field(min_length=100, max_length=1000)


class FriendRequestCreate(Model):
    username: str = Field(pattern=USERNAME_PATTERN)


class FriendRequestView(Model):
    id: str
    sender: Profile
    recipient: Profile
    state: str
    created_at: datetime


class FriendshipView(Model):
    friendship_id: str
    profile: Profile
    created_at: datetime


class PartnershipCreate(Model):
    username: str = Field(pattern=USERNAME_PATTERN)


class PartnershipView(Model):
    id: str
    inviter: Profile
    invitee: Profile
    state: str
    space_id: str | None
    created_at: datetime


class SpaceCreate(Model):
    encrypted_name: str | None = Field(None, max_length=2048)
    member_usernames: list[str] = Field(default_factory=list, max_length=20)


class SpaceView(Model):
    id: str
    type: str
    owner_id: str
    encrypted_name: str | None
    key_version: int
    role: str


class MemberUpdate(Model):
    role: Literal["editor", "viewer"]


class MemberView(Model):
    profile: Profile
    role: str
    joined_at: datetime


class EnvelopePut(Model):
    device_id: str
    key_version: int = Field(ge=1)
    envelope: str = Field(min_length=32, max_length=16384)


class EnvelopeView(Model):
    space_id: str
    device_id: str
    key_version: int
    envelope: str


class SyncMutation(Model):
    space_id: str
    record_id: str
    record_type: str = Field(pattern=r"^[a-z][a-z0-9_]{0,39}$")
    key_version: int = Field(ge=1)
    base_revision: int = Field(ge=0)
    ciphertext: str = Field(min_length=1, max_length=2_000_000)
    deleted: bool = False


class SyncPush(Model):
    mutations: list[SyncMutation] = Field(max_length=100)


class SyncResult(Model):
    record_id: str
    revision: int | None = None
    conflict_revision: int | None = None
    accepted: bool


class SyncRecord(Model):
    space_id: str
    record_id: str
    record_type: str
    key_version: int
    revision: int
    ciphertext: str
    deleted: bool
    updated_at: datetime


class SyncPull(Model):
    cursor: int
    has_more: bool
    records: list[SyncRecord]


class BackupCreate(Model):
    size: int = Field(gt=0, le=100 * 1024 * 1024)
    sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    format_version: int = Field(ge=1, le=100)


class BackupUpload(Model):
    id: str
    upload_url: str
    required_headers: dict[str, str]


class BackupView(Model):
    id: str
    size: int
    sha256: str
    format_version: int
    status: str
    created_at: datetime


class BackupDownload(Model):
    download_url: str
