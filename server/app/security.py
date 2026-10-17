import base64
import hashlib
import secrets
from datetime import datetime, timedelta, timezone

import jwt
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import Settings
from .models import Device, RefreshSession, User, now
from .schemas import TokenPair


def digest(value: str) -> bytes:
    return hashlib.sha256(value.encode()).digest()


def expired(value: datetime) -> bool:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value < datetime.now(timezone.utc)


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def b64url_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def new_secret(size: int = 32) -> str:
    return b64url(secrets.token_bytes(size))


def validate_public_key(pem: str) -> None:
    try:
        key = serialization.load_pem_public_key(pem.encode())
    except ValueError as error:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Invalid public key") from error
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "A P-256 EC key is required")


def verify_signature(public_key_pem: str, challenge: str, signature: str) -> None:
    try:
        key = serialization.load_pem_public_key(public_key_pem.encode())
        assert isinstance(key, ec.EllipticCurvePublicKey)
        key.verify(b64url_decode(signature), challenge.encode(), ec.ECDSA(hashes.SHA256()))
    except (ValueError, InvalidSignature, AssertionError) as error:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid device signature") from error


def access_token(user: User, device: Device, settings: Settings) -> str:
    current = datetime.now(timezone.utc)
    return jwt.encode(
        {
            "sub": user.id,
            "device": device.id,
            "iat": current,
            "exp": current + timedelta(minutes=settings.access_token_minutes),
            "iss": "ashwake-api",
            "aud": "ashwake-android",
        },
        settings.jwt_secret,
        algorithm="HS256",
    )


async def issue_tokens(
    session: AsyncSession,
    user: User,
    device: Device,
    settings: Settings,
) -> TokenPair:
    raw_refresh = new_secret(48)
    session.add(
        RefreshSession(
            user_id=user.id,
            device_id=device.id,
            token_hash=digest(raw_refresh),
            expires_at=now() + timedelta(days=settings.refresh_token_days),
        )
    )
    device.last_seen_at = now()
    await session.commit()
    return TokenPair(
        access_token=access_token(user, device, settings),
        refresh_token=raw_refresh,
        expires_in=settings.access_token_minutes * 60,
        user_id=user.id,
        device_id=device.id,
    )


async def rotate_refresh(
    session: AsyncSession,
    raw_token: str,
    settings: Settings,
) -> TokenPair:
    refresh = await session.scalar(
        select(RefreshSession).where(RefreshSession.token_hash == digest(raw_token))
    )
    if not refresh or expired(refresh.expires_at):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid refresh token")
    if refresh.revoked_at:
        # Повторное использование старого токена означает возможную кражу:
        # отзываем всю цепочку сессий этого устройства.
        rows = await session.scalars(
            select(RefreshSession).where(RefreshSession.device_id == refresh.device_id)
        )
        for item in rows:
            item.revoked_at = now()
        await session.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token reuse detected")

    user = await session.get(User, refresh.user_id)
    device = await session.get(Device, refresh.device_id)
    if not user or user.disabled_at or not device or device.revoked_at:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Session revoked")

    refresh.revoked_at = now()
    pair = await issue_tokens(session, user, device, settings)
    replacement = await session.scalar(
        select(RefreshSession).where(RefreshSession.token_hash == digest(pair.refresh_token))
    )
    refresh.replaced_by_id = replacement.id if replacement else None
    await session.commit()
    return pair
