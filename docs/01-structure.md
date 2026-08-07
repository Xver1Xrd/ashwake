# Этап 1a. Структура пакетов

Корневой пакет: `dev.ashwake`. Один Gradle-модуль, три слоя, пакеты по фичам внутри слоёв.

Девять фич-пакетов сквозные для всех слоёв:
`tasks`, `habits`, `abstinence`, `routines`, `focus`, `character`, `analytics`, `backup`, `blocking`.

```
dev.ashwake
├── AshwakeApp.kt                  // @HiltAndroidApp, инициализация WorkManager
├── MainActivity.kt                // единственная Activity, host для Compose-навигации
│
├── core/                          // общее, не привязано к фиче
│   ├── time/                      // Clock, границы суток, LocalDate/LocalTime-хелперы
│   ├── model/                     // Sphere, Weekday, Priority — общие перечисления
│   ├── result/                    // Result/Failure-обёртки
│   └── ext/                       // расширения Kotlin и Compose
│
├── domain/                        // чистый Kotlin, без единого импорта android.*
│   ├── model/
│   │   ├── tasks/                 // Task, Project, Tag, RecurrenceRule, TimeboxBlock
│   │   ├── habits/                // Habit, HabitEntry, HabitSchedule, Anchor, Freeze, Pause
│   │   ├── abstinence/            // Abstinence, Attempt, Baseline, Milestone, CravingEvent
│   │   ├── routines/              // Routine, RoutineStep, RoutineSession
│   │   ├── focus/                 // FocusSession
│   │   ├── character/             // EquipItem, ItemEffect, Palette, Affix, ItemSet, Stat
│   │   ├── economy/               // Wallet, LedgerTransaction, UserReward
│   │   ├── ritual/                // DailyReview
│   │   └── blocking/              // BlockingRule, BlockedApp
│   │
│   ├── repository/                // ТОЛЬКО интерфейсы, по одному пакету на фичу
│   │   ├── tasks/ habits/ abstinence/ routines/ focus/
│   │   └── character/ economy/ ritual/ blocking/ analytics/ backup/
│   │
│   ├── engine/                    // чистые калькуляторы, покрыты юнит-тестами (п. 19.3)
│   │   ├── habit/
│   │   │   ├── HabitScoreCalculator.kt      // экспоненциальное сглаживание, п. 3
│   │   │   └── StreakCalculator.kt          // стрик с учётом заморозок и пауз
│   │   ├── timebox/
│   │   │   └── TimeboxPlanner.kt            // раскладка дня, п. 2
│   │   ├── abstinence/
│   │   │   └── AbstinenceCalculator.kt      // счётчик, вехи, экономия, GENTLE/STRICT
│   │   ├── analytics/
│   │   │   ├── CorrelationAnalyzer.kt       // Пирсон, лаг 0/1, порог 14 дней
│   │   │   └── WeeklyReportBuilder.kt
│   │   ├── reward/
│   │   │   ├── RewardEngine.kt              // единственная точка начисления/списания
│   │   │   └── RewardConfig.kt              // ВСЕ коэффициенты баланса в одном файле
│   │   ├── character/
│   │   │   ├── EquipmentEngine.kt           // эффекты, аффиксы, сеты, потолки, п. 16.6
│   │   │   ├── StatProgressCalculator.kt    // floor(sqrt(points/4))
│   │   │   ├── LayerResolver.kt             // hides + z-порядок, п. 15.3
│   │   │   └── ItemBudgetValidator.kt       // бюджет редкости, п. 16.5.3
│   │   └── nlp/
│   │       ├── QuickInputParser.kt          // «купить молоко завтра 18:00 !p2 #дом ~30м»
│   │       └── RuDateGrammar.kt             // русская грамматика дат
│   │
│   └── usecase/                   // по фичам; оркестрация репозиториев и движков
│       └── tasks/ habits/ abstinence/ routines/ focus/ character/ ritual/ analytics/
│
├── data/
│   ├── db/
│   │   ├── AshwakeDatabase.kt
│   │   ├── entity/                // по фичам, зеркалит domain/model
│   │   ├── dao/                   // по фичам
│   │   ├── converter/             // Instant↔Long, LocalDate↔Int, enum↔String
│   │   ├── view/                  // @DatabaseView для тяжёлых выборок статистики
│   │   └── migration/             // пусто до 1.0, дальше по одному файлу на шаг
│   │
│   ├── settings/                  // DataStore Preferences: тема, рабочие часы, буфер,
│   │                              // время ритуала, квоты заморозок, «уменьшить движение»
│   ├── repository/                // реализации domain/repository, по фичам
│   ├── assets/                    // ЧИТАЕТСЯ ИЗ assets/, не из БД:
│   │   ├── CatalogLoader.kt       // items.json, palettes.json, affixes.json, sets.json
│   │   ├── SpriteManifestLoader.kt// sprites/manifest.json + валидация (п. 15.4)
│   │   ├── HabitPresetLoader.kt   // каталог 60+ готовых привычек
│   │   └── AchievementLoader.kt
│   ├── importer/
│   │   ├── loop/                  // CSV + чтение SQLite-базы Loop Habit Tracker
│   │   ├── ticktick/              // CSV
│   │   ├── todoist/               // CSV/JSON
│   │   └── ImportReport.kt
│   ├── backup/
│   │   ├── JsonBackupSerializer.kt
│   │   ├── CsvExporter.kt
│   │   └── ArchiveCrypto.kt       // PBKDF2 → AES-256-GCM, соль и nonce в заголовке
│   └── calendar/
│       └── SystemCalendarReader.kt// READ_CALENDAR, только чтение, для таймбоксинга
│
├── ui/
│   ├── theme/                     // Material 3, динамические цвета, тёмная по умолчанию
│   ├── navigation/                // NavHost, маршруты, deep links из уведомлений
│   ├── components/                // общие: свайп-строка, heatmap, кольцо прогресса, графики
│   ├── home/                      // главный экран: диорама + персонаж (п. 15.9)
│   ├── today/                     // «Сегодня»
│   ├── tasks/                     // список, матрица Эйзенхауэра, календарь, шкала дня
│   ├── habits/                    // карточки, отметка, heatmap-год
│   ├── abstinence/                // список, экран отказа, срыв, тяга
│   ├── routines/                  // редактор и полноэкранный режим выполнения
│   ├── focus/                     // помодоро и секундомер
│   ├── ritual/                    // вечерний ритуал, 5 шагов
│   ├── analytics/                 // отчёты, корреляции, «Год в цифрах»
│   ├── character/                 // экипировка, магазин, характеристики, пресеты
│   │   └── render/                // PixelCharacterRenderer, кэш композита, анимации
│   ├── blocking/                  // настройка правил, экран-объяснение разрешений
│   ├── backup/                    // экспорт/импорт, мастер импорта
│   └── settings/
│
├── platform/                      // тонкая обвязка Android, вызывается из ui/data
│   ├── notification/              // каналы, «настойчивое напоминание», действия в шторке
│   ├── alarm/                     // AlarmManager, SCHEDULE_EXACT_ALARM
│   ├── work/                      // WorkManager: бэкап, недельный отчёт, вехи, пересчёт score
│   ├── service/                   // foreground: фокус, рутина, блокировка
│   ├── widget/                    // Glance-виджеты (п. 18)
│   ├── tile/                      // Quick Settings tiles
│   ├── shortcut/                  // App Shortcuts + ACTION_SEND
│   ├── tts/                       // объявление шагов рутины
│   ├── speech/                    // SpeechRecognizer (ru), микрофон в быстром вводе
│   └── sensor/                    // параллакс диорамы по наклону
│
└── di/                            // Hilt-модули: DatabaseModule, RepositoryModule,
                                   // EngineModule, WorkerModule, PlatformModule
```

