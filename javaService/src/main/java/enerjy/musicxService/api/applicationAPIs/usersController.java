package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.user.TimezoneStatus;
import enerjy.musicxService.user.UserPrincipal;
import enerjy.musicxService.user.userService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * PUT /users/me/timezone — часовой пояс устройства из Mini App.
 *
 * Зона валидируется через {@link ZoneId#of} и сохраняется со статусом AUTO.
 * Зону, выставленную пользователем вручную (/timezone, статус MANUAL),
 * автодетект не перетирает. userId берётся из JWT, как в /user/me.
 */
@RestController
@RequestMapping("/users")
public class usersController {

    private final userService userService;

    public usersController(userService userService) {
        this.userService = userService;
    }

    public record TimezoneRequest(String timezone) {
    }

    public record TimezoneResponse(String timezone, String status) {
    }

    @PutMapping("/me/timezone")
    public ResponseEntity<TimezoneResponse> setMyTimezone(
            @RequestAttribute("principal") UserPrincipal principal,
            @RequestBody TimezoneRequest request
    ) {
        if (request == null || request.timezone() == null || request.timezone().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(request.timezone().trim());
        } catch (DateTimeException e) {
            return ResponseEntity.badRequest().build();
        }

        var user = userService.setTimezone(principal.userId(), zone, TimezoneStatus.AUTO);
        var status = user.getTimezoneStatus() != null ? user.getTimezoneStatus() : TimezoneStatus.AUTO;
        return ResponseEntity.ok(new TimezoneResponse(user.getTimezone(), status.name()));
    }
}
