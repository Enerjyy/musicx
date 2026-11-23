package enerjy.musicxService.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Выдача и проверка собственных токенов сессии.
 *
 * Зачем свой токен, если есть initData: initData — долгоживущий подписанный
 * блоб, который Telegram не обновляет. Гонять его в каждом запросе плохо —
 * он становится bearer-креденшлом и реиграбелен до истечения auth_date.
 * Поэтому обмениваем один раз на JWT с коротким сроком.
 *
 * Токен не знает ничего про способ входа, кроме claim `provider`:
 * {@code subject} — внутренний {@code userId}, провайдер лежит отдельно.
 * Благодаря этому токен, выданный после входа через Telegram, и токен после
 * входа через Google проверяются и читаются одинаково.
 *
 * Секрет — отдельный `jwt.secret`, НЕ токен бота: бот-токен нужен только для
 * проверки подписи Telegram на этапе логина.
 */
@Component
public class JwtService {

    private static final String CLAIM_PROVIDER = "provider";

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.ttl-seconds}") long ttlSeconds,
            @Value("${jwt.issuer}") String issuer
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256 требует ключ от 256 бит. Короткий секрет — уязвимость,
            // лучше упасть на старте, чем тихо работать со слабым ключом.
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes for HS256, got " + secretBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    /**
     * Выдаёт токен для пользователя.
     *
     * В subject — внутренний userId (стабилен, пока живёт запись в БД).
     * Провайдер — отдельным claim, только для информации.
     */
    public String issue(long userId, AuthProvider provider) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_PROVIDER, provider.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Проверяет подпись и срок, возвращает userId и способ входа.
     *
     * @throws TokenException если токен битый, просрочен или подписан другим ключом
     */
    public AuthenticatedUser parse(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // ExpiredJwtException, SignatureException, MalformedJwtException и прочее
            throw new TokenException("invalid token", e);
        }

        String subject = claims.getSubject();
        if (subject == null) {
            throw new TokenException("token has no subject");
        }

        final long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new TokenException("token subject is not a number", e);
        }

        // Провайдер — информационный claim. Если его нет (старый токен) —
        // считаем, что это Telegram, чтобы не ломать уже выданные токены.
        AuthProvider provider = parseProvider(claims.get(CLAIM_PROVIDER, String.class));

        return new AuthenticatedUser(userId, provider);
    }

    private static AuthProvider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) return AuthProvider.TELEGRAM;
        try {
            return AuthProvider.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // Провайдер, о котором сервер не знает (токен из более новой версии) —
            // не повод отклонять запрос: личность уже подтверждена подписью.
            return AuthProvider.TELEGRAM;
        }
    }

    /** Сколько живёт токен — нужно, чтобы фронт мог планировать обновление. */
    public long getTtlSeconds() {
        return ttlSeconds;
    }
}