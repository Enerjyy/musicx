package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.security.AuthProvider;
import enerjy.musicxService.user.TimezoneStatus;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userService;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Команда /timezone: просмотр и ручная установка часового пояса.
 *
 * Без аргумента показывает сохранённую зону (и кто её задал), с аргументом —
 * валидирует через {@link ZoneId#of} и сохраняет со статусом MANUAL.
 * Невалидный ввод не меняет ничего, пользователь получает ответ с примером.
 */
@Component
public class TimezoneCommandHandler {

    public static final String COMMAND = "/timezone";

    /** Пример для подсказки при неверном вводе. */
    static final String EXAMPLE = "Europe/Berlin";

    private final ChannelRegistry channels;
    private final userService userService;

    public TimezoneCommandHandler(ChannelRegistry channels, userService userService) {
        this.channels = channels;
        this.userService = userService;
    }

    /** Сообщение начинается с команды /timezone (в том числе /timezone@botname). */
    public static boolean isTimezoneCommand(String text) {
        if (text == null) {
            return false;
        }
        var firstToken = text.trim().split("\\s+", 2)[0];
        return firstToken.equals(COMMAND) || firstToken.startsWith(COMMAND + "@");
    }

    public void handle(IncomingMessage message) {
        var channel = channels.by(message.channel());
        var conversationId = message.conversationId();

        userEntity user = userService.upsertByIdentity(
                providerOf(message.channel()), message.userRef(), null, null);

        var zoneArg = argument(message.text());
        if (zoneArg == null) {
            publish(channel, conversationId, currentZoneMessage(user));
            return;
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(zoneArg);
        } catch (DateTimeException e) {
            publish(channel, conversationId,
                    "Не понял часовой пояс «" + zoneArg + "». Пример: " + COMMAND + " " + EXAMPLE);
            return;
        }

        userService.setTimezone(user.getUserId(), zone, TimezoneStatus.MANUAL);
        publish(channel, conversationId, "Часовой пояс сохранён: " + zone.getId() + " (вручную).");
    }

    private static String currentZoneMessage(userEntity user) {
        if (user.getTimezone() == null) {
            return "Часовой пояс не задан. Пришлите " + COMMAND + " " + EXAMPLE;
        }
        var status = user.getTimezoneStatus() == TimezoneStatus.MANUAL ? "вручную" : "автоматически";
        return "Текущий часовой пояс: " + user.getTimezone() + " (" + status + ").";
    }

    /** Аргумент команды — всё после первого токена; пусто или отсутствует — null. */
    static String argument(String text) {
        var trimmed = text.trim();
        var spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0) {
            return null;
        }
        var argument = trimmed.substring(spaceIndex).trim();
        return argument.isEmpty() ? null : argument;
    }

    private static void publish(OutboundChannel channel, String conversationId, String text) {
        channel.publish(SendMessage.create(
                channel.id(), conversationId, java.util.UUID.randomUUID().toString(), text));
    }

    private static AuthProvider providerOf(String channel) {
        if (Channels.TELEGRAM.equals(channel)) {
            return AuthProvider.TELEGRAM;
        }
        throw new IllegalArgumentException("no identity provider for channel: " + channel);
    }
}
