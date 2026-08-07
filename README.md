# Ashwake

Офлайн-first Android-приложение: задачи, привычки, отказы, рутины, фокус
и собираемый пиксельный персонаж.

Без аккаунтов. Без сетевых вызовов. Без аналитики и сторонних SDK.

## Стек

- Kotlin, Jetpack Compose, Material 3 (динамические цвета, тёмная тема по умолчанию)
- Room + Flow, ViewModel + StateFlow, Hilt, WorkManager
- DataStore (настройки), TextToSpeech (голос в рутинах), Glance (виджеты)
- minSdk 26, targetSdk 35, один модуль, слои `data / domain / ui`

## Статус

Этап 1 — проектирование. Структура пакетов и схема БД:

- [docs/01-structure.md](docs/01-structure.md) — структура пакетов
- [docs/02-database.md](docs/02-database.md) — схема базы данных
- [docs/03-plan.md](docs/03-plan.md) — разбивка работы по этапам

Код появится после подтверждения схемы.
