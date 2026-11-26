package enerjy.musicxService.messaging.contract;

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

    /** Топик, в который нужно публиковать сообщение данного типа. */
    public static String forMessage(KafkaMessage message) {
        return switch (message) {
            case IncomingMessage ignored -> INBOUND_MESSAGES;
            case DeliveryResult ignored -> OUTBOUND_RESULTS;
            case OutboundCommand command -> outboundCommands(command.channel());
        };
    }

    /** Все рабочие топики контракта и их DLT для известных каналов. */
    public static List<String> contractTopics() {
        List<String> main = List.of(INBOUND_MESSAGES, OUTBOUND_RESULTS, OUTBOUND_COMMANDS_TELEGRAM);
        return main.stream().flatMap(topic -> Stream.of(topic, dlt(topic))).toList();
    }
}
