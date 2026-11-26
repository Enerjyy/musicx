package enerjy.musicxService.messaging.contract;

/**
 * Итог выполнения команды {@link SendMessage} или {@link DeleteMessage}
 * (транспорт → ядро, топик {@link Topics#OUTBOUND_RESULTS}).
 *
 * @param commandId         id команды, на которую это ответ
 * @param externalMessageId id отправленного сообщения (для SENT после SendMessage)
 * @param retryAfterSec     сколько ждать до повтора (для RATE_LIMITED)
 * @param error             краткое описание ошибки для логов
 */
public record DeliveryResult(
        String eventId,
        int schemaVersion,
        String occurredAt,
        String channel,
        String conversationId,
        String commandId,
        DeliveryStatus status,
        String externalMessageId,
        Integer retryAfterSec,
        String error
) implements KafkaMessage {

    public static final String TYPE = "DELIVERY_RESULT";

    public DeliveryResult {
        Envelope.check(eventId, schemaVersion, occurredAt, channel, conversationId);
        Envelope.requireText(commandId, "commandId");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }

    public static DeliveryResult create(String channel, String conversationId, String commandId,
                                        DeliveryStatus status, String externalMessageId,
                                        Integer retryAfterSec, String error) {
        return new DeliveryResult(Envelope.newEventId(), CURRENT_SCHEMA_VERSION, Envelope.now(),
                channel, conversationId, commandId, status, externalMessageId, retryAfterSec, error);
    }
}
