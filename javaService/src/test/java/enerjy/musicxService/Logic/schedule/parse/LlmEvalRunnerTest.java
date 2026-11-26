package enerjy.musicxService.Logic.schedule.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.OpenRouterClient;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import enerjy.musicxService.LlmRequests.prompts.EventPrompts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Шаг 10: ручной прогон датасета русских фраз через LLM-модели (тег {@code llm-eval}).
 *
 * Запускается только задачей {@code ./gradlew llmEval} при заданном
 * {@code OPENROUTER_API_KEY}. Обычный {@code test} этот класс не выполняет:
 * тег исключён в {@code build.gradle.kts}, а без ключа тест ещё и пропускается
 * через {@link EnabledIfEnvironmentVariable}. Внешние вызовы из обычной сборки
 * поэтому не идут.
 *
 * Результат — отчёт {@code build/reports/llm-eval.md} со сравнением моделей
 * по порогам: 100% валидного JSON, >= 95% верных start, p95 < 8 с.
 */
@Tag("llm-eval")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class LlmEvalRunnerTest {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private static final String DATASET = "/llm-eval/phrases-ru.jsonl";
    private static final int MAX_TOKENS = 1024;

    /** Оценочные цены USD за 1M токенов (input/output); используется, если API не отдаёт usage.cost. */
    private static final List<ModelPrice> PRICES = List.of(
            new ModelPrice("deepseek/deepseek-v4.1-flash", 0.30, 1.20),
            new ModelPrice("qwen/qwen3.8-flash", 0.20, 0.60),
            new ModelPrice("xiaomi/mimo-v2.6-flash", 0.15, 0.60)
    );

    private record ModelPrice(String model, double inputUsdPerM, double outputUsdPerM) {
    }

    private static final class Row {
        String id;
        String text;
        ZonedDateTime now;
        String mode; // "exact" | "date"
        JsonNode expected;
        String raw;
    }

    private static final class Attempt {
        boolean validJson;
        LocalDateTime startLocal;
        LocalDateTime endLocal;
        Integer durationMinutes;
        boolean isEvent;
        Set<String> missing = Set.of();
        boolean ambiguous;
        long latencyMs;
        Integer promptTokens;
        Integer completionTokens;
        Double costUsd;
        String error;
    }

    private static final class Score {
        int total;
        int validJson;
        int isEventOk;
        int startOk;
        int startCases; // строки, где start ожидается непустым (или mode=date)
        int endOk;
        int endCases;
        int durationOk;
        int durationCases;
        int missingOk;
        int ambiguousOk;
        final List<Long> latencies = new ArrayList<>();
        double costUsd;
        boolean usedApiCost;
        final List<String> failures = new ArrayList<>();
    }

    @Test
    void runEval() throws Exception {
        var key = System.getenv("OPENROUTER_API_KEY");
        assertFalse(key == null || key.isBlank(), "OPENROUTER_API_KEY is required for llm-eval");

        var mapper = new ObjectMapper();
        var rows = loadDataset(mapper);
        assertFalse(rows.isEmpty(), "dataset " + DATASET + " must not be empty");

        var schema = mapper.readTree(EventPrompts.SCHEMA_JSON);
        var client = new OpenRouterClient(BASE_URL, key, 30);

        var report = new StringBuilder();
        report.append("# LLM eval: разбор русских фраз (Шаг 10)\n\n");
        report.append("Датасет: `src/test/resources/llm-eval/phrases-ru.jsonl`, строк: ").append(rows.size()).append('\n');
        report.append("Фиксированное «сейчас»: 2026-11-27T12:00, зона Europe/Moscow.\n\n");

        var scores = new java.util.LinkedHashMap<String, Score>();
        for (var model : PRICES.stream().map(ModelPrice::model).toList()) {
            var score = runModel(client, mapper, schema, model, rows);
            scores.put(model, score);
        }

        appendSummary(report, scores);
        appendFailures(report, scores);
        appendRecommendation(report, scores);

        var out = Path.of("build", "reports", "llm-eval.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println("llm-eval report written to " + out.toAbsolutePath());
    }

    private static List<Row> loadDataset(ObjectMapper mapper) throws IOException {
        try (InputStream in = LlmEvalRunnerTest.class.getResourceAsStream(DATASET)) {
            if (in == null) {
                throw new IOException("dataset not found on classpath: " + DATASET);
            }
            var rows = new ArrayList<Row>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                var node = mapper.readTree(line);
                var row = new Row();
                row.id = node.path("id").asText();
                row.text = node.path("text").asText();
                row.now = LocalDateTime.parse(node.path("now").asText())
                        .atZone(ZoneId.of(node.path("zone").asText()));
                row.mode = node.path("mode").asText("exact");
                row.expected = node.path("expected");
                row.raw = line;
                rows.add(row);
            }
            return rows;
        }
    }

    private Score runModel(
            OpenRouterClient client, ObjectMapper mapper, JsonNode schema, String model, List<Row> rows
    ) {
        var score = new Score();
        for (var row : rows) {
            var attempt = callOnce(client, mapper, schema, model, row);
            score.total++;
            if (attempt.validJson) {
                score.validJson++;
            }
            score.latencies.add(attempt.latencyMs);
            if (attempt.promptTokens != null) {
                score.costUsd += tokenCost(model, attempt.promptTokens, attempt.completionTokens);
            }
            if (attempt.costUsd != null) {
                score.costUsd = attempt.costUsd; // фактическая цена от API точнее оценки
                score.usedApiCost = true;
            }

            var exp = row.expected;
            boolean eventOk = attempt.isEvent == exp.path("isEvent").asBoolean(false);
            if (eventOk) {
                score.isEventOk++;
            } else {
                score.failures.add(row.id + " [" + model + "]: isEvent " + attempt.isEvent
                        + " != " + exp.path("isEvent").asBoolean(false) + " («" + row.text + "»)");
            }
            if (attempt.missing.equals(readMissing(exp))) {
                score.missingOk++;
            }
            if (attempt.ambiguous == exp.path("ambiguous").asBoolean(false)) {
                score.ambiguousOk++;
            }

            var expStart = textOrNull(exp, "startLocal");
            boolean startOk;
            if (expStart == null) {
                startOk = attempt.startLocal == null;
            } else if ("date".equals(row.mode)) {
                startOk = attempt.startLocal != null
                        && attempt.startLocal.toLocalDate().equals(LocalDateTime.parse(expStart).toLocalDate());
            } else {
                startOk = expStart.equals(localText(attempt.startLocal));
            }
            if (expStart != null) {
                score.startCases++;
                if (startOk) {
                    score.startOk++;
                } else {
                    score.failures.add(row.id + " [" + model + "]: start=" + localText(attempt.startLocal)
                            + ", ожидалось " + expStart);
                }
            }

            var expEnd = textOrNull(exp, "endLocal");
            if (expEnd != null) {
                score.endCases++;
                if (expEnd.equals(localText(attempt.endLocal))) {
                    score.endOk++;
                } else {
                    score.failures.add(row.id + " [" + model + "]: end=" + localText(attempt.endLocal)
                            + ", ожидалось " + expEnd);
                }
            }

            var expDur = exp.path("durationMinutes").isInt() ? exp.path("durationMinutes").intValue() : null;
            if (expDur != null) {
                score.durationCases++;
                if (expDur.equals(attempt.durationMinutes)) {
                    score.durationOk++;
                } else {
                    score.failures.add(row.id + " [" + model + "]: duration=" + attempt.durationMinutes
                            + ", ожидалось " + expDur);
                }
            }
        }
        return score;
    }

    private Attempt callOnce(
            OpenRouterClient client, ObjectMapper mapper, JsonNode schema, String model, Row row
    ) {
        var attempt = new Attempt();
        var request = new ChatRequest(
                model,
                List.of(
                        new ChatRequest.Message("system", EventPrompts.system(row.now)),
                        new ChatRequest.Message("user", row.text)
                ),
                new ChatRequest.ResponseFormat(
                        "json_schema",
                        new ChatRequest.JsonSchema(EventPrompts.SCHEMA_NAME, true, schema)
                ),
                MAX_TOKENS,
                new ChatRequest.Provider(true),
                null,
                0.0,
                new ChatRequest.Reasoning(false)
        );
        long started = System.nanoTime();
        try {
            var response = client.post(request);
            attempt.latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (response.usage() != null) {
                attempt.promptTokens = response.usage().promptTokens();
                attempt.completionTokens = response.usage().completionTokens();
                attempt.costUsd = response.usage().cost();
            }
            var content = response.choices() == null || response.choices().isEmpty()
                    ? null : response.choices().get(0).message().content();
            var node = mapper.readTree(content == null ? "" : content);
            if (node == null || !node.isObject() || !node.path("isEvent").isBoolean()) {
                attempt.error = "invalid json";
                return attempt;
            }
            attempt.validJson = true;
            attempt.isEvent = node.path("isEvent").asBoolean(false);
            attempt.startLocal = localOrNull(node, "startLocal");
            attempt.endLocal = localOrNull(node, "endLocal");
            attempt.durationMinutes = node.path("durationMinutes").isInt()
                    ? node.path("durationMinutes").intValue() : null;
            var missing = new TreeSet<String>();
            if (node.path("missing").isArray()) {
                for (var item : node.path("missing")) {
                    String value = item.asText("").trim();
                    if (value.equals("date") || value.equals("time")) {
                        missing.add(value);
                    }
                }
            }
            attempt.missing = missing;
            attempt.ambiguous = node.path("ambiguous").asBoolean(false);
        } catch (Exception e) {
            attempt.latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            attempt.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return attempt;
    }

    private double tokenCost(String model, Integer promptTokens, Integer completionTokens) {
        for (var price : PRICES) {
            if (price.model().equals(model)) {
                double input = (promptTokens == null ? 0 : promptTokens) * price.inputUsdPerM() / 1_000_000;
                double output = (completionTokens == null ? 0 : completionTokens) * price.outputUsdPerM() / 1_000_000;
                return input + output;
            }
        }
        return 0;
    }

    private static LocalDateTime localOrNull(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            return null; // невалидное время = неверный start, но JSON ещё валиден
        }
    }

    private static String localText(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private static String textOrNull(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Set<String> readMissing(JsonNode expected) {
        var result = new TreeSet<String>();
        if (expected.path("missing").isArray()) {
            for (var item : expected.path("missing")) {
                result.add(item.asText().trim());
            }
        }
        return result;
    }

    private static void appendSummary(StringBuilder report, java.util.Map<String, Score> scores) {
        report.append("## Сводка по моделям\n\n");
        report.append("| Модель | Валидный JSON | isEvent | start | end | duration | missing | ambiguous | p50, мс | p95, мс | Стоимость, USD |\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var entry : scores.entrySet()) {
            var s = entry.getValue();
            report.append('|').append(entry.getKey())
                    .append('|').append(pct(s.validJson, s.total))
                    .append('|').append(pct(s.isEventOk, s.total))
                    .append('|').append(pct(s.startOk, s.startCases))
                    .append('|').append(pct(s.endOk, s.endCases))
                    .append('|').append(pct(s.durationOk, s.durationCases))
                    .append('|').append(pct(s.missingOk, s.total))
                    .append('|').append(pct(s.ambiguousOk, s.total))
                    .append('|').append(percentile(s.latencies, 50))
                    .append('|').append(percentile(s.latencies, 95))
                    .append('|').append(String.format(Locale.ROOT, "%.4f%s", s.costUsd, s.usedApiCost ? " (факт)" : " (оценка)"))
                    .append("|\n");
        }
        report.append("\nПороги: 100% валидного JSON, >= 95% верных `start`, p95 < 8 с.\n\n");
        report.append("Обозначения: start — точное совпадение `startLocal` (или дата, если mode=date); ")
                .append("end/duration — только по строкам, где они ожидаются.\n\n");
    }

    private static String pct(int ok, int total) {
        return total == 0 ? "—" : String.format(Locale.ROOT, "%.1f%%", 100.0 * ok / total);
    }

    private static long percentile(List<Long> values, int p) {
        if (values.isEmpty()) {
            return 0;
        }
        var sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static void appendFailures(StringBuilder report, java.util.Map<String, Score> scores) {
        report.append("## Расхождения\n\n");
        for (var entry : scores.entrySet()) {
            var failures = entry.getValue().failures;
            report.append("### ").append(entry.getKey()).append(" (расхождений: ").append(failures.size()).append(")\n\n");
            if (failures.isEmpty()) {
                report.append("Нет.\n\n");
            } else {
                for (var failure : failures) {
                    report.append("- ").append(failure).append('\n');
                }
                report.append('\n');
            }
        }
    }

    private static void appendRecommendation(StringBuilder report, java.util.Map<String, Score> scores) {
        var passing = new ArrayList<String>();
        var partial = new ArrayList<String>();
        for (var entry : scores.entrySet()) {
            var s = entry.getValue();
            boolean valid = s.total > 0 && s.validJson == s.total;
            boolean start = s.startCases == 0 || 100.0 * s.startOk / s.startCases >= 95.0;
            boolean p95 = percentile(s.latencies, 95) < 8000;
            if (valid && start && p95) {
                passing.add(entry.getKey());
            } else if (valid) {
                partial.add(entry.getKey());
            }
        }
        report.append("## Рекомендация\n\n");
        if (!passing.isEmpty()) {
            report.append("Пороги выполняют: ").append(String.join(", ", passing)).append(".\n");
            report.append("Основная модель: `").append(passing.get(0)).append("`.");
            if (passing.size() > 1) {
                report.append(" Запасная: `").append(passing.get(1)).append("`.");
            }
        } else if (!partial.isEmpty()) {
            report.append("Ни одна модель не выполняет все пороги. Ближайшие кандидаты (валидный JSON, но просадка точности/задержки): ")
                    .append(String.join(", ", partial)).append(".\n");
            report.append("Запасную модель выбирать из этого списка после повторного прогона.\n");
        } else {
            report.append("Ни одна модель не вернула 100% валидного JSON — для продакшена модели не готовы, нужен повторный прогон.\n");
        }
        report.append('\n');
    }
}
