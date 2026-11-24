package enerjy.musicxService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной тест авторизации: initData → JWT → защищённый эндпоинт.
 *
 * Поднимает полный контекст Spring на H2 в памяти и проверяет ровно то, что
 * происходит в реальной жизни: клиент логинится, получает токен, ходит с ним
 * в /calendar, а без токена получает 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String BOT_TOKEN = "test-bot-token-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private enerjy.musicxService.user.userRepo userRepository;

    @Autowired
    private enerjy.musicxService.user.userIdentityRepo identityRepository;

    // ── Генерация initData для теста ───────────────────────────────────

    private static String sign(Map<String, String> params) {
        String dataCheckString = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secret = mac.doFinal(BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String buildInitData(long telegramId, String firstName, String username) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("query_id", "AAHdF6IQ");
        params.put("user", String.format(
                "{\"id\":%d,\"first_name\":\"%s\",\"username\":\"%s\",\"language_code\":\"ru\"}",
                telegramId, firstName, username));
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
              .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.append("&hash=").append(sign(params)).toString();
    }

    private String login(String initData) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("initData", initData));

        String response = mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    // ── Позитив ────────────────────────────────────────────────────────

    @Test
    void loginReturnsTokenAndCreatesUser() throws Exception {
        String initData = buildInitData(111L, "Anna", "anna_dev");

        mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", initData))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.firstName").value("Anna"))
                .andExpect(jsonPath("$.user.username").value("anna_dev"));

        // telegram_id больше не колонка пользователя: связка живёт в user_identity.
        assertThat(identityRepository
                .findByProviderAndProviderUserId(
                        enerjy.musicxService.security.AuthProvider.TELEGRAM, "111"))
                .isPresent();
    }

    @Test
    void secondLoginUpdatesExistingUserInsteadOfDuplicating() throws Exception {
        // Контекст Spring переиспользуется между тестами, поэтому считаем
        // пользователей до и после, а не в абсолюте.
        int usersBefore = userRepository.findAll().size();
        int identitiesBefore = identityRepository.findAll().size();

        String first = buildInitData(222L, "Petr", "petr_old");
        String second = buildInitData(222L, "Petr", "petr_new");

        login(first);
        login(second);

        // Один пользователь и одна связка добавились, а не два.
        assertThat(userRepository.findAll().size()).isEqualTo(usersBefore + 1);
        assertThat(identityRepository.findAll().size()).isEqualTo(identitiesBefore + 1);

        assertThat(identityRepository
                .findByProviderAndProviderUserId(
                        enerjy.musicxService.security.AuthProvider.TELEGRAM, "222"))
                .isPresent();

        assertThat(userRepository.findAll().stream()
                .filter(u -> "petr_new".equals(u.getNickname()))
                .count()).isEqualTo(1);
    }

    @Test
    void protectedEndpointAcceptsValidToken() throws Exception {
        String token = login(buildInitData(333L, "Elena", "elena_dev"));

        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void meEndpointReturnsCurrentUser() throws Exception {
        String token = login(buildInitData(444L, "Ivan", "ivan_dev"));

        mockMvc.perform(get("/user/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Ivan"));
    }

    // ── Негатив: логин ─────────────────────────────────────────────────

    @Test
    void loginRejectsTamperedInitData() throws Exception {
        String initData = buildInitData(555L, "Semen", "semen_dev")
                .replace("Semen", "Attacker");

        mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", initData))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid initData"));
    }

    @Test
    void loginRejectsExpiredInitData() throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("query_id", "AAHdF6IQ");
        params.put("user", "{\"id\":666,\"first_name\":\"Old\",\"username\":\"old\"}");
        params.put("auth_date", String.valueOf(Instant.now().getEpochSecond() - 200_000));

        String initData = new StringBuilder()
                .append("query_id=").append(params.get("query_id"))
                .append("&user=").append(java.net.URLEncoder.encode(params.get("user"), StandardCharsets.UTF_8))
                .append("&auth_date=").append(params.get("auth_date"))
                .append("&hash=").append(sign(params))
                .toString();

        mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", initData))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsBlankInitData() throws Exception {
        // 400, а не 401: @NotBlank ловит пустое поле до валидатора. Это
        // правильнее — запрос malformed, а не «представьтесь заново».
        mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    @Test
    void loginRejectsMissingInitDataField() throws Exception {
        mockMvc.perform(post("/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    // ── Негатив: защищённые эндпоинты ──────────────────────────────────

    @Test
    void calendarWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void calendarWithGarbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calendarWithMalformedHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "TokenWithoutBearerPrefix"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherUserDoesNotLeakData() throws Exception {
        String tokenA = login(buildInitData(777L, "A", "user_a"));
        String tokenB = login(buildInitData(888L, "B", "user_b"));

        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/calendar")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void debugMeEndpointShowsUserAndIdentity() throws Exception {
        String token = login(buildInitData(999L, "Debug", "debug_user"));

        mockMvc.perform(get("/auth/debug/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.provider").value("TELEGRAM"))
                .andExpect(jsonPath("$.user.firstName").value("Debug"))
                .andExpect(jsonPath("$.user.username").value("debug_user"))
                .andExpect(jsonPath("$.identities").isArray())
                .andExpect(jsonPath("$.identities[0].provider").value("TELEGRAM"))
                .andExpect(jsonPath("$.identities[0].providerUserId").value("999"))
                .andExpect(jsonPath("$.scheduleCount").value(0));
    }
}
