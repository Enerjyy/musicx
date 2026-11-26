package enerjy.musicxService.messaging.contract;

import java.time.Instant;
import java.util.UUID;

/** Проверки и генерация полей конверта. Не часть контракта на проводе. */
final class Envelope {

    private Envelope() {
    }

    static String newEventId() {
        return UUID.randomUUID().toString();
    }

    static String now() {
        return Instant.now().toString();
    }

    static void check(String eventId, int schemaVersion, String occurredAt, String channel, String conversationId) {
        requireText(eventId, "eventId");
        requireText(occurredAt, "occurredAt");
        requireText(channel, "channel");
        requireText(conversationId, "conversationId");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