## Assets

```
assets/
├── sprites/
│   ├── manifest.json              // валидируется на старте: файл есть, ширина кратна 128,
│   │                              // слот известен. Ошибка → краш в debug, пропуск в release
│   ├── body/ head/ chest/ legs/ boots/ gloves/ belt/ shoulders/ cloak/
│   ├── mainhand/ offhand/ back/ face/ hair/ fx/
│   └── _reference/                // сборки всех слотов вместе, для проверки стыков
├── catalog/
│   ├── items.json                 // ~900 записей каталога
│   ├── palettes.json              // рампы материалов + LUT перекраски
│   ├── affixes.json               // префиксы и суффиксы
│   ├── sets.json                  // сеты и бонусы 2/4/6
│   └── achievements.json
└── presets/
    ├── habits.json                // каталог 60+ готовых привычек по категориям
    ├── routines.json              // утро, вечер, тренировка, глубокая работа
    └── milestones.json            // необязательные шаблоны текстов к вехам
```

## Три решения, которые стоит зафиксировать сейчас

1. **Каталог предметов живёт в assets, а не в БД.** В базе только владение, апгрейд
   и экипировка. Правка баланса или добавление предметов не требует миграции Room.
2. **Спрайт уже знает своё место** (холст 128×128 со смещением) — в коде нет ни одного
   offset'а, только z-порядок и `hides`.
3. **`platform/` вынесен из `ui/` и `data/`.** Уведомления, воркеры и сервисы дёргают
   usecase-слой, а не репозитории напрямую, — иначе логика начислений расползётся
   мимо `RewardEngine`.
