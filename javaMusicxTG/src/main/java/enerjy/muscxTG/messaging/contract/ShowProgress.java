package enerjy.muscxTG.messaging.contract;

/**
 * РџРѕРєР°Р·Р°С‚СЊ РїРѕР»СЊР·РѕРІР°С‚РµР»СЋ СЃС‚Р°С‚СѓСЃ РѕР±СЂР°Р±РѕС‚РєРё (С‡РµСЂРЅРѕРІРёРє). РћС‚РІРµС‚ {@link DeliveryResult} РЅРµ РѕС‚РїСЂР°РІР»СЏРµС‚СЃСЏ.
 *
 * @param draftRef С‚РѕС‚ Р¶Рµ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ, С‡С‚Рѕ РїСЂРёС€С‘Р» РІ {@link IncomingMessage#progressRef()}
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
