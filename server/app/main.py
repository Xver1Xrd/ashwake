import secrets
import asyncio
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from datetime import timedelta
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import and_, delete, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import Settings, get_settings
from .db import create_schema, get_session
from .dependencies import Principal, principal
from .models import (
    Backup,
    Block,
    ChangeLog,
    Device,
    EncryptedRecord,
    FriendRequest,
    Friendship,
    FriendState,
    KeyEnvelope,
    LoginChallenge,
    MemberRole,
    Partnership,
    PartnershipState,
    RefreshSession,
    Space,
    SpaceMember,
    SpaceType,
    User,
    now,
)
from .schemas import (
    BackupCreate,
    BackupDownload,
    BackupUpload,
    BackupView,
    ChallengeRequest,
    ChallengeResponse,
    DeviceAdd,
    DeviceView,
    EnvelopePut,
    EnvelopeView,
    FriendRequestCreate,
    FriendRequestView,
    FriendshipView,
    MemberUpdate,
    MemberView,
    PartnershipCreate,
    PartnershipView,
    Profile,
    ProfileUpdate,
    RefreshRequest,
    RecoveryRequest,
    RecoveryResponse,
    RecoverySetup,
    RegisterRequest,
    SpaceCreate,
    SpaceView,
    SyncPull,
    SyncPush,
    SyncRecord,
    SyncResult,
    TokenPair,
    VerifyRequest,
)
from .security import digest, expired, issue_tokens, new_secret, rotate_refresh, validate_public_key, verify_signature
from .storage import ObjectStorage


Session = Annotated[AsyncSession, Depends(get_session)]
Actor = Annotated[Principal, Depends(principal)]
Config = Annotated[Settings, Depends(get_settings)]

settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    if settings.environment in {"development", "test"}:
        await create_schema()
    yield


app = FastAPI(title="Ashwake API", version="0.1.0", docs_url=None, redoc_url=None, lifespan=lifespan)
if settings.allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type"],
    )


_auth_attempts: dict[str, deque[float]] = defaultdict(deque)
_auth_attempts_lock = asyncio.Lock()


@app.middleware("http")
async def protect_auth(request: Request, call_next):
    if request.url.path.startswith("/v1/auth/"):
        client = request.client.host if request.client else "unknown"
        current = time.monotonic()
        async with _auth_attempts_lock:
            if len(_auth_attempts) >= 10_000:
                stale_clients = [key for key, values in _auth_attempts.items() if not values or values[-1] < current - 60]
                for key in stale_clients:
                    _auth_attempts.pop(key, None)
            if client not in _auth_attempts and len(_auth_attempts) >= 10_000:
                return Response("Too many authentication sources", status_code=429, headers={"Retry-After": "60"})
            attempts = _auth_attempts[client]
            while attempts and attempts[0] < current - 60:
                attempts.popleft()
            if len(attempts) >= 30:
                return Response("Too many authentication attempts", status_code=429, headers={"Retry-After": "60"})
            attempts.append(current)
    return await call_next(request)


@app.get("/healthz")
async def health() -> dict[str, str]:
    return {"status": "ok"}


def profile(user: User) -> Profile:
    return Profile.model_validate(user)


async def user_by_username(session: AsyncSession, username: str) -> User:
    user = await session.scalar(select(User).where(User.username == username.lower()))
    if not user or user.disabled_at:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")
    return user


async def blocked(session: AsyncSession, left: str, right: str) -> bool:
    return bool(
        await session.scalar(
            select(Block.id).where(
                or_(
                    and_(Block.blocker_id == left, Block.blocked_id == right),
                    and_(Block.blocker_id == right, Block.blocked_id == left),
                )
            )
        )
    )


async def friendship_exists(session: AsyncSession, left: str, right: str) -> bool:
    low, high = sorted((left, right))
    return bool(
        await session.scalar(
            select(Friendship.id).where(
                Friendship.user_low_id == low,
                Friendship.user_high_id == high,
            )
        )
    )


async def member(session: AsyncSession, space_id: str, user_id: str) -> SpaceMember:
    result = await session.scalar(
        select(SpaceMember).where(
            SpaceMember.space_id == space_id,
            SpaceMember.user_id == user_id,
            SpaceMember.removed_at.is_(None),
        )
    )
    if not result:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Space not found")
    return result


