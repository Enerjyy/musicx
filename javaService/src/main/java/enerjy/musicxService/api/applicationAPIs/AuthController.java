package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.api.dto.AuthRequest;
import enerjy.musicxService.api.dto.AuthResponse;
import enerjy.musicxService.api.dto.UserDto;
import enerjy.musicxService.security.AuthProvider;
import enerjy.musicxService.security.JwtService;
import enerjy.musicxService.security.TGDataValidation;
import enerjy.musicxService.security.TelegramUser;
import enerjy.musicxService.user.UserPrincipal;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userIdentityEntity;
import enerjy.musicxService.user.userService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Обмен данных, подтверждённых провайдером, на собственный токен сессии.
 *
 * Поток для Telegram:
 *  1. Проверяем подпись initData по токену бота (TGDataValidation).
 *  2. Ищем пользователя по (TELEGRAM, telegram_id) или создаём.
 *  3. Выдаём JWT с userId в subject.
 *
 * Это единственный эндпоинт, где initData принимается: дальше везде работает
 * только собственный токен. Поэтому именно здесь весь риск, и именно поэтому
 * подпись проверяется до любого обращения к БД.
 *
 * Когда появится второй провайдер, здесь добавится ещё один метод
 * (например {@code /auth/google}), который сделает те же шаги 2-3, но с
 * проверкой токена Google вместо initData. Шаги 2-3 уже обобщены в
 * {@link userService#upsertByIdentity}.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TGDataValidation validation;
    private final JwtService jwtService;
    private final userService userService;
    private final long maxAgeSeconds;

    public AuthController(
            TGDataValidation validation,
            JwtService jwtService,
            userService userService,
            @Value("${telegram.max-age-seconds}") long maxAgeSeconds
    ) {
        this.validation = validation;
        this.jwtService = jwtService;
        this.userService = userService;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @PostMapping("/telegram")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        TelegramUser telegramUser = validation.validate(request.initData(), maxAgeSeconds);

        userEntity user = userService.upsertByIdentity(
                AuthProvider.TELEGRAM,
                String.valueOf(telegramUser.id()),
                telegramUser.firstName(),
                telegramUser.username()
        );

        String token = jwtService.issue(user.getUserId(), AuthProvider.TELEGRAM);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new AuthResponse(token, jwtService.getTtlSeconds(), UserDto.from(user)));
    }

    @GetMapping("/debug/me")
    public ResponseEntity<Map<String, Object>> debugMe(@RequestAttribute("principal") UserPrincipal principal) {
        long userId = principal.userId();
        userEntity user = userService.findById(userId).orElse(null);
        List<userIdentityEntity> identities = userService.findIdentities(userId);
        long scheduleCount = userService.countSchedules(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "provider", principal.provider().name(),
                "user", user != null ? Map.of(
                        "firstName", user.getName(),
                        "username", user.getNickname()
                ) : null,
                "identities", identities.stream().map(i -> Map.of(
                        "provider", i.getProvider().name(),
                        "providerUserId", i.getProviderUserId()
                )).toList(),
                "scheduleCount", scheduleCount
        ));
    }
}