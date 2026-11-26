package enerjy.musicxService.messaging.contract;

/**
 * Входящее сообщение пользователя (транспорт → ядро, топик {@link Topics#INBOUND_MESSAGES}).
 *
 * @param messageId   id сообщения в канале; вместе с channel и conversationId даёт ключ дедупликации
 * @param userRef     id пользователя у провайдера (в Telegram это user id)
 * @param locale      язык пользователя, может отсутствовать
 * @param progressRef непрозрачный идентификатор черновика статуса (в Telegram это draft_id)
 */
public record IncomingMessage(
        String eventId,
        int schemaVersion,
        String occurredAt,
        String channel,
        String conversationId,
        String messageId,
        String userRef,
        String text,
        String locale,
        String progressRef
) implements KafkaMessage {

    public static final String TYPE = "INCOMING_MESSAGE";

    public IncomingMessage {
        Envelope.check(eventId, schemaVersion, occurredAt, channel, conversationId);
        Envelope.requireText(messageId, "messageId");
        Envelope.requireText(userRef, "userRef");
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
    }

    public static IncomingMessage create(String channel, String conversationId, String messageId,
                                         String userRef, String text, String locale, String progressRef) {
        return new IncomingMessage(Envelope.newEventId(), CURRENT_SCHEMA_VERSION, Envelope.now(),
                channel, conversationId, messageId, userRef, text, locale, progressRef);
    }
}
