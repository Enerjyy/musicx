package enerjy.musicxService.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты проверки Telegram initData.
 *
 * Подписи здесь считаются тем же алгоритмом, что и в TGDataValidation — это
 * проверяет согласованность шагов (порядок аргументов HMAC, раскодированные
 * значения, сортировка), но не независимость реализации. Поэтому дополнительно
 * есть тест с фиксированной подписью из документации Telegram: он ловит
 * ошибки, которые по определению нельзя воспроизвести своим же кодом.
 */
class TGDataValidationTest {

    private static final String BOT_TOKEN = "5768337691:AAH5YkoiEuPk8-FZa32hStHTqXiLPtAEhx8";
    private static final long MAX_AGE = 86_400;

    private final TGDataValidation validator =
            new TGDataValidation(BOT_TOKEN, new ObjectMapper());

    // ── Источник подписи для тестов ────────────────────────────────────

    private static String sign(String botToken, Map<String, String> params) {
        String dataCheckString = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secret = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String buildInitData(String botToken, Map<String, String> params) {
        String hash = sign(botToken, params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
              .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        if (sb.length() > 0) sb.append('&');
        return sb.append("hash=").append(hash).toString();
    }

    private static Map<String, String> freshParams() {
        Map<String, String> params = new TreeMap<>();
        params.put("query_id", "AAHdF6IQAAAAAN0XohDhrOrc");
        params.put("user", "{\"id\":279058397,\"first_name\":\"Vladislav\","
                + "\"last_name\":\"Kibenko\",\"username\":\"vdkfrost\","
                + "\"language_code\":\"ru\",\"is_premium\":true}");
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond()));
        return params;
    }

    // ── Позитив ────────────────────────────────────────────────────────

    @Test
    void validInitDataReturnsUser() {
        String initData = buildInitData(BOT_TOKEN, freshParams());

        TelegramUser user = validator.validate(initData, MAX_AGE);

        assertThat(user.id()).isEqualTo(279058397L);
        assertThat(user.firstName()).isEqualTo("Vladislav");
        assertThat(user.lastName()).isEqualTo("Kibenko");
        assertThat(user.username()).isEqualTo("vdkfrost");
        assertThat(user.languageCode()).isEqualTo("ru");
        assertThat(user.isPremium()).isTrue();
    }

    /**
     * Подпись из документации Telegram (core.telegram.org/bots/webapps).
     * initData взят оттуда дословно — если наш алгоритм разойдётся с
     * эталонным хоть в одном символе, тест упадёт.
     */
    @Test
    void documentedVectorFromTelegramDocs() {
        String initData = "query_id=AAHdF6IQAAAAAN0XohDhrOrc"
                + "&user=%7B%22id%22%3A279058397%2C%22first_name%22%3A%22Vladislav%22"
                + "%2C%22last_name%22%3A%22Kibenko%22%2C%22username%22%3A%22vdkfrost%22"
                + "%2C%22language_code%22%3A%22ru%22%2C%22is_premium%22%3Atrue%7D"
                + "&auth_date=1662771648"
                + "&hash=c501b71e775f74ce10e377dea85a7ea24ecd640b223ea86dfe453e0eaed2e2b2";

        // auth_date из документации старый, поэтому окно огромное: проверяем
        // только подпись, а не свежесть.
        TelegramUser user = validator.validate(initData, Long.MAX_VALUE);

        assertThat(user.id()).isEqualTo(279058397L);
        assertThat(user.firstName()).isEqualTo("Vladislav");
    }

    @Test
    void expiredInitDataIsRejected() {
        Map<String, String> params = freshParams();
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond() - 90_000));

        String initData = buildInitData(BOT_TOKEN, params);

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void futureAuthDateIsRejected() {
        Map<String, String> params = freshParams();
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond() + 3600));

        String initData = buildInitData(BOT_TOKEN, params);

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("future");
    }

    // ── Негатив: подпись ───────────────────────────────────────────────

    @Test
    void tamperedPayloadIsRejected() {
        Map<String, String> params = freshParams();
        String initData = buildInitData(BOT_TOKEN, params);

        // Меняем user после подписи — подпись перестаёт сходиться.
        String tampered = initData.replace("Vladislav", "Attacker");

        assertThatThrownBy(() -> validator.validate(tampered, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("bad signature");
    }

    @Test
    void wrongBotTokenIsRejected() {
        Map<String, String> params = freshParams();
        String initData = buildInitData(BOT_TOKEN, params);

        // Подпись сделана другим токеном — проверяющий токен не тот.
        TGDataValidation other =
                new TGDataValidation("9999999999:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new ObjectMapper());

        assertThatThrownBy(() -> other.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("bad signature");
    }

    @Test
    void missingHashIsRejected() {
        Map<String, String> params = freshParams();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
              .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }

        assertThatThrownBy(() -> validator.validate(sb.toString(), MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("missing hash");
    }

    @Test
    void garbageHashIsRejected() {
        Map<String, String> params = freshParams();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
              .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        String initData = sb + "&hash=deadbeef";

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("bad signature");
    }

    // ── Негатив: структура ─────────────────────────────────────────────

    @Test
    void nullInitDataIsRejected() {
        assertThatThrownBy(() -> validator.validate(null, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void blankInitDataIsRejected() {
        assertThatThrownBy(() -> validator.validate("   ", MAX_AGE))
                .isInstanceOf(InitDataException.class);
    }

    @Test
    void duplicateParameterIsRejected() {
        Map<String, String> params = freshParams();
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond()));
        String hash = sign(BOT_TOKEN, params);

        String initData = "auth_date=" + params.get("auth_date")
                + "&auth_date=" + params.get("auth_date")
                + "&query_id=" + params.get("query_id")
                + "&user=" + java.net.URLEncoder.encode(params.get("user"), StandardCharsets.UTF_8)
                + "&hash=" + hash;

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void missingUserIsRejected() {
        Map<String, String> params = new TreeMap<>();
        params.put("query_id", "AAHdF6IQ");
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond()));

        String initData = buildInitData(BOT_TOKEN, params);

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("missing user");
    }

    @Test
    void userWithoutIdIsRejected() {
        Map<String, String> params = freshParams();
        params.put("user", "{\"first_name\":\"NoId\"}");

        String initData = buildInitData(BOT_TOKEN, params);

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class);
    }

    @Test
    void missingAuthDateIsRejected() {
        Map<String, String> params = new TreeMap<>();
        params.put("query_id", "AAHdF6IQ");
        params.put("user", "{\"id\":1,\"first_name\":\"A\"}");

        String initData = buildInitData(BOT_TOKEN, params);

        assertThatThrownBy(() -> validator.validate(initData, MAX_AGE))
                .isInstanceOf(InitDataException.class)
                .hasMessageContaining("auth_date");
    }
}