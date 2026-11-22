# Лабораторна робота №6. Quartz: планування задач

**Проєкт:** VAG.CV Fleet & Garage Platform (`com.vagcv.garage`)
**Гілка:** `lab-6-quartz`
**Звіт попередньої роботи:** [docs/reports/lab-01.md](docs/reports/lab-01.md)

---

## 1. Мета роботи

Додати до проєкту планувальник **Quartz** так, щоб періодичні задачі були:

- **постійні**: jobs і triggers зберігаються в PostgreSQL (JDBC job store), а не в пам'яті, тому переживають перезапуск застосунку;
- **керовані конфігурацією**: параметри передаються через `JobDataMap`, значення тригера перекривають значення job-а;
- **безпечні**: ідемпотентні, без паралельного накладання, з явною політикою пропущених запусків (misfire), часових поясів і збоїв.

## 2. Що реалізовано в предметній області автогаража

| Job | Що робить | Тригери |
|---|---|---|
| `stale-repair-reminder` (група `garage-maintenance`) | Знаходить авто зі статусом `IN_REPAIR`, які надто довго не оновлювались, і створює нагадування в таблиці `repair_reminders` | **2 тригери**: `stale-repair-daily` (щодня 09:00 Київ) і `stale-repair-demo-2min` (кожні 2 хв, для демонстрації) |
| `fleet-snapshot` (група `garage-reporting`) | Записує щоденний знімок автопарку (кількість авто за статусами, очікуваний прибуток) у `fleet_snapshots` | `fleet-snapshot-nightly` (щодня 23:30 Київ) |

Додатково для демонстрації й перевірки додано admin REST API (лише `ADMIN`, правило з ЛР 1 `/api/admin/**`): перегляд jobs/triggers із БД Quartz, негайний ручний запуск job-а, перегляд результатів.

## 3. Архітектура

```mermaid
flowchart LR
    subgraph Spring Boot
      QC[QuartzConfig<br/>JobDetail + Trigger beans] --> S[(Scheduler)]
      S -->|fires| J1[StaleRepairJob]
      S -->|fires| J2[FleetSnapshotJob]
      J1 -->|1 виклик| SV1[StaleRepairService<br/>@Transactional]
      J2 -->|1 виклик| SV2[FleetSnapshotService<br/>@Transactional]
      SV1 --> R1[(repair_reminders)]
      SV2 --> R2[(fleet_snapshots)]
      API[SchedulerAdminController<br/>/api/admin/**] --> S
    end
    S <-->|JDBC job store| Q[(QRTZ_* таблиці PostgreSQL)]
```

Структура коду (`src/main/java/com/vagcv/garage/`):

| Файл | Призначення |
|---|---|
| `scheduling/QuartzConfig` | Єдине місце, де описані всі `JobDetail` і `Trigger` |
| `scheduling/QuartzNames` | Явні імена/групи jobs і triggers (`JobKey`, `TriggerKey`) |
| `scheduling/StaleRepairJob`, `FleetSnapshotJob` | **Тонкі** job-класи: прочитати параметри, викликати ОДИН метод сервісу |
| `scheduling/JobParams` | Читання `JobDataMap` з явною політикою відсутніх/помилкових ключів |
| `scheduling/SchedulerAdminService` | Обгортка над `Scheduler` для admin API |
| `service/StaleRepairService`, `FleetSnapshotService` | Уся бізнес-логіка і транзакції |
| `domain/RepairReminder`, `FleetSnapshot` + `repository/*` | Нові сутності й репозиторії |
| `web/api/SchedulerAdminController` | `/api/admin/scheduler/**`, `/api/admin/reminders`, `/api/admin/snapshots` |
| `db/changelog/changes/003-quartz-schema.yaml` (+ `sql/quartz-postgres.sql`) | Таблиці `QRTZ_*` |
| `db/changelog/changes/004-lab6-tables.yaml` | `repair_reminders`, `fleet_snapshots` |

## 4. Збереження стану в БД (JDBC job store)

`application.yml`:

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never       # схему створює Liquibase
    overwrite-existing-jobs: true
    properties:
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
      org.quartz.jobStore.misfireThreshold: "60000"
