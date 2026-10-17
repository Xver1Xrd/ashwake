from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from .config import Settings, get_settings
from .db import get_session
from .models import Device, User


bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Principal:
    user: User
    device: Device


async def principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    session: AsyncSession = Depends(get_session),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if not credentials or credentials.scheme.lower() != "bearer":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Authentication required")
    try:
        claims = jwt.decode(
            credentials.credentials,
            settings.jwt_secret,
            algorithms=["HS256"],
            audience="ashwake-android",
            issuer="ashwake-api",
        )
        user_id = claims["sub"]
        device_id = claims["device"]
    except (jwt.InvalidTokenError, KeyError) as error:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid access token") from error
    user = await session.get(User, user_id)
    device = await session.get(Device, device_id)
    if not user or user.disabled_at or not device or device.user_id != user.id or device.revoked_at:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Session revoked")
    return Principal(user, device)
