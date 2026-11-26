package enerjy.musicxService.messaging.contract;

/**
 * Отправить пользователю сообщение. Транспорт отвечает {@link DeliveryResult}
 * с тем же commandId и id отправленного сообщения. Решение об удалении принимает ядро.
 */
public record SendMessage(
        String eventId,
        int schemaVersion,
        String occurredAt,
        String channel,
        String conversationId,
        String commandId,
        String text
) implements OutboundCommand {

    public static final String TYPE = "SEND_MESSAGE";

    public SendMessage {
        Envelope.check(eventId, schemaVersion, occurredAt, channel, conversationId);
        Envelope.requireText(commandId, "commandId");
        Envelope.requireText(text, "text");
    }

    public static SendMessage create(String channel, String conversationId, String commandId, String text) {
        return new SendMessage(Envelope.newEventId(), CURRENT_SCHEMA_VERSION, Envelope.now(),
                channel, conversationId, commandId, text);
    }
}
