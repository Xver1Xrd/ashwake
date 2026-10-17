# Ashwake API

API хранит аккаунты, граф связей, права и зашифрованные записи. Содержимое задач
шифруется на Android-устройстве; сервер получает только ciphertext, тип объекта,
версию ключа и служебные идентификаторы.

## Локальный запуск

```bash
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
export ASHWAKE_JWT_SECRET="$(openssl rand -base64 48)"
export ASHWAKE_ENVIRONMENT=development
.venv/bin/uvicorn app.main:app --reload --port 8080
```

Swagger намеренно отключён в production-приложении. Проверка доступности:
`GET /healthz`.

## Selectel

1. Создать приватный S3 bucket без публичного чтения.
2. Создать отдельный S3-ключ только для этого bucket.
3. Скопировать `.env.example` в `.env` и заменить все `CHANGE_ME`.
4. Указать `ASHWAKE_DOMAIN=api.example.ru` и выполнить `docker compose up -d`.
5. Ограничить SSH по IP, включить автоматические security-обновления и резервные
   копии PostgreSQL в отдельный bucket с retention policy.

Файл `.env` не коммитится. Доступ Android выдаётся коротким JWT, refresh-токены
хранятся на сервере только как SHA-256 и ротируются при каждом обновлении.
Повторное использование старого refresh-токена отзывает сессии устройства.

Контейнер API перед каждым запуском выполняет `alembic upgrade head`. Автосоздание
таблиц самим приложением разрешено только при `development` или `test`.
