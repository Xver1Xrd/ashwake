# Ashwake

Офлайн-first Android-приложение: задачи, привычки, отказы, рутины, фокус
и собираемый пиксельный персонаж.

Без аккаунтов. Без сетевых вызовов. Без аналитики и сторонних SDK.

## Стек

- Kotlin, Jetpack Compose, Material 3 (динамические цвета, тёмная тема по умолчанию)
- Room + Flow, ViewModel + StateFlow, Hilt, WorkManager
- DataStore (настройки), TextToSpeech (голос в рутинах), Glance (виджеты)
- minSdk 26, targetSdk 35, один модуль, слои `data / domain / ui`

## Документация

- [docs/01-structure.md](docs/01-structure.md) — структура пакетов
- [docs/02-database.md](docs/02-database.md) — схема базы данных под всё ТЗ
- [docs/03-plan.md](docs/03-plan.md) — разбивка работы по этапам

## Статус

| Этап | Что | Готово |
|---|---|---|
| 0 | Каркас: Gradle, Hilt, Room, Compose, тема, навигация | ✅ |
| 1 | Задачи: проекты, теги, приоритеты, повторы, переносы, быстрый ввод, матрица | ✅ базовый объём |
| 2 | Привычки | — |
| 3 | Отказы | — |
| 4 | Персонаж на плейсхолдерах | — |
| 5–11 | Рутины, фокус, таймбоксинг, ритуал, аналитика, блокировка, импорт, бэкапы | — |

## Сборка

```bash
# debug-APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# юнит-тесты чистых классов (без эмулятора)
./gradlew :app:testDebugUnitTest

# установка на подключённое устройство
./gradlew :app:installDebug
```

Требуется JDK 17+ и Android SDK 35. Путь к SDK — в `local.properties`
(`sdk.dir=/путь/к/Android/Sdk`), файл не коммитится.
