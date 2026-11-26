package enerjy.muscxTG.messaging.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * РћР±С‰РёР№ РєРѕРЅРІРµСЂС‚ РІСЃРµС… СЃРѕРѕР±С‰РµРЅРёР№ РјРµР¶РґСѓ СЏРґСЂРѕРј Рё С‚СЂР°РЅСЃРїРѕСЂС‚РЅС‹РјРё СЃРµСЂРІРёСЃР°РјРё (РєРѕРЅС‚СЂР°РєС‚ v1).
 *
 * РџРѕР»СЏ РєРѕРЅРІРµСЂС‚Р° Р»РµР¶Р°С‚ РїР»РѕСЃРєРѕ СЂСЏРґРѕРј СЃ РїРѕР»РµР·РЅРѕР№ РЅР°РіСЂСѓР·РєРѕР№. РџРѕР»Рµ {@code type}
 * РїРёС€РµС‚ Jackson РїРѕ Р°РЅРЅРѕС‚Р°С†РёРё, РІ СЃР°РјРёС… Р·Р°РїРёСЃСЏС… РµРіРѕ РЅРµС‚.
 * РљРѕРїРёСЏ СЌС‚РёС… С„Р°Р№Р»РѕРІ Р»РµР¶РёС‚ РІ РѕР±РѕРёС… СЃРµСЂРІРёСЃР°С…: РёР·РјРµРЅРµРЅРёСЏ РІРЅРѕСЃРёС‚СЊ СЃРёРЅС…СЂРѕРЅРЅРѕ,
 * СЃРѕРІРїР°РґРµРЅРёРµ С„РёРєСЃС‚СѓСЂ РїСЂРѕРІРµСЂСЏСЋС‚ РєРѕРЅС‚СЂР°РєС‚РЅС‹Рµ С‚РµСЃС‚С‹.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IncomingMessage.class, name = IncomingMessage.TYPE),
        @JsonSubTypes.Type(value = ShowProgress.class, name = ShowProgress.TYPE),
        @JsonSubTypes.Type(value = SendMessage.class, name = SendMessage.TYPE),
        @JsonSubTypes.Type(value = DeleteMessage.class, name = DeleteMessage.TYPE),
        @JsonSubTypes.Type(value = DeliveryResult.class, name = DeliveryResult.TYPE)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface KafkaMessage permits IncomingMessage, OutboundCommand, DeliveryResult {

    int CURRENT_SCHEMA_VERSION = 1;

    /** РЈРЅРёРєР°Р»СЊРЅС‹Р№ id СЃРѕР±С‹С‚РёСЏ (UUID), РѕСЃРЅРѕРІР° РёРґРµРјРїРѕС‚РµРЅС‚РЅРѕСЃС‚Рё. */
    String eventId();

    int schemaVersion();

    /** РњРѕРјРµРЅС‚ СЃРѕР·РґР°РЅРёСЏ, ISO-8601 РІ UTC. */
    String occurredAt();

    /** РљР°РЅР°Р», РЅР°РїСЂРёРјРµСЂ {@link Channels#TELEGRAM}. */
    String channel();

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РґРёР°Р»РѕРіР° РІ РєР°РЅР°Р»Рµ (РІ Telegram СЌС‚Рѕ chat_id). */
    String conversationId();

    /** РљР»СЋС‡ РїР°СЂС‚РёС†РёРё Kafka: СЃРѕС…СЂР°РЅСЏРµС‚ РїРѕСЂСЏРґРѕРє СЃРѕРѕР±С‰РµРЅРёР№ РІРЅСѓС‚СЂРё РѕРґРЅРѕРіРѕ РґРёР°Р»РѕРіР°. */
    @JsonIgnore
    default String partitionKey() {
        return channel() + ":" + conversationId();
    }
}
