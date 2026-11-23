package enerjy.musicxService.api.dto;

import enerjy.musicxService.user.userEntity;

/**
 * Публичное представление пользователя. Отдаём только то, что нужно фронту.
 *
 * @param id внутренний userId — стабилен, пока живёт запись в БД
 */
public record UserDto(
        long id,
        String firstName,
        String lastName,
        String username
) {
    public static UserDto from(userEntity user) {
        return new UserDto(
                user.getUserId() != null ? user.getUserId() : 0L,
                user.getName(),
                null,
                user.getNickname()
        );
    }
}