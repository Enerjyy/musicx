package enerjy.musicxService.user;

import enerjy.musicxService.security.AuthProvider;

/**
 * Кто пришёл в запросе. Складывается в request attribute фильтром
 * `JwtAuthFilter` и достаётся контроллерами через `@RequestAttribute`.
 *
 * Содержит внутренний `userId` — этого достаточно контроллерам, а способ
 * входа нужен только для аудита и отладки, но лежит здесь бесплатно.
 */
public record UserPrincipal(long userId, AuthProvider provider) {
}