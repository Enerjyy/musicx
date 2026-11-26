package enerjy.musicxService.messaging.contract;

/**
 * Удалить ранее отправленное сообщение. Транспорт отвечает {@link DeliveryResult}.
 *
 * @param externalMessageId id сообщения в канале из {@link DeliveryResult#externalMessageId()}
 */
public record DeleteMessage(
        String eventId,
        int schemaVersion,
        String occurredAt,
        String channel,
        String conversationId,
        String commandId,
        String externalMessageId
) implements OutboundCommand {

    public static final String TYPE = "DELETE_MESSAGE";

    public DeleteMessage {
        Envelope.check(eventId, schemaVersion, occurredAt, channel, conversationId);
        Envelope.requireText(commandId, "commandId");
        Envelope.requireText(externalMessageId, "externalMessageId");
    }

    public static DeleteMessage create(String channel, String conversationId, String commandId,
                                       String externalMessageId) {
        return new DeleteMessage(Envelope.newEventId(), CURRENT_SCHEMA_VERSION, Envelope.now(),
                channel, conversationId, commandId, externalMessageId);
    }
}
