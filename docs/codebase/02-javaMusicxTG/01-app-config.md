# 01 · javaMusicxTG — приложение и конфигурация

Пакет `enerjy.muscxTG`. Файлы: `MuscxTgApplication.java`, `Kafka/CFG/*`, `application.yml`, `build.gradle.kts`.

## MuscxTgApplication

Точка входа Spring Boot Telegram-транспорта.

#### `main(String[] args)` → void
- **Назначение:** `SpringApplication.run(MuscxTgApplication.class, args)`.
- **Аннотации:** `@SpringBootApplication`, `@EnableKafka`.

#### `objectMapper()` → `ObjectMapper` (`@Bean`)
- **Назначение:** общий `ObjectMapper` (без JavaTimeModule — время в контракте строковое ISO).

#### `telegramBotsApi(MusicxBot bot)` → `TelegramBotsApi` (`@Bean`)
- **Назначение:** зарегистрировать бота (`registerBot`) для long-polling.
- **Условия:** `@Profile("!test")` (в тестах не ходит в api.telegram.org),
  `@ConditionalOnProperty("bot.register", matchIfMissing=true)`.
- **Ошибки:** `TelegramApiException` (deleteWebhook и т.п.).

## Kafka/CFG

### ContractKafkaConfig
Зеркало конфигурации ядра. `FACTORY = "contractListenerContainerFactory"`.

#### `contractListenerContainerFactory(...)` → `ConcurrentKafkaListenerContainerFactory<String,String>` (`@Bean`)
- **Параметры:** `bootstrapServers`, `groupId` (default `telegram-bot-group`), `maxRetries` (4),
  `initialIntervalMs` (1000), `multiplier` (2.0), `maxIntervalMs` (10000), `kafkaTemplate`.
- **Настройка:** String-сериализаторы, `ENABLE_AUTO_COMMIT=false`, `earliest`, `MAX_POLL_RECORDS=10`,
  `AckMode.MANUAL_IMMEDIATE`, `DefaultErrorHandler`.

#### `errorHandler(...)` → `DefaultErrorHandler` (static, package-private)
- **Назначение:** DLT + backoff; `JsonProcessingException`/`IllegalArgumentException` не ретраятся; `setCommitRecovered(true)`.

#### `deadLetterDestination(record, exception)` → `TopicPartition` (static, package-private)
- **Назначение:** `Topics.dlt(topic)`, партиция -1.

### KafkaConsumerConfig
#### `consumerFactory(bootstrapServers)` → `ConsumerFactory<String,String>` (`@Bean`)
- **Назначение:** `group-id=telegram-bot-group`, String-десериализаторы.
#### `kafkaListenerContainerFactory(consumerFactory)` → фабрика (`@Bean`)
- **Назначение:** фабрика по умолчанию.

### KafkaProducerConfig
#### `producerFactory(bootstrapServers)` → `ProducerFactory<String,String>` (`@Bean`)
- **Назначение:** идемпотентный продюсер (`ENABLE_IDEMPOTENCE=true`, `ACKS=all`), String-сериализаторы.
#### `kafkaTemplate(producerFactory)` → `KafkaTemplate<String,String>` (`@Bean`)

## application.yml (ключевое)
- `spring.threads.virtual.enabled: true`; `spring.kafka.bootstrap-servers: localhost:9092`,
  `group-id: telegram-bot-group`, `earliest`, String-десериализаторы.
- `bot.token` (env `BOT_TOKEN`), `bot.name` (env `BOT_NAME`).
- `app.telegram.connect-timeout-seconds` (5), `read-timeout-seconds` (12),
  `progress-ttl` (PT30S), `dedup-capacity` (10000).

## build.gradle.kts (ключевое)
- Плагины `java`, `org.springframework.boot` 4.1.2, `io.spring.dependency-management` 1.1.7; Java 21.
- Зависимости: spring-boot-starter, spring-kafka, `org.telegram:telegrambots-spring-boot-starter:6.9.7.1`,
  spring-boot-starter-web; тесты: starter-test, spring-kafka-test, junit-platform-launcher.
- `jar` отключён (Docker копирует boot-jar).