package enerjy.musicxService.LlmRequests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import enerjy.musicxService.LlmRequests.dto.LessonParseResult;
import enerjy.musicxService.LlmRequests.prompts.LessonPrompts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class LessonParser {

    private static final int MAX_TOKENS = 1024;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("ru")),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("ru")),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    );

    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
    );

    private final OpenRouterClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final Clock clock;

    @Autowired
    public LessonParser(
            OpenRouterClient client,
            ObjectMapper mapper,
            @Value("${openrouter.model}") String model,
            @Value("${app.time-zone:Europe/Moscow}") String timeZone
    ) {
        this(client, mapper, model, Clock.system(ZoneId.of(timeZone)));
    }

    LessonParser(OpenRouterClient client, ObjectMapper mapper, String model, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.model = model;
        this.clock = clock;
    }

    public LessonParseResult parse(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new LlmRequestException("empty input");
        }

        var request = new ChatRequest(
                model,
                List.of(
                        new ChatRequest.Message("system", LessonPrompts.system(LocalDate.now(clock))),
                        new ChatRequest.Message("user", userText)
                ),
                new ChatRequest.ResponseFormat(
                        "json_schema",
                        new ChatRequest.JsonSchema(
                                LessonPrompts.SCHEMA_NAME,
                                true,
                                schema()
                        )
                ),
                MAX_TOKENS,
                new ChatRequest.Provider(true)
        );

        var response = client.post(request);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmRequestException("empty model response");
        }

        var choice = response.choices().get(0);
        var content = choice.message() == null ? null : choice.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmRequestException(
                    "model returned empty content, finish_reason=" + choice.finishReason());
        }

        return toResult(parseObject(content));
    }

    private JsonNode schema() {
        try {
            return mapper.readTree(LessonPrompts.SCHEMA_JSON);
        } catch (Exception e) {
            throw new LlmRequestException("bad schema constant", e);
        }
    }

    private JsonNode parseObject(String content) {
        try {
            var node = mapper.readTree(content);
            if (node == null || !node.isObject()) {
                throw new LlmRequestException("model returned non-object json: " + content);
            }
            return node;
        } catch (LlmRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmRequestException("cannot parse model json: " + content, e);
        }
    }

    private static LessonParseResult toResult(JsonNode node) {
        return new LessonParseResult(
                parseDate(text(node, "date")),
                parseTime(text(node, "time")),
                text(node, "title"),
                text(node, "note")
        );
    }

    static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String value = normalizeDate(raw);
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new LlmRequestException("bad date from model: " + raw);
    }

    static LocalTime parseTime(String raw) {
        if (raw == null) return null;
        for (String candidate : normalizeTime(raw)) {
            for (DateTimeFormatter formatter : TIME_FORMATS) {
                try {
                    return LocalTime.parse(candidate, formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        throw new LlmRequestException("bad time from model: " + raw);
    }

    private static String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static String normalizeDate(String raw) {
        String value = unquote(raw);
        value = value.replaceAll("(?i),?\\s+\\d{1,2}:\\d{2}(:\\d{2})?.*$", "");
        value = value.replaceAll("(?i)\\s*(года|г\\.|г|year)\\s*\\.?$", "");
        int separator = value.indexOf('T');
        if (separator > 0) {
            value = value.substring(0, separator);
        }
        return value.trim();
    }

    private static List<String> normalizeTime(String raw) {
        String value = unquote(raw);
        value = value.replaceAll("(?i)\\s*(мск|msk|utc(\\s*[+-]\\d{1,2}(:\\d{2})?)?)\\s*$", "");
        return List.of(value, value.replaceAll("\\s.*$", ""), value.replace('.', ':'));
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        var text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
