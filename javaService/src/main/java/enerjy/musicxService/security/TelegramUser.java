package enerjy.musicxService.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Пользователь из подписанных данных Telegram.
 *
 * Поля приходят в snake_case (`first_name`), а record объявляет camelCase —
 * поэтому каждое поле помечено `@JsonProperty`. Без этого Jackson молча
 * оставил бы все строки в null, и авторизация «работала» бы с пустым именем.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
        @JsonProperty("id") long id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("username") String username,
        @JsonProperty("language_code") String languageCode,
        @JsonProperty("is_premium") boolean isPremium
) {
}