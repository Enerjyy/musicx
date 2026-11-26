package enerjy.musicxService.messaging.contract;

/**
 * Показать пользователю статус обработки (черновик). Ответ {@link DeliveryResult} не отправляется.
 *
 * @param draftRef тот же идентификатор, что пришёл в {@link IncomingMessage#progressRef()}
 */
public record ShowProgress(
        String eventId,
        int schemaVersion,
        String occurredAt,
        String channel,
        String conversationId,
        String draftRef,
        String text
) implements OutboundCommand {

    public static final String TYPE = "SHOW_PROGRESS";

    public ShowProgress {
        Envelope.check(eventId, schemaVersion, occurredAt, channel, conversationId);
        Envelope.requireText(draftRef, "draftRef");
        Envelope.requireText(text, "text");
    }

    public static ShowProgress create(String channel, String conversationId, String draftRef, String text) {
        return new ShowProgress(Envelope.newEventId(), CURRENT_SCHEMA_VERSION, Envelope.now(),
                channel, conversationId, draftRef, text);
    }
}
