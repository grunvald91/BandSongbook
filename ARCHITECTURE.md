# Band Book — карта системы

**CURRENT STATE** подтверждается кодом/конфигурацией, а состояние production — только прямой проверкой среды. **INTENDED STATE** задаётся решениями владельца и утверждёнными `PRODUCT.md`/`RULES.md`. Расхождения — **IMPLEMENTATION GAP**; одна категория не подменяет другую.

## CURRENT STATE — компоненты и поток данных

| Компонент | Роль | Где смотреть |
| --- | --- | --- |
| Android | Kotlin/Compose UI; Room `band_songbook.db` (миграции до v6); настройки DataStore; аудио; ручная и фоновая синхронизация WorkManager | `app/src/main/java/com/fithealthzone/bandsongbook/{AppContainer.kt,data/local/,data/settings/,data/sync/,sync/,media/,ui/}` |
| Web/PWA | React/Vite UI; выбранная группа и параметры отображения в localStorage; запросы sync API и аудио; service worker не кэширует API и медиа | `BandBook-v2/src/{App.tsx,sync/,pages/}`, `BandBook-v2/public/sw.js` |
| Sync-server | FastAPI `/sync/pull`, `/sync/push`, `/sync/meta` и `/audio/*`; полный снимок группы в SQLite; опциональное локальное или S3-compatible хранилище аудио | `sync-server/fastapi_server.py`, `sync-server/README.md` |

`sync-server/server.py` — альтернативный простой сервер на `store.json`, не обязательный компонент FastAPI. Фактически запущенный вариант сервера, состояние БД, объектного хранилища, бэкапов, auth и опубликованных клиентов **не подтверждены** этим документом.

Android имеет локальную библиотеку и в текущей рабочей копии выполняет pull → merge → push; код слияния применяет LWW и tombstone, оставляя некоторые личные параметры на устройстве. Web `feature/prod` читает и отправляет **целый снимок**; выбранная группа хранится в браузере. Сервер при push заменяет групповой снимок. На веб-сайте предполагается same-origin nginx-прокси `/sync/` и `/audio/` к `:8787` (`BandBook-v2/scripts/deploy.py`), но фактическая конфигурация VPS не проверена. Остаточные Firebase-модули/правила в Web не являются доказательством защиты sync API.

## INTENDED STATE — утверждённые границы

Группы не смешивают данные. Удаления, неизвестные старому клиенту поля, личные настройки музыканта и временные аудио-URL сохраняют семантику из `RULES.md`. Конфликты разрешаются предсказуемо и не сводятся к слепому «последний полный push затёр всё». Android LWW/tombstone — возможная основа, **не утверждённый окончательный межклиентский алгоритм**. Новая модель owner/admin не утверждена.

## IMPLEMENTATION GAP / не подтверждено

- Межклиентский контракт Android + Web + server для параллельных правок, tombstone, воскрешения и старых клиентов не проверен; нынешний полный Web-push/серверная замена могут конфликтовать с intended state. Требуются технический review и конфликтные тесты.
- Защита sync API и фактические права участника на production не подтверждены. Клиентская поддержка токена и серверные режимы `off|bearer|jwt|auto` не доказывают, какой режим действует.
- Восстановление SQLite, аудио и клиентских данных/откат не описаны единым проверенным runbook.

Детали форматов, миграций и API не дублируются здесь: см. `app/src/main/java/com/fithealthzone/bandsongbook/data/sync/`, `data/local/AppDatabase.kt`, `sync-server/README.md`, `BandBook-v2/src/sync/`. Исторические объяснения проектных решений: `BandBook-v2/docs/superpowers/specs/`; сверять с нынешним кодом и утверждениями владельца.
