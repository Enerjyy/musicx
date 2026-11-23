package enerjy.musicxService.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Тело запроса логина.
 *
 * `initData` — строка из Telegram.WebApp.initData. Приходит в теле, а не в
 * query-параметрах: query попадает в логи nginx и историю, а initData —
 * секрет, хоть и короткоживущий.
 */
public record AuthRequest(
        @NotBlank(message = "initData is required") String initData
) {
}