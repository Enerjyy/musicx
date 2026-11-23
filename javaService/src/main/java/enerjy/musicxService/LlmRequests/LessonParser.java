package enerjy.musicxService.LlmRequests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import enerjy.musicxService.LlmRequests.dto.LessonParseResult;
import enerjy.musicxService.LlmRequests.prompts.LessonPrompts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class LessonParser {

    private static final int MAX_TOKENS = 512;

    private final OpenRouterClient client;
    private final ObjectMapper mapper;
    private final String model;

    public LessonParser(
            OpenRouterClient client,
            ObjectMapper mapper,
            @Value("${openrouter.model}") String model
    ) {
        this.client = client;
        this.mapper = mapper;
        this.model = model;
    }

    public LessonParseResult parse(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new LlmRequestException("empty input");
        }

        var request = new ChatRequest(
                model,
                List.of(
                        new ChatRequest.Message("system", LessonPrompts.SYSTEM),
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
        if (!"stop".equals(choice.finishReason())) {
            throw new LlmRequestException("model stopped early: " + choice.finishReason());
        }

        var content = choice.message() == null ? null : choice.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmRequestException("model returned empty content");
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

    private static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new LlmRequestException("bad date from model: " + raw, e);
        }
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null) return null;
        // Пускаем и HH:mm, и HH:mm:ss
        try {
            return LocalTime.parse(raw.length() == 5 ? raw + ":00" : raw);
        } catch (DateTimeParseException e) {
            throw new LlmRequestException("bad time from model: " + raw, e);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        var text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}