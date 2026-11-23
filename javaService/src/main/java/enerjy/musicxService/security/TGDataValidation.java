package enerjy.musicxService.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Проверка подписи Telegram Mini App `initData`.
 *
 * Алгоритм (docs: Validating data received via the Mini App):
 *  1. Распарсить строку как query-параметры, вытащить `hash`, остальные
 *     сложить в отсортированную по ключу карту.
 *  2. Склеить пары как `key=value` через `\n` — это data-check-string.
 *     Значения берутся РАСКОДИРОВАННЫМИ, не percent-encoded.
 *  3. secret_key = HMAC-SHA256(key = "WebAppData", message = botToken).
 *  4. Ожидаемый хэш = hex(HMAC-SHA256(key = secret_key, message = dataCheckString)).
 *  5. Сравнить с полученным `hash` в constant-time.
 *
 * Дополнительно проверяется `auth_date`: валидная подпись доказывает, что
 * данные от Telegram, но не что они свежие. Без этой проверки перехваченный
 * initData работал бы вечно.
 *
 * Любая проблема — InitDataException. Никогда не возвращаем null и не
 * «пропускаем» проверку: вызывающий код обязан считать исключение отказом.
 */
@Component
public class TGDataValidation {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";

    private final String botToken;
    private final ObjectMapper objectMapper;

    public TGDataValidation(
            @Value("${telegram.bot-token}") String botToken,
            ObjectMapper objectMapper
    ) {
        this.botToken = botToken;
        this.objectMapper = objectMapper;
    }

    /**
     * @param initData       строка initData из Telegram.WebApp.initData
     * @param maxAgeSeconds  сколько секунд данные считаются свежими
     * @return пользователь из подписанных данных
     * @throws InitDataException если подпись неверна, данные просрочены или битые
     */
    public TelegramUser validate(String initData, long maxAgeSeconds) {
        if (initData == null || initData.isBlank()) {
            throw new InitDataException("initData is empty");
        }
        if (botToken == null || botToken.isBlank()) {
            // Без токена бота проверить подпись невозможно — это ошибка конфигурации,
            // а не проблема клиента. Падаем рано и явно.
            throw new IllegalStateException("telegram.bot-token is not configured");
        }

        String receivedHash = null;
        // TreeMap сразу даёт алфавитный порядок ключей, который требует шаг 2.
        Map<String, String> params = new TreeMap<>();

        for (String pair : initData.split("&")) {
            if (pair.isEmpty()) continue;

            int eq = pair.indexOf('=');
            String rawKey = eq < 0 ? pair : pair.substring(0, eq);
            String rawValue = eq < 0 ? "" : pair.substring(eq + 1);

            String key = urlDecode(rawKey);
            String value = urlDecode(rawValue);

            if ("hash".equals(key)) {
                if (receivedHash != null) {
                    throw new InitDataException("duplicate hash");
                }
                receivedHash = value;
                continue;
            }

            // Дубликат ключа — повод не доверять строке целиком.
            if (params.put(key, value) != null) {
                throw new InitDataException("duplicate parameter: " + key);
            }
        }

        if (receivedHash == null || receivedHash.isBlank()) {
            throw new InitDataException("missing hash");
        }
        if (params.isEmpty()) {
            throw new InitDataException("no data");
        }

        String dataCheckString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow(() -> new InitDataException("empty data-check-string"));

        String expectedHex = hmacHex(dataCheckString);
        if (!constantTimeEquals(expectedHex, receivedHash)) {
            throw new InitDataException("bad signature");
        }

        long authDate = parseAuthDate(params.get("auth_date"));
        long age = Instant.now().getEpochSecond() - authDate;
        if (age < 0) {
            // auth_date из будущего — либо подделка, либо разъехавшиеся часы.
            throw new InitDataException("auth_date is in the future");
        }
        if (age > maxAgeSeconds) {
            throw new InitDataException("expired");
        }

        return parseUser(params.get("user"));
    }

    /** Раскодирует percent-encoding. `+` в query трактуется как пробел. */
    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static long parseAuthDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InitDataException("missing auth_date");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new InitDataException("bad auth_date", e);
        }
    }

    private String hmacHex(String dataCheckString) {
        try {
            // Шаг 3: ключ — константа "WebAppData", сообщение — токен бота.
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            // Шаг 4.
            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new InitDataException("hmac failure", e);
        }
    }

    /** Сравнение за постоянное время — не даёт измерить хэш по символам. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private TelegramUser parseUser(String json) {
        if (json == null || json.isBlank()) {
            throw new InitDataException("missing user");
        }
        try {
            TelegramUser user = objectMapper.readValue(json, TelegramUser.class);
            if (user == null || user.id() <= 0) {
                throw new InitDataException("user has no valid id");
            }
            return user;
        } catch (InitDataException e) {
            throw e;
        } catch (Exception e) {
            throw new InitDataException("bad user json", e);
        }
    }
}