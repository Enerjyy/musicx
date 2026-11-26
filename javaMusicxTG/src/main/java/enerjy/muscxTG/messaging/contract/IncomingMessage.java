package enerjy.muscxTG.messaging.contract;

/**
 * Р’С…РѕРґСЏС‰РµРµ СЃРѕРѕР±С‰РµРЅРёРµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ (С‚СЂР°РЅСЃРїРѕСЂС‚ в†’ СЏРґСЂРѕ, С‚РѕРїРёРє {@link Topics#INBOUND_MESSAGES}).
 *
 * @param messageId   id СЃРѕРѕР±С‰РµРЅРёСЏ РІ РєР°РЅР°Р»Рµ; РІРјРµСЃС‚Рµ СЃ channel Рё conversationId РґР°С‘С‚ РєР»СЋС‡ РґРµРґСѓРїР»РёРєР°С†РёРё
 * @param userRef     id РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ Сѓ РїСЂРѕРІР°Р№РґРµСЂР° (РІ Telegram СЌС‚Рѕ user id)
 * @param locale      СЏР·С‹Рє РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ, РјРѕР¶РµС‚ РѕС‚СЃСѓС‚СЃС‚РІРѕРІР°С‚СЊ
 * @param progressRef РЅРµРїСЂРѕР·СЂР°С‡РЅС‹Р№ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ С‡РµСЂРЅРѕРІРёРєР° СЃС‚Р°С‚СѓСЃР° (РІ Telegram СЌС‚Рѕ draft_id)
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
