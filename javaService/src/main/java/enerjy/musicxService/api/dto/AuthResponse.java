package enerjy.musicxService.api.dto;

/**
 * Ответ логина: собственный токен + профиль пользователя.
 *
 * Форма совпадает с типом `TelegramAuthResult` на фронте
 * (src/telegram/auth.ts) — отсюда snake_case, а не camelCase.
 */
public record AuthResponse(
        String token,
        long expiresIn,
        UserDto user
) {
}