package enerjy.musicxService.messaging.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Общий конверт всех сообщений между ядром и транспортными сервисами (контракт v1).
 *
 * Поля конверта лежат плоско рядом с полезной нагрузкой. Поле {@code type}
 * пишет Jackson по аннотации, в самих записях его нет.
 * Копия этих файлов лежит в обоих сервисах: изменения вносить синхронно,
 * совпадение фикстур проверяют контрактные тесты.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IncomingMessage.class, name = IncomingMessage.TYPE),
        @JsonSubTypes.Type(value = ShowProgress.class, name = ShowProgress.TYPE),
        @JsonSubTypes.Type(value = SendMessage.class, name = SendMessage.TYPE),
        @JsonSubTypes.Type(value = DeleteMessage.class, name = DeleteMessage.TYPE),
        @JsonSubTypes.Type(value = DeliveryResult.class, name = DeliveryResult.TYPE)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface KafkaMessage permits IncomingMessage, OutboundCommand, DeliveryResult {

    int CURRENT_SCHEMA_VERSION = 1;

    /** Уникальный id события (UUID), основа идемпотентности. */
    String eventId();

    int schemaVersion();

    /** Момент создания, ISO-8601 в UTC. */
    String occurredAt();

    /** Канал, например {@link Channels#TELEGRAM}. */
    String channel();

    /** Идентификатор диалога в канале (в Telegram это chat_id). */
    String conversationId();

    /** Ключ партиции Kafka: сохраняет порядок сообщений внутри одного диалога. */
    @JsonIgnore
    default String partitionKey() {
        return channel() + ":" + conversationId();
    }
}
