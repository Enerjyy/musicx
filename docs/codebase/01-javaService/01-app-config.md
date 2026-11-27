# 01 · javaService — приложение и конфигурация

Пакет `enerjy.musicxService`. Файлы: `JavaService.java`, `Kafka/CFG/*`, `application.yml`, `build.gradle.kts`.

## JavaService

Точка входа Spring Boot.

#### `main(String[] args)` → void
- **Назначение:** запускает приложение через `SpringApplication.run(JavaService.class, args)`.
- **Параметры:** `args` — аргументы командной строки (не используются).
- **Возврат:** нет.
- **Побочные эффекты:** поднимает контекст Spring, включает Kafka и планировщик.
- **Ошибки:** пробрасывает `SpringApplication` наружу.
- **Аннотации класса:** `@SpringBootApplication`, `@EnableKafka` (Kafka-консьюмеры/продюсер),
  `@EnableScheduling` (нужно для `@Scheduled` в `ReminderProcessor`/`ScheduledDeletionProcessor`).

#### `objectMapper()` → `ObjectMapper` (`@Bean`)
- **Назначение:** общий `ObjectMapper` с поддержкой `java.time` (`JavaTimeModule`).
- **Возврат:** новый `ObjectMapper` с зарегистрированным `JavaTimeModule`.
- **Побочные эффекты:** нет; используется для (де)сериализации Kafka-контракта и LLM-ответов.
- **Примечание:** без модуля `Instant`/`LocalDateTime` сериализовались бы как объекты.

## Kafka/CFG

### ContractKafkaConfig
Фабрика консьюмеров контракта v1: ручной ack, ретраи с backoff, DLT.

#### `contractListenerContainerFactory(...)` → `ConcurrentKafkaListenerContainerFactory<String,String>` (`@Bean(name=FACTORY)`)
- **Назначение:** фабрика контейнеров для слушателей, принимающих `Acknowledgment`.
- **Параметры:** `bootstrapServers`, `groupId` (default `musicx-service`), `maxRetries` (4),
  `initialIntervalMs` (1000), `multiplier` (2.0), `maxIntervalMs` (10000), `kafkaTemplate`.
- **Настройка:** `StringDeserializer` для ключа/значения, `ENABLE_AUTO_COMMIT=false`,
  `AUTO_OFFSET_RESET=earliest`, `MAX_POLL_RECORDS=10` (обработка одного сообщения — десятки секунд из-за LLM),
  `AckMode.MANUAL_IMMEDIATE`, общий `DefaultErrorHandler`.
- **Возврат:** настроенная фабрика.
- **Побочные эффекты:** нет (только bean).
- **Имя бина:** `contractListenerContainerFactory` (константа `FACTORY`).

#### `errorHandler(kafkaTemplate, maxRetries, initialIntervalMs, multiplier, maxIntervalMs)` → `DefaultErrorHandler` (static, package-private)
- **Назначение:** политика ошибок: ретраи + DLT.
- **Параметры:** шаблон для DLT, параметры backoff.
- **Логика:** `DeadLetterPublishingRecoverer` + `ExponentialBackOffWithMaxRetries`;
  `addNotRetryableExceptions(JsonProcessingException, IllegalArgumentException)` — битый/невалидный контракт не ретраится;
  `setCommitRecovered(true)` — смещение коммитится после отправки в DLT.
- **Возврат:** `DefaultErrorHandler`.

#### `deadLetterDestination(record, exception)` → `TopicPartition` (static, package-private)
- **Назначение:** куда отправить сообщение, которое не удалось обработать.
- **Возврат:** `TopicPartition(Topics.dlt(record.topic()), -1)` — партиция -1 (Kafka выберет по ключу, сохраняя порядок диалога).

### KafkaConsumerConfig
Простая (устаревшая) фабрика консьюмеров — оставлена для старых слушателей.

#### `consumerFactory(bootstrapServers)` → `ConsumerFactory<String,String>` (`@Bean`)
- **Назначение:** `DefaultKafkaConsumerFactory` с `group-id=musicx-service`, `StringDeserializer`.
- **Возврат:** фабрика консьюмеров.

#### `kafkaListenerContainerFactory(consumerFactory)` → `ConcurrentKafkaListenerContainerFactory<String,String>` (`@Bean`)
- **Назначение:** фабрика контейнеров по умолчанию (без ручного ack/DLT).
- **Возврат:** фабрика с указанным `consumerFactory`.

### KafkaProducerConfig
Идемпотентный продюсер.

#### `producerFactory(bootstrapServers)` → `ProducerFactory<String,String>` (`@Bean`)
- **Назначение:** `DefaultKafkaProducerFactory` с `StringSerializer`, `ENABLE_IDEMPOTENCE=true`, `ACKS=all`.
- **Возврат:** фабрика продюсеров.

#### `kafkaTemplate(producerFactory)` → `KafkaTemplate<String,String>` (`@Bean`)
- **Назначение:** шаблон для отправки; используется `KafkaMessagePublisher`.
- **Возврат:** `KafkaTemplate`.

### KafkaTopicsConfig
Явное создание топиков контракта и их DLT (создаёт только ядро).

#### `kafkaAdmin(bootstrapServers, autoCreate)` → `KafkaAdmin` (`@Bean`)
- **Назначение:** админ-клиент; `setAutoCreate(autoCreate)` (default true), `setFatalIfBrokerNotAvailable(false)`.
- **Возврат:** `KafkaAdmin`.

#### `contractTopics(partitions, replicas)` → `KafkaAdmin.NewTopics` (`@Bean`)
- **Назначение:** объявляет все топики контракта и DLT.
- **Параметры:** `partitions` (default 3), `replicas` (default 1).
- **Возврат:** `NewTopics` из `contractTopicDefinitions(...)`.

#### `contractTopicDefinitions(partitions, replicas)` → `List<NewTopic>` (static, package-private)
- **Назначение:** строит `NewTopic` для каждого имени из `Topics.contractTopics()`.
- **Возврат:** список `NewTopic` с заданными partitions/replicas.

## application.yml (ключевое)

- `server.port: 8081` (в docker-compose переопределён на 8080, маппинг 8082).
- `app.time-zone` (default Europe/Moscow), `app.reminders.*` (offset PT3H, cron `0 0 * * * *`, stuck-after PT10M,
  max-attempts 3, batch-size 50), `app.deletions.*` (delay PT30S, retry-delay PT60S, stuck-after PT10M,
  poll-interval-ms 5000, batch-size 50), `app.calendar.*` (default-duration 60, max-horizon-days 730,
  max-duration-minutes 10080, past-tolerance-seconds 60).
- `spring.threads.virtual.enabled: true` (виртуальные потоки), `spring.kafka.*` (bootstrap localhost:9092,
  group-id musicx-service, auto-offset-reset earliest, String-сериализаторы).
- `spring.datasource.*` из env, `spring.jpa.hibernate.ddl-auto: validate`, `show-sql: true`.
- `spring.flyway` включён, `locations: classpath:db/migration`, placeholder `app-time-zone`.
- `spring.web.cors.allowed-origins` из `CORS_ALLOWED_ORIGINS`.
- `telegram.bot-token`, `telegram.max-age-seconds` (86400); `jwt.secret/ttl-seconds/issuer` (issuer musicx).
- `openrouter.base-url/api-key/model/fallback-models/attempt-timeout-seconds` (12).
- Профиль `test`: свои `telegram`/`jwt` значения, чтобы не требовать реальный `.env`.

## build.gradle.kts (ключевое)

- Плагины: `java`, `org.springframework.boot` 4.1.2, `io.spring.dependency-management` 1.1.7,
  `io.freefair.lombok` 8.10. Java 21 toolchain.
- Зависимости: spring-boot-starter, data-jpa, web, validation, flyway, jackson, spring-kafka,
  springdoc-openapi 2.4.0, jjwt 0.12.6 (api/impl/jackson), postgresql, flyway-database-postgresql;
  тесты: starter-test, webmvc-test, h2, spring-kafka-test, junit-platform-launcher.
- `tasks.named("test")` исключает тег `llm-eval`; отдельная задача `llmEval` запускает только
  `llm-eval`-тесты (нужен `OPENROUTER_API_KEY`).
- `jar` отключён (Docker копирует только boot-jar).