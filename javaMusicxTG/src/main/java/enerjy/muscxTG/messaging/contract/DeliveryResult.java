package enerjy.muscxTG.messaging.contract;

/**
 * РС‚РѕРі РІС‹РїРѕР»РЅРµРЅРёСЏ РєРѕРјР°РЅРґС‹ {@link SendMessage} РёР»Рё {@link DeleteMessage}
 * (С‚СЂР°РЅСЃРїРѕСЂС‚ в†’ СЏРґСЂРѕ, С‚РѕРїРёРє {@link Topics#OUTBOUND_RESULTS}).
 *
 * @param commandId         id РєРѕРјР°РЅРґС‹, РЅР° РєРѕС‚РѕСЂСѓСЋ СЌС‚Рѕ РѕС‚РІРµС‚
 * @param externalMessageId id РѕС‚РїСЂР°РІР»РµРЅРЅРѕРіРѕ СЃРѕРѕР±С‰РµРЅРёСЏ (РґР»СЏ SENT РїРѕСЃР»Рµ SendMessage)
 * @param retryAfterSec     СЃРєРѕР»СЊРєРѕ Р¶РґР°С‚СЊ РґРѕ РїРѕРІС‚РѕСЂР° (РґР»СЏ RATE_LIMITED)
 * @param error             РєСЂР°С‚РєРѕРµ РѕРїРёСЃР°РЅРёРµ РѕС€РёР±РєРё РґР»СЏ Р»РѕРіРѕРІ
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
