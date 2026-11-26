package enerjy.musicxService.Logic.schedule.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.LlmRequestException;
import enerjy.musicxService.LlmRequests.OpenRouterClient;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import enerjy.musicxService.LlmRequests.prompts.EventPrompts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Парсер событий через LLM (OpenRouter) по контракту v1 ({@link EventPrompts}).
 *
 * Модель перебирается по порядку: основная, затем запасные из
 * {@code openrouter.fallback-models}. Каждая попытка ограничена таймаутом
 * HTTP-клиента, суммарный бюджет всех попыток — {@link #TOTAL_BUDGET}: дольше
 * ждать нельзя, потому что черновик статуса в Telegram живёт около 30 секунд.
 *
 * Модель возвращает только локальные значения пользователя; {@code Instant}
 * из них считает {@link ParsedEvent#startAt(ZoneId)}.
 */
@Service
public class LlmEventParser implements EventParser {

    static final int MAX_TOKENS = 1024;
    static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(12);
    static final Duration TOTAL_BUDGET = Duration.ofSeconds(25);

    private static final DateTimeFormatter STRICT_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final OpenRouterClient client;
    private final ObjectMapper mapper;
    private final List<String> models;
    private final List<String> fallbacks;
    private final Clock clock;

    @Autowired
    public LlmEventParser(
            OpenRouterClient client,
            ObjectMapper mapper,
            @Value("${openrouter.model}") String model,
            @Value("${openrouter.fallback-models:}") List<String> fallbackModels
    ) {
        this(client, mapper, model, fallbackModels, Clock.systemUTC());
    }

    LlmEventParser(
            OpenRouterClient client,
            ObjectMapper mapper,
            String model,
            List<String> fallbackModels,
            Clock clock
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.models = normalizeModels(model, fallbackModels);
        this.fallbacks = models.stream().skip(1).toList();
    }

    private static List<String> normalizeModels(String model, List<String> fallbackModels) {
        var all = new ArrayList<String>();
        if (model != null && !model.isBlank()) {
            all.add(model.trim());
        }
        if (fallbackModels != null) {
            fallbackModels.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(candidate -> !candidate.isEmpty())
                    .forEach(all::add);
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("openrouter.model is required");
        }
        return List.copyOf(all);
    }

    @Override
    public ParsedEvent parse(String userText, ZoneId userZone) {
        if (userText == null || userText.isBlank()) {
            throw new LlmRequestException("empty input");
        }
        Objects.requireNonNull(userZone, "userZone");

        Instant deadline = Instant.now(clock).plus(TOTAL_BUDGET);
        LlmRequestException lastFailure = null;
        for (String model : models) {
            if (!attemptFits(deadline)) {
                break;
            }
            try {
                return toEvent(client.post(request(model, userText, userZone)), model);
            } catch (LlmRequestException e) {
                lastFailure = e;
            }
        }
        if (lastFailure == null) {
            throw new LlmRequestException("no time left for LLM attempts");
        }
        throw new LlmRequestException("all models failed, last: " + lastFailure.getMessage(), lastFailure);
    }

    /** Остатка бюджета должно хватать на целую попытку, иначе начинаю её заведомо безнадёжно. */
    private boolean attemptFits(Instant deadline) {
        return Duration.between(Instant.now(clock), deadline).compareTo(ATTEMPT_TIMEOUT) >= 0;
    }

    private ChatRequest request(String model, String userText, ZoneId userZone) {
        var userNow = Instant.now(clock).atZone(userZone);
        return new ChatRequest(
                model,
                List.of(
                        new ChatRequest.Message("system", EventPrompts.system(userNow)),
                        new ChatRequest.Message("user", userText)
                ),
                new ChatRequest.ResponseFormat(
                        "json_schema",
                        new ChatRequest.JsonSchema(EventPrompts.SCHEMA_NAME, true, schema())
                ),
                MAX_TOKENS,
                new ChatRequest.Provider(true),
                fallbacks,
                0.0,
                new ChatRequest.Reasoning(false)
        );
    }

    private ParsedEvent toEvent(ChatRequest.Response response, String model) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmRequestException("empty model response from " + model);
        }

        var choice = response.choices().get(0);
        var content = choice.message() == null ? null : choice.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmRequestException(
                    "model returned empty content, finish_reason=" + choice.finishReason());
        }

        return map(parseObject(content));
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

    private ParsedEvent map(JsonNode node) {
        return new ParsedEvent(
                node.path("isEvent").asBoolean(false),
                textOrNull(node, "title"),
                textOrNull(node, "description"),
                localDateTimeOrNull(node, "startLocal"),
                localDateTimeOrNull(node, "endLocal"),
                integerOrNull(node, "durationMinutes"),
                missing(node),
                node.path("ambiguous").asBoolean(false)
        );
    }

    private static LocalDateTime localDateTimeOrNull(JsonNode node, String field) {
        String raw = textOrNull(node, field);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, STRICT_LOCAL);
        } catch (DateTimeParseException ignored) {
        }
        try {
            // Страховка: ISO-формат допускает секунды и доли, которые модель иногда добавляет.
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new LlmRequestException("bad " + field + " from model: " + raw);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        var text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integerOrNull(JsonNode node, String field) {
        var value = node.path(field);
        return value.isIntegralNumber() ? value.intValue() : null;
    }

    private static Set<String> missing(JsonNode node) {
        var array = node.path("missing");
        if (!array.isArray()) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        for (var item : array) {
            String value = item.asText().trim();
            if (value.equals("date") || value.equals("time")) {
                result.add(value);
            }
        }
        return Set.copyOf(result);
    }

    private JsonNode schema() {
        try {
            return mapper.readTree(EventPrompts.SCHEMA_JSON);
        } catch (Exception e) {
            throw new LlmRequestException("bad schema constant", e);
        }
    }
}
