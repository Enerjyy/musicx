package enerjy.musicxService.security;

/**
 * Результат проверки токена: внутренний id пользователя и способ входа.
 *
 * {@code JwtAuthFilter} кладёт {@link enerjy.musicxService.user.UserPrincipal}
 * с этими же данными в request attribute, а контроллеры достают его через
 * {@code @RequestAttribute}.
 */
public record AuthenticatedUser(long userId, AuthProvider provider) {
}