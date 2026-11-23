package enerjy.musicxService.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes!!";
    private static final String ISSUER = "musicx-test";

    private JwtService serviceWithTtl(long ttlSeconds) {
        return new JwtService(SECRET, ttlSeconds, ISSUER);
    }

    @Test
    void issuesAndParsesToken() {
        JwtService service = serviceWithTtl(3600);

        String token = service.issue(42L, AuthProvider.TELEGRAM);
        AuthenticatedUser parsed = service.parse(token);

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.provider()).isEqualTo(AuthProvider.TELEGRAM);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = serviceWithTtl(3600);
        String token = service.issue(42L, AuthProvider.TELEGRAM);

        // Меняем последний символ подписи.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 3600, ISSUER);
        JwtService verifier =
                new JwtService("another-secret-key-at-least-32-bytes!!!!", 3600, ISSUER);

        String token = issuer.issue(1L, AuthProvider.TELEGRAM);

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService issuer = new JwtService(SECRET, 3600, "other-issuer");
        JwtService verifier = serviceWithTtl(3600);

        String token = issuer.issue(1L, AuthProvider.TELEGRAM);

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        // TTL = -1 секунда: токен выдан в прошлом.
        JwtService service = serviceWithTtl(-1);
        String token = service.issue(1L, AuthProvider.TELEGRAM);

        assertThatThrownBy(() -> serviceWithTtl(3600).parse(token))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> serviceWithTtl(3600).parse("not-a-jwt"))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void emptyTokenIsRejected() {
        assertThatThrownBy(() -> serviceWithTtl(3600).parse(""))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void nullTokenIsRejected() {
        assertThatThrownBy(() -> serviceWithTtl(3600).parse(null))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void shortSecretFailsFast() {
        // HS256 требует 256-битный ключ. Молчаливая работа со слабым ключом
        // хуже явной ошибки на старте.
        assertThatThrownBy(() -> new JwtService("too-short", 3600, ISSUER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesConfiguredTtl() {
        assertThat(serviceWithTtl(1234).getTtlSeconds()).isEqualTo(1234L);
    }
}
