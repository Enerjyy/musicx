package enerjy.muscxTG.messaging.contract;

/**
 * РћС‚РїСЂР°РІРёС‚СЊ РїРѕР»СЊР·РѕРІР°С‚РµР»СЋ СЃРѕРѕР±С‰РµРЅРёРµ. РўСЂР°РЅСЃРїРѕСЂС‚ РѕС‚РІРµС‡Р°РµС‚ {@link DeliveryResult}
 * СЃ С‚РµРј Р¶Рµ commandId Рё id РѕС‚РїСЂР°РІР»РµРЅРЅРѕРіРѕ СЃРѕРѕР±С‰РµРЅРёСЏ. Р РµС€РµРЅРёРµ РѕР± СѓРґР°Р»РµРЅРёРё РїСЂРёРЅРёРјР°РµС‚ СЏРґСЂРѕ.
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