```

- Таблиці `QRTZ_*` створює **Liquibase** (changeSet `003-quartz-schema`), а не Spring: так уся схема БД проєкту керується одним інструментом (як і вимагає ЛР 1: `ddl-auto: none`). Тому `initialize-schema: never`.
- `overwrite-existing-jobs: true`: якщо змінити розклад у `QuartzConfig` і перезапустити застосунок, збережений у БД job/trigger оновиться, а не залишиться старим.
- Після перезапуску застосунку jobs і triggers **не реєструються з нуля**: планувальник читає їх із БД і продовжує за розкладом (див. скріншот 12).

## 5. JobDataMap: значення за замовчуванням і перекриття

`JobDetail` `stale-repair-reminder` має значення за замовчуванням: `staleDays=7`, `maxRetries=2`.

| Тригер | Власні дані | Результат у `context.getMergedJobDataMap()` |
|---|---|---|
| `stale-repair-daily` | немає | `staleDays=7` (з job), `maxRetries=2` |
| `stale-repair-demo-2min` | `staleDays=0` | **`staleDays=0`** (перекрито тригером), `maxRetries=2` (з job) |

Quartz будує змерджену мапу так: спочатку дані `JobDetail`, поверх них дані `Trigger`. Job завжди читає саме `getMergedJobDataMap()`.

**Політика відсутніх/помилкових ключів (`JobParams`):**

| Ситуація | Поведінка |
|---|---|
| Обов'язковий ключ `staleDays` відсутній або написаний з помилкою (`stalDays`) | `JobExecutionException` з повідомленням `Missing required JobDataMap key 'staleDays' ... Keys present: [...]`, **без повторів** (конфігурація сама не виправиться), сервіс не викликається |
| `staleDays` не число або від'ємне | `JobExecutionException`, без повторів |
| Необов'язковий ключ `maxRetries` відсутній | значення за замовчуванням `2` |
| Необов'язковий ключ `simulateFailure` відсутній | `false` |

## 6. Ключові рішення

### 6.1 Тонкі job-класи, транзакція в сервісі
Job лише читає параметри й викликає один метод сервісу. Транзакція (`@Transactional`) стоїть на методі сервісу: уся пакетна операція або фіксується повністю, або відкочується повністю. Job-класи не мають ні бізнес-логіки, ні доступу до репозиторіїв. Spring Boot створює job-класи через `AutowireCapableBeanFactory`, тому працює звичайна ін'єкція через конструктор.

### 6.2 Заборона паралельного виконання
Обидва job-класи позначені `@DisallowConcurrentExecution`. Приклад: 2-хвилинний тригер не накладеться на ще не завершений запуск того самого job-а. У БД це видно як `is_nonconcurrent = true` у `qrtz_job_details`.

### 6.3 Ідемпотентність
| Job | Як гарантується |
|---|---|
| `stale-repair-reminder` | Перед вставкою `existsByVehicleIdAndReminderDate`; у БД додатково `UNIQUE (vehicle_id, reminder_date)`: одне нагадування на авто на день |
| `fleet-snapshot` | Перед вставкою `existsBySnapshotDate`; у БД `UNIQUE (snapshot_date)`: один знімок на день |

Повторний запуск (ручний, після збою чи після перезапуску) нічого не дублює. Бізнес-день рахується за київським часом (`Europe/Kyiv`), а не за поясом сервера.

### 6.4 Misfire (пропущені запуски)
Misfire це запуск, який не відбувся вчасно (застосунок був вимкнений або всі потоки зайняті) і затримка перевищила `misfireThreshold` (60 с). Для кожного тригера політика задана **явно**:

| Тригер | Політика | Обґрунтування |
|---|---|---|
| `stale-repair-daily` | `FIRE_ONCE_NOW` | Нагадування важливі: якщо застосунок був вимкнений о 09:00, після старту виконати один раз (а не N разів) |
| `stale-repair-demo-2min` | `DO_NOTHING` | Демо-запуск кожні 2 хв: пропущені ніхто не наздоганяє, чекаємо наступного слота |
| `fleet-snapshot-nightly` | `FIRE_ONCE_NOW` | Знімок за добу має бути; ідемпотентність гарантує, що зайвий запуск безпечний |

### 6.5 Часові пояси
Усі cron-тригери мають **явний** пояс `Europe/Kyiv` (`.inTimeZone(...)`), тому "09:00" це 09:00 за Києвом і влітку (06:00 UTC), і взимку (07:00 UTC), незалежно від налаштувань машини. Пояс зберігається в `qrtz_cron_triggers.time_zone_id`. Перевірено unit-тестом на літній і зимовий час.

### 6.6 Стратегія збоїв
| Job | Стратегія |
|---|---|
| `stale-repair-reminder` | **Обмежений retry.** При збої job просить Quartz повторити його негайно (`refireImmediately`), але не більше `maxRetries` разів (лічильник `context.getRefireCount()`). Усього спроб: `maxRetries + 1` = 3. Після цього в лог пишеться ERROR, а наступна спроба буде на наступному плановому спрацюванні. Нескінченного циклу немає |
| `fleet-snapshot` | **"Записати і чекати".** Без негайних повторів: помилка логується, наступний плановий запуск повторить спробу. Ідемпотентність робить безпечним і ручний перезапуск |

Збій можна змоделювати через admin API (`simulateFailure=true`).

### 6.7 Явні імена, групи та прив'язка через JobKey
Усі імена й групи задані явно в `QuartzNames`. Тригери прив'язуються до job-а через `.forJob(JobKey)`, а не через об'єкт `JobDetail`.

## 7. Admin REST API

Усе під `/api/admin/**` доступне лише `ADMIN` (правило з ЛР 1): механік отримує `403`, без токена `401`.

| Метод і шлях | Що робить |
|---|---|
| `GET /api/admin/scheduler/jobs` | Jobs і triggers **з БД Quartz**: клас, durable, nonconcurrent, JobDataMap, cron, часовий пояс, політика misfire, стан, `previousFireTime`, `nextFireTime` |
| `POST /api/admin/scheduler/jobs/{group}/{name}/run` | Негайний одноразовий запуск. Необов'язкові параметри `staleDays`, `simulateFailure` перекривають JobDataMap лише для цього запуску. Відповідь `202` |
| `GET /api/admin/reminders` | Створені нагадування |
| `GET /api/admin/snapshots` | Знімки автопарку |

Postman-колекція: `postman/vagcv-garage-lab6.postman_collection.json` (папка `0` токени, папка `6` Quartz).

## 8. Відповідність вимогам лабораторної

| Вимога | Де виконано | Чим підтверджено |
|---|---|---|
| JDBC job store, таблиці QRTZ_* через Liquibase, `initialize-schema: never` | `application.yml`, `003-quartz-schema.yaml` | скріншоти 01, 02, 03 |
| Не менше двох job-класів, JobDetail і Trigger бінами в одній `@Configuration` | `QuartzConfig` | `QuartzConfigTest`, скріншоти 03, 04 |
| Хоча б один job з двома тригерами | `stale-repair-reminder`: daily + demo-2min | `triggersPointToTheirJobsByJobKey`, скріншот 04 |
| JobDataMap: defaults у job, перекриття в тригері | `QuartzConfig`, `StaleRepairJob` | `triggerJobDataOverridesJobDetailDefaults`, скріншот 06 |
| Тонкі jobs, транзакція в сервісі | `StaleRepairJob`, `FleetSnapshotJob`, `*Service` | `StaleRepairJobTest` (сервіс у моку) |
| `@DisallowConcurrentExecution` | обидва job-класи | `jobsAreMarkedDisallowConcurrentExecution`, скріншот 03 (`is_nonconcurrent`) |
| Явна політика misfire для кожного тригера | `QuartzConfig`, розділ 6.4 | `misfireInstructionsAreExplicit`, скріншот 04 (`misfire_instr`) |
| Cron із явним часовим поясом | `QuartzConfig`, розділ 6.5 | `dailyTriggerFiresAt0900KyivRegardlessOfServerZone`, скріншот 05 |
| Ідемпотентна логіка | `StaleRepairService`, `FleetSnapshotService` | `runningTwiceIsIdempotent`, `secondRunOnSameDayIsSkipped_idempotent`, скріншоти 08, 09, 10 |
| Явна стратегія збоїв без нескінченного циклу | розділ 6.6 | `failureWithinRetryBudget...`, `failureAfterRetriesExhausted...`, скріншот 11 |
| Явна поведінка при відсутньому/помилковому ключі JobDataMap | `JobParams`, розділ 5 | `missingRequiredKey...`, `misspelledKeyIsTreatedAsMissing`, скріншот 12 |
| Явні імена й групи, прив'язка через JobKey | `QuartzNames`, `QuartzConfig` | `jobsHaveExplicitNamesAndGroups`, `triggersPointToTheirJobsByJobKey` |

## 9. Тести

```
mvn test
```

| Клас | Що перевіряє |
|---|---|
| `QuartzConfigTest` | Імена/групи, прив'язка тригерів через `JobKey`, `@DisallowConcurrentExecution`, мердж `JobDataMap`, політики misfire, часовий пояс і час спрацювання влітку/взимку |
| `StaleRepairJobTest` | Відсутній і помилково написаний ключ, від'ємне значення, retry у межах бюджету, відсутність retry після вичерпання, значення `maxRetries` за замовчуванням |
| `StaleRepairServiceTest` | Відбір застарілих авто, ідемпотентність, київська дата замість UTC, відхилення від'ємних `staleDays` |
| `FleetSnapshotServiceTest` | Створення знімка, ідемпотентність, відсутність зайвого читання при існуючому знімку |
| `MethodSecurityTest` | З ЛР 1: `@PreAuthorize` |

Тести не потребують БД і Keycloak: сервіси перевіряються з моками репозиторіїв, конфігурація Quartz перевіряється звичайним викликом бін-методів.

## 10. Запуск

```
docker compose up -d
mvn spring-boot:run          # або запуск GarageApplication з IntelliJ
```

Liquibase при старті створить QRTZ_* та нові таблиці. У консолі з'являться рядки Quartz про запуск планувальника, а далі кожні 2 хвилини рядки `StaleRepairJob started`.

Токен і запити: Postman, колекція `postman/vagcv-garage-lab6.postman_collection.json`.

Перегляд БД Quartz у терміналі:

```
docker exec vagcv-postgres psql -U garage -d garage -c "SELECT job_name, job_group, job_class_name, is_durable, is_nonconcurrent FROM qrtz_job_details;"
docker exec vagcv-postgres psql -U garage -d garage -c "SELECT trigger_name, job_name, trigger_state, misfire_instr, next_fire_time FROM qrtz_triggers;"
```

## 11. Скріншоти

Усі файли в `docs/screenshots/lab-06/`.

### База даних і запуск

**01. QRTZ_* таблиці створені Liquibase**
![01](docs/screenshots/lab-06/01-db-qrtz-tables.png)
Команда `\dt`: видно `qrtz_*`, `repair_reminders`, `fleet_snapshots`, `databasechangelog`.

**02. Планувальник стартував із JDBC job store**
![02](docs/screenshots/lab-06/02-startup-log-jdbc-jobstore.png)
У логу: `Using job-store 'org.quartz.impl.jdbcjobstore.JobStoreTX'` і `Scheduler ... started`.

**03. Jobs у БД**
![03](docs/screenshots/lab-06/03-db-job-details.png)
`qrtz_job_details`: два jobs, `is_durable=t`, `is_nonconcurrent=t`.

**04. Triggers у БД**
![04](docs/screenshots/lab-06/04-db-triggers.png)
`qrtz_triggers`: три тригери, `stale-repair-reminder` має два; видно `misfire_instr` і `next_fire_time`.

**05. Cron і часовий пояс**
![05](docs/screenshots/lab-06/05-db-cron-timezone.png)
`qrtz_cron_triggers`: cron-вирази й `time_zone_id = Europe/Kyiv`.

### Робота jobs

**06. Список jobs/triggers через API**
![06](docs/screenshots/lab-06/06-api-jobs-list.png)
`GET /api/admin/scheduler/jobs`: JobDataMap job-а (`staleDays=7`) і перекриття в demo-тригері (`staleDays=0`).

**07. Автоматичний запуск за розкладом**
![07](docs/screenshots/lab-06/07-log-demo-trigger-fires.png)
У консолі кожні 2 хв: `StaleRepairJob started: trigger=garage-maintenance.stale-repair-demo-2min, staleDays=0`.

**08. Стратегія збоїв (3 спроби, без нескінченного циклу)**
![08](docs/screenshots/lab-06/11-log-failure-retries.png)
`run?simulateFailure=true`: у логу `attempt 1/3 ... retry immediately`, `attempt 2/3 ... retry immediately`, `attempt 3/3 ... retries exhausted`.

**09. Unit-тести**
![09](docs/screenshots/lab-06/15-unit-tests-green.png)
`mvn test`: усі тести зелені.


## 12. Обмеження

- Планувальник працює в одному екземплярі застосунку (`isClustered=false`). Для кластера потрібні `isClustered=true` та спільна БД.
- Demo-тригер `stale-repair-demo-2min` створений для демонстрації; у реальній експлуатації його варто видалити, залишивши `stale-repair-daily`.