@app.post("/v1/auth/register", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
async def register(body: RegisterRequest, session: Session, config: Config) -> TokenPair:
    validate_public_key(body.public_key_pem)
    validate_public_key(body.encryption_public_key_pem)
    if await session.scalar(select(User.id).where(User.username == body.username)):
        raise HTTPException(status.HTTP_409_CONFLICT, "Username is already taken")
    user = User(username=body.username, display_name=body.display_name)
    session.add(user)
    await session.flush()
    device = Device(
        user_id=user.id,
        name=body.device_name,
        public_key_pem=body.public_key_pem,
        encryption_public_key_pem=body.encryption_public_key_pem,
    )
    session.add(device)
    await session.flush()
    personal = Space(type=SpaceType.personal, owner_id=user.id)
    session.add(personal)
    await session.flush()
    session.add(SpaceMember(space_id=personal.id, user_id=user.id, role=MemberRole.owner))
    return await issue_tokens(session, user, device, config)


@app.post("/v1/auth/challenge", response_model=ChallengeResponse)
async def challenge(body: ChallengeRequest, session: Session, config: Config) -> ChallengeResponse:
    # Одинаковая ошибка для неизвестного пользователя и устройства не раскрывает
    # наличие аккаунта подборщику username.
    user = await session.scalar(select(User).where(User.username == body.username.lower()))
    device = await session.get(Device, body.device_id)
    if not user or not device or device.user_id != user.id or device.revoked_at:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Account or device not found")
    raw = new_secret()
    item = LoginChallenge(
        device_id=device.id,
        challenge_hash=digest(raw),
        expires_at=now() + timedelta(minutes=config.challenge_minutes),
    )
    session.add(item)
    await session.commit()
    return ChallengeResponse(challenge_id=item.id, challenge=raw, expires_at=item.expires_at)


@app.post("/v1/auth/verify", response_model=TokenPair)
async def verify(body: VerifyRequest, session: Session, config: Config) -> TokenPair:
    item = await session.get(LoginChallenge, body.challenge_id)
    if not item or item.consumed_at or expired(item.expires_at) or not secrets.compare_digest(item.challenge_hash, digest(body.challenge)):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired challenge")
    device = await session.get(Device, item.device_id)
    if not device or device.revoked_at:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Device revoked")
    verify_signature(device.public_key_pem, body.challenge, body.signature)
    user = await session.get(User, device.user_id)
    if not user or user.disabled_at:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Account disabled")
    item.consumed_at = now()
    return await issue_tokens(session, user, device, config)


@app.post("/v1/auth/refresh", response_model=TokenPair)
async def refresh(body: RefreshRequest, session: Session, config: Config) -> TokenPair:
    return await rotate_refresh(session, body.refresh_token, config)


@app.put("/v1/auth/recovery", status_code=status.HTTP_204_NO_CONTENT)
async def setup_recovery(body: RecoverySetup, actor: Actor, session: Session) -> Response:
    actor.user.recovery_token_hash = bytes.fromhex(body.token_hash)
    actor.user.recovery_bundle = body.encrypted_bundle
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.post("/v1/auth/recover", response_model=RecoveryResponse)
async def recover(body: RecoveryRequest, session: Session, config: Config) -> RecoveryResponse:
    user = await session.scalar(select(User).where(User.username == body.username.lower()))
    supplied = digest(body.recovery_token)
    # Выполняем одно и то же сравнение даже для неизвестного пользователя.
    expected = user.recovery_token_hash if user and user.recovery_token_hash else bytes(32)
    if not secrets.compare_digest(supplied, expected) or not user or not user.recovery_bundle:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid recovery credentials")
    validate_public_key(body.public_key_pem)
    validate_public_key(body.encryption_public_key_pem)
    device = Device(
        user_id=user.id,
        name=body.device_name,
        public_key_pem=body.public_key_pem,
        encryption_public_key_pem=body.encryption_public_key_pem,
    )
    session.add(device)
    await session.flush()
    tokens = await issue_tokens(session, user, device, config)
    return RecoveryResponse(session=tokens, encrypted_bundle=user.recovery_bundle)


@app.post("/v1/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(body: RefreshRequest, session: Session) -> Response:
    item = await session.scalar(select(RefreshSession).where(RefreshSession.token_hash == digest(body.refresh_token)))
    if item and not item.revoked_at:
        item.revoked_at = now()
        await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/v1/profile", response_model=Profile)
async def own_profile(actor: Actor) -> Profile:
    return profile(actor.user)


@app.patch("/v1/profile", response_model=Profile)
async def update_profile(body: ProfileUpdate, actor: Actor, session: Session) -> Profile:
    for key, value in body.model_dump(exclude_unset=True).items():
        setattr(actor.user, key, value)
    await session.commit()
    return profile(actor.user)


@app.get("/v1/profiles/{username}", response_model=Profile)
async def find_profile(username: str, actor: Actor, session: Session) -> Profile:
    user = await user_by_username(session, username.removeprefix("@"))
    if user.id != actor.user.id and (not user.discoverable or await blocked(session, user.id, actor.user.id)):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")
    return profile(user)


@app.get("/v1/devices", response_model=list[DeviceView])
async def devices(actor: Actor, session: Session) -> list[Device]:
    return list(await session.scalars(select(Device).where(Device.user_id == actor.user.id).order_by(Device.created_at)))


@app.post("/v1/devices", response_model=DeviceView, status_code=status.HTTP_201_CREATED)
async def add_device(body: DeviceAdd, actor: Actor, session: Session) -> Device:
    validate_public_key(body.public_key_pem)
    validate_public_key(body.encryption_public_key_pem)
    device = Device(
        user_id=actor.user.id,
        name=body.name,
        public_key_pem=body.public_key_pem,
        encryption_public_key_pem=body.encryption_public_key_pem,
    )
    session.add(device)
    await session.commit()
    return device


@app.get("/v1/profiles/{username}/devices", response_model=list[DeviceView])
async def profile_devices(username: str, actor: Actor, session: Session) -> list[Device]:
    target = await user_by_username(session, username.removeprefix("@"))
    partnership = await session.scalar(
        select(Partnership.id).where(
            or_(
                and_(Partnership.inviter_id == actor.user.id, Partnership.invitee_id == target.id),
                and_(Partnership.inviter_id == target.id, Partnership.invitee_id == actor.user.id),
            ),
            Partnership.state == PartnershipState.active,
        )
    )
    if target.id != actor.user.id and not partnership and not await friendship_exists(session, actor.user.id, target.id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")
    return list(await session.scalars(select(Device).where(Device.user_id == target.id, Device.revoked_at.is_(None))))


@app.delete("/v1/devices/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def revoke_device(device_id: str, actor: Actor, session: Session) -> Response:
    device = await session.get(Device, device_id)
    if not device or device.user_id != actor.user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Device not found")
    device.revoked_at = now()
    sessions = await session.scalars(select(RefreshSession).where(RefreshSession.device_id == device.id))
    for item in sessions:
        item.revoked_at = now()
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


async def request_view(session: AsyncSession, item: FriendRequest) -> FriendRequestView:
    sender = await session.get(User, item.sender_id)
    recipient = await session.get(User, item.recipient_id)
    return FriendRequestView(
        id=item.id,
        sender=profile(sender),
        recipient=profile(recipient),
        state=item.state.value,
        created_at=item.created_at,
    )


@app.post("/v1/friends/requests", response_model=FriendRequestView, status_code=status.HTTP_201_CREATED)
async def send_friend_request(body: FriendRequestCreate, actor: Actor, session: Session) -> FriendRequestView:
    recipient = await user_by_username(session, body.username)
    if recipient.id == actor.user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Cannot add yourself")
    if not recipient.allow_friend_requests or await blocked(session, actor.user.id, recipient.id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")
    if await friendship_exists(session, actor.user.id, recipient.id):
        raise HTTPException(status.HTTP_409_CONFLICT, "Already friends")
    existing = await session.scalar(
        select(FriendRequest).where(
            FriendRequest.sender_id == actor.user.id,
            FriendRequest.recipient_id == recipient.id,
        )
    )
    if existing and existing.state == FriendState.pending:
        raise HTTPException(status.HTTP_409_CONFLICT, "Request already sent")
    if existing:
        existing.state = FriendState.pending
        existing.created_at = now()
        existing.decided_at = None
        item = existing
    else:
        item = FriendRequest(sender_id=actor.user.id, recipient_id=recipient.id)
        session.add(item)
    await session.commit()
    return await request_view(session, item)


@app.get("/v1/friends/requests", response_model=list[FriendRequestView])
async def friend_requests(actor: Actor, session: Session) -> list[FriendRequestView]:
    items = await session.scalars(
        select(FriendRequest).where(
            or_(FriendRequest.sender_id == actor.user.id, FriendRequest.recipient_id == actor.user.id),
            FriendRequest.state == FriendState.pending,
        ).order_by(FriendRequest.created_at.desc())
    )
    return [await request_view(session, item) for item in items]


@app.post("/v1/friends/requests/{request_id}/accept", response_model=FriendshipView)
async def accept_friend_request(request_id: str, actor: Actor, session: Session) -> FriendshipView:
    item = await session.get(FriendRequest, request_id)
    if not item or item.recipient_id != actor.user.id or item.state != FriendState.pending:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Pending request not found")
    if await blocked(session, item.sender_id, item.recipient_id):
        raise HTTPException(status.HTTP_409_CONFLICT, "Request cannot be accepted")
    low, high = sorted((item.sender_id, item.recipient_id))
    friendship = Friendship(user_low_id=low, user_high_id=high)
    session.add(friendship)
    item.state = FriendState.accepted
    item.decided_at = now()
    await session.commit()
    other = await session.get(User, item.sender_id)
    return FriendshipView(friendship_id=friendship.id, profile=profile(other), created_at=friendship.created_at)


@app.post("/v1/friends/requests/{request_id}/decline", status_code=status.HTTP_204_NO_CONTENT)
async def decline_friend_request(request_id: str, actor: Actor, session: Session) -> Response:
    item = await session.get(FriendRequest, request_id)
    if not item or item.recipient_id != actor.user.id or item.state != FriendState.pending:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Pending request not found")
    item.state = FriendState.declined
    item.decided_at = now()
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/v1/friends", response_model=list[FriendshipView])
async def friends(actor: Actor, session: Session) -> list[FriendshipView]:
    rows = list(await session.scalars(select(Friendship).where(or_(Friendship.user_low_id == actor.user.id, Friendship.user_high_id == actor.user.id))))
    result = []
    for row in rows:
        other_id = row.user_high_id if row.user_low_id == actor.user.id else row.user_low_id
        other = await session.get(User, other_id)
        result.append(FriendshipView(friendship_id=row.id, profile=profile(other), created_at=row.created_at))
    return result


@app.delete("/v1/friends/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_friend(user_id: str, actor: Actor, session: Session) -> Response:
    low, high = sorted((actor.user.id, user_id))
    await session.execute(delete(Friendship).where(Friendship.user_low_id == low, Friendship.user_high_id == high))
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.post("/v1/blocks/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def block_user(user_id: str, actor: Actor, session: Session) -> Response:
    if user_id == actor.user.id or not await session.get(User, user_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")
    if not await session.scalar(select(Block.id).where(Block.blocker_id == actor.user.id, Block.blocked_id == user_id)):
        session.add(Block(blocker_id=actor.user.id, blocked_id=user_id))
    low, high = sorted((actor.user.id, user_id))
    await session.execute(delete(Friendship).where(Friendship.user_low_id == low, Friendship.user_high_id == high))
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


async def partnership_view(session: AsyncSession, item: Partnership) -> PartnershipView:
    return PartnershipView(
        id=item.id,
        inviter=profile(await session.get(User, item.inviter_id)),
        invitee=profile(await session.get(User, item.invitee_id)),
        state=item.state.value,
        space_id=item.space_id,
        created_at=item.created_at,
    )


@app.post("/v1/partnerships", response_model=PartnershipView, status_code=status.HTTP_201_CREATED)
async def invite_partner(body: PartnershipCreate, actor: Actor, session: Session) -> PartnershipView:
    invitee = await user_by_username(session, body.username)
    if invitee.id == actor.user.id or await blocked(session, actor.user.id, invitee.id):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Invalid partner")
    active = await session.scalar(select(Partnership.id).where(or_(Partnership.inviter_id == actor.user.id, Partnership.invitee_id == actor.user.id), Partnership.state.in_([PartnershipState.pending, PartnershipState.active])))
    other_active = await session.scalar(select(Partnership.id).where(or_(Partnership.inviter_id == invitee.id, Partnership.invitee_id == invitee.id), Partnership.state.in_([PartnershipState.pending, PartnershipState.active])))
    if active or other_active:
        raise HTTPException(status.HTTP_409_CONFLICT, "One of the users already has a partnership")
    item = Partnership(inviter_id=actor.user.id, invitee_id=invitee.id)
    session.add(item)
    await session.commit()
    return await partnership_view(session, item)


@app.get("/v1/partnerships/current", response_model=PartnershipView | None)
async def current_partnership(actor: Actor, session: Session) -> PartnershipView | None:
    item = await session.scalar(select(Partnership).where(or_(Partnership.inviter_id == actor.user.id, Partnership.invitee_id == actor.user.id), Partnership.state.in_([PartnershipState.pending, PartnershipState.active])).order_by(Partnership.created_at.desc()))
    return await partnership_view(session, item) if item else None


@app.post("/v1/partnerships/{partnership_id}/accept", response_model=PartnershipView)
async def accept_partnership(partnership_id: str, actor: Actor, session: Session) -> PartnershipView:
    item = await session.get(Partnership, partnership_id)
    if not item or item.invitee_id != actor.user.id or item.state != PartnershipState.pending:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Pending partnership not found")
    space = Space(type=SpaceType.couple, owner_id=item.inviter_id)
    session.add(space)
    await session.flush()
    session.add_all([
        SpaceMember(space_id=space.id, user_id=item.inviter_id, role=MemberRole.owner),
        SpaceMember(space_id=space.id, user_id=item.invitee_id, role=MemberRole.editor),
    ])
    item.state = PartnershipState.active
    item.space_id = space.id
    item.accepted_at = now()
    await session.commit()
    return await partnership_view(session, item)


@app.delete("/v1/partnerships/{partnership_id}", status_code=status.HTTP_204_NO_CONTENT)
async def end_partnership(partnership_id: str, actor: Actor, session: Session) -> Response:
    item = await session.get(Partnership, partnership_id)
    if not item or actor.user.id not in {item.inviter_id, item.invitee_id} or item.state == PartnershipState.ended:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Partnership not found")
    item.state = PartnershipState.ended
    item.ended_at = now()
    if item.space_id:
        space = await session.get(Space, item.space_id)
        if space:
            space.archived_at = now()
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.post("/v1/spaces", response_model=SpaceView, status_code=status.HTTP_201_CREATED)
async def create_space(body: SpaceCreate, actor: Actor, session: Session) -> SpaceView:
    users = []
    for username in set(body.member_usernames):
        user = await user_by_username(session, username.removeprefix("@"))
        if user.id != actor.user.id and not await friendship_exists(session, actor.user.id, user.id):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Only friends can be invited")
        users.append(user)
    space = Space(type=SpaceType.shared, owner_id=actor.user.id, encrypted_name=body.encrypted_name)
    session.add(space)
    await session.flush()
    session.add(SpaceMember(space_id=space.id, user_id=actor.user.id, role=MemberRole.owner))
    for user in users:
        if user.id != actor.user.id:
            session.add(SpaceMember(space_id=space.id, user_id=user.id, role=MemberRole.editor))
    await session.commit()
    return SpaceView(id=space.id, type=space.type.value, owner_id=space.owner_id, encrypted_name=space.encrypted_name, key_version=space.key_version, role="owner")


@app.get("/v1/spaces", response_model=list[SpaceView])
async def spaces(actor: Actor, session: Session) -> list[SpaceView]:
    rows = await session.execute(
        select(Space, SpaceMember).join(SpaceMember, SpaceMember.space_id == Space.id).where(
            SpaceMember.user_id == actor.user.id,
            SpaceMember.removed_at.is_(None),
            Space.archived_at.is_(None),
        )
    )
    return [SpaceView(id=space.id, type=space.type.value, owner_id=space.owner_id, encrypted_name=space.encrypted_name, key_version=space.key_version, role=membership.role.value) for space, membership in rows]


@app.patch("/v1/spaces/{space_id}/members/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def update_member(space_id: str, user_id: str, body: MemberUpdate, actor: Actor, session: Session) -> Response:
    own = await member(session, space_id, actor.user.id)
    target = await member(session, space_id, user_id)
    if own.role != MemberRole.owner or target.role == MemberRole.owner:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Only owner can change roles")
    target.role = MemberRole(body.role)
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/v1/spaces/{space_id}/members", response_model=list[MemberView])
async def space_members(space_id: str, actor: Actor, session: Session) -> list[MemberView]:
    await member(session, space_id, actor.user.id)
    rows = list(await session.scalars(select(SpaceMember).where(SpaceMember.space_id == space_id, SpaceMember.removed_at.is_(None))))
    result = []
    for row in rows:
        user = await session.get(User, row.user_id)
        result.append(MemberView(profile=profile(user), role=row.role.value, joined_at=row.joined_at))
    return result


@app.delete("/v1/spaces/{space_id}/members/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_member(space_id: str, user_id: str, actor: Actor, session: Session) -> Response:
    own = await member(session, space_id, actor.user.id)
    target = await member(session, space_id, user_id)
    if actor.user.id != user_id and own.role != MemberRole.owner:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Only owner can remove another member")
    if target.role == MemberRole.owner:
        raise HTTPException(status.HTTP_409_CONFLICT, "Owner cannot be removed")
    target.removed_at = now()
    space = await session.get(Space, space_id)
    space.key_version += 1
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.put("/v1/spaces/{space_id}/envelopes", status_code=status.HTTP_204_NO_CONTENT)
async def put_envelope(space_id: str, body: EnvelopePut, actor: Actor, session: Session) -> Response:
    own = await member(session, space_id, actor.user.id)
    if own.role != MemberRole.owner:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Only owner can distribute space keys")
    device = await session.get(Device, body.device_id)
    if not device or device.revoked_at:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Device not found")
    await member(session, space_id, device.user_id)
    space = await session.get(Space, space_id)
    if body.key_version != space.key_version:
        raise HTTPException(status.HTTP_409_CONFLICT, "Stale key version")
    existing = await session.scalar(select(KeyEnvelope).where(KeyEnvelope.space_id == space_id, KeyEnvelope.device_id == body.device_id, KeyEnvelope.key_version == body.key_version))
    if existing:
        existing.envelope = body.envelope
    else:
        session.add(KeyEnvelope(space_id=space_id, device_id=body.device_id, key_version=body.key_version, envelope=body.envelope))
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/v1/spaces/{space_id}/envelope", response_model=EnvelopeView)
async def get_envelope(space_id: str, actor: Actor, session: Session) -> EnvelopeView:
    await member(session, space_id, actor.user.id)
    space = await session.get(Space, space_id)
    envelope = await session.scalar(select(KeyEnvelope).where(KeyEnvelope.space_id == space_id, KeyEnvelope.device_id == actor.device.id, KeyEnvelope.key_version == space.key_version))
    if not envelope:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Key envelope not found")
    return EnvelopeView(space_id=space_id, device_id=actor.device.id, key_version=envelope.key_version, envelope=envelope.envelope)


@app.post("/v1/sync/push", response_model=list[SyncResult])
async def sync_push(body: SyncPush, actor: Actor, session: Session) -> list[SyncResult]:
    results = []
    for mutation in body.mutations:
        membership = await member(session, mutation.space_id, actor.user.id)
        if membership.role == MemberRole.viewer:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Viewer cannot sync changes")
        space = await session.get(Space, mutation.space_id)
        if space.archived_at:
            raise HTTPException(status.HTTP_409_CONFLICT, "Space is archived")
        if mutation.key_version != space.key_version:
            results.append(SyncResult(record_id=mutation.record_id, accepted=False, conflict_revision=None))
            continue
        record = await session.scalar(select(EncryptedRecord).where(EncryptedRecord.space_id == mutation.space_id, EncryptedRecord.record_id == mutation.record_id))
        current_revision = record.revision if record else 0
        if mutation.base_revision != current_revision:
            results.append(SyncResult(record_id=mutation.record_id, accepted=False, conflict_revision=current_revision))
            continue
        revision = current_revision + 1
        if record:
            record.record_type = mutation.record_type
            record.key_version = mutation.key_version
            record.revision = revision
            record.ciphertext = mutation.ciphertext
            record.deleted = mutation.deleted
            record.author_id = actor.user.id
            record.updated_at = now()
        else:
            record = EncryptedRecord(
                space_id=mutation.space_id,
                record_id=mutation.record_id,
                record_type=mutation.record_type,
                key_version=mutation.key_version,
                revision=revision,
                ciphertext=mutation.ciphertext,
                deleted=mutation.deleted,
                author_id=actor.user.id,
            )
            session.add(record)
        session.add(ChangeLog(space_id=mutation.space_id, record_id=mutation.record_id, revision=revision))
        results.append(SyncResult(record_id=mutation.record_id, accepted=True, revision=revision))
    await session.commit()
    return results


@app.get("/v1/sync/pull", response_model=SyncPull)
async def sync_pull(actor: Actor, session: Session, cursor: int = Query(0, ge=0), limit: int = Query(100, ge=1, le=500)) -> SyncPull:
    memberships = select(SpaceMember.space_id).where(SpaceMember.user_id == actor.user.id, SpaceMember.removed_at.is_(None))
    changes = list(await session.scalars(select(ChangeLog).where(ChangeLog.cursor > cursor, ChangeLog.space_id.in_(memberships)).order_by(ChangeLog.cursor).limit(limit + 1)))
    has_more = len(changes) > limit
    changes = changes[:limit]
    records = []
    for change in changes:
        record = await session.scalar(select(EncryptedRecord).where(EncryptedRecord.space_id == change.space_id, EncryptedRecord.record_id == change.record_id))
        if record:
            records.append(SyncRecord.model_validate(record))
    return SyncPull(cursor=changes[-1].cursor if changes else cursor, has_more=has_more, records=records)


def storage(config: Settings) -> ObjectStorage:
    try:
        return ObjectStorage(config)
    except RuntimeError as error:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, str(error)) from error


@app.post("/v1/backups", response_model=BackupUpload, status_code=status.HTTP_201_CREATED)
async def create_backup(body: BackupCreate, actor: Actor, session: Session, config: Config) -> BackupUpload:
    item = Backup(user_id=actor.user.id, object_key="pending", size=body.size, sha256=body.sha256, format_version=body.format_version)
    session.add(item)
    await session.flush()
    item.object_key = f"users/{actor.user.id}/backups/{item.id}.ashw"
    signed = storage(config).upload(item.object_key, item.size, item.sha256)
    await session.commit()
    return BackupUpload(id=item.id, upload_url=signed.url, required_headers=signed.headers)


@app.post("/v1/backups/{backup_id}/complete", response_model=BackupView)
async def complete_backup(backup_id: str, actor: Actor, session: Session, config: Config) -> Backup:
    item = await session.get(Backup, backup_id)
    if not item or item.user_id != actor.user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Backup not found")
    metadata = storage(config).head(item.object_key)
    if metadata.get("ContentLength") != item.size or metadata.get("Metadata", {}).get("sha256") != item.sha256:
        raise HTTPException(status.HTTP_409_CONFLICT, "Uploaded object does not match metadata")
    item.status = "ready"
    await session.commit()
    return item


@app.get("/v1/backups", response_model=list[BackupView])
async def backups(actor: Actor, session: Session) -> list[Backup]:
    return list(await session.scalars(select(Backup).where(Backup.user_id == actor.user.id, Backup.status == "ready").order_by(Backup.created_at.desc()).limit(30)))


@app.get("/v1/backups/{backup_id}/download", response_model=BackupDownload)
async def download_backup(backup_id: str, actor: Actor, session: Session, config: Config) -> BackupDownload:
    item = await session.get(Backup, backup_id)
    if not item or item.user_id != actor.user.id or item.status != "ready":
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Backup not found")
    return BackupDownload(download_url=storage(config).download(item.object_key))


@app.delete("/v1/account", status_code=status.HTTP_202_ACCEPTED)
async def delete_account(actor: Actor, session: Session) -> dict[str, str]:
    # Немедленно блокируем вход. Очистка объектов выполняется отдельной фоновой
    # задачей с журналом удаления, чтобы сбой не оставил неизвестный остаток данных.
    actor.user.disabled_at = now()
    actor.user.username = f"deleted_{actor.user.id.replace('-', '')[:20]}"
    actor.user.display_name = "Удалённый пользователь"
    actor.user.bio = ""
    devices = await session.scalars(select(Device).where(Device.user_id == actor.user.id))
    for device in devices:
        device.revoked_at = now()
    await session.commit()
    return {"status": "scheduled"}
