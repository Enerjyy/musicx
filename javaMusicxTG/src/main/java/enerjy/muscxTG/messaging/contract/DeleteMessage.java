package enerjy.muscxTG.messaging.contract;

/**
 * РЈРґР°Р»РёС‚СЊ СЂР°РЅРµРµ РѕС‚РїСЂР°РІР»РµРЅРЅРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ. РўСЂР°РЅСЃРїРѕСЂС‚ РѕС‚РІРµС‡Р°РµС‚ {@link DeliveryResult}.
 *
 * @param externalMessageId id СЃРѕРѕР±С‰РµРЅРёСЏ РІ РєР°РЅР°Р»Рµ РёР· {@link DeliveryResult#externalMessageId()}
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
