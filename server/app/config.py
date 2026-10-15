from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ASHWAKE_", env_file=".env")

    environment: str = "development"
    database_url: str = "sqlite+aiosqlite:///./ashwake-dev.db"
    jwt_secret: str = Field(min_length=32)
    access_token_minutes: int = 10
    refresh_token_days: int = 30
    challenge_minutes: int = 5
    cors_origins: str = ""

    s3_endpoint: str | None = None
    s3_region: str = "ru-1"
    s3_bucket: str | None = None
    s3_access_key: str | None = None
    s3_secret_key: str | None = None

    @property
    def allowed_origins(self) -> list[str]:
        return [value.strip() for value in self.cors_origins.split(",") if value.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
