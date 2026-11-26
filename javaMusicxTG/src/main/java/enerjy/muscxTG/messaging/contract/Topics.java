package enerjy.muscxTG.messaging.contract;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class Topics {

    public static final String INBOUND_MESSAGES = "inbound.messages";
    public static final String OUTBOUND_RESULTS = "outbound.results";
    public static final String OUTBOUND_COMMANDS_TELEGRAM = "outbound.commands.telegram";
    public static final String DLT_SUFFIX = ".dlt";

    private static final String OUTBOUND_COMMANDS_PREFIX = "outbound.commands.";

    private Topics() {
    }

    public static String outboundCommands(String channel) {
        return OUTBOUND_COMMANDS_PREFIX + channel.toLowerCase(Locale.ROOT);
    }

    public static String dlt(String topic) {
        return topic + DLT_SUFFIX;
    }

    /** РўРѕРїРёРє, РІ РєРѕС‚РѕСЂС‹Р№ РЅСѓР¶РЅРѕ РїСѓР±Р»РёРєРѕРІР°С‚СЊ СЃРѕРѕР±С‰РµРЅРёРµ РґР°РЅРЅРѕРіРѕ С‚РёРїР°. */
    public static String forMessage(KafkaMessage message) {
        return switch (message) {
            case IncomingMessage ignored -> INBOUND_MESSAGES;
            case DeliveryResult ignored -> OUTBOUND_RESULTS;
            case OutboundCommand command -> outboundCommands(command.channel());
        };
    }

    /** Р’СЃРµ СЂР°Р±РѕС‡РёРµ С‚РѕРїРёРєРё РєРѕРЅС‚СЂР°РєС‚Р° Рё РёС… DLT РґР»СЏ РёР·РІРµСЃС‚РЅС‹С… РєР°РЅР°Р»РѕРІ. */
    public static List<String> contractTopics() {
        List<String> main = List.of(INBOUND_MESSAGES, OUTBOUND_RESULTS, OUTBOUND_COMMANDS_TELEGRAM);
        return main.stream().flatMap(topic -> Stream.of(topic, dlt(topic))).toList();
    }
}
