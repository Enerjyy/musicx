package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.api.dto.CalendarEntryDto;
import enerjy.musicxService.user.UserPrincipal;
import enerjy.musicxService.user.userService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Календарь пользователя.
 *
 * Ключевой момент: userId берётся ИЗ ТОКЕНА (`@RequestAttribute`), а не из
 * параметров запроса. Если бы контроллер принимал `?userId=...`, любой мог
 * бы запросить чужой календарь. Токен подписан нашим секретом, подделать
 * нельзя.
 *
 * Сейчас отдаёт пустой список-заглушку: слой хранения записей ещё не
 * спроектирован. Контракт с фронтом при этом финальный, и когда таблица
 * появится, меняется только тело этого метода.
 */
@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final userService userService;

    public CalendarController(userService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<CalendarEntryDto>> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @RequestAttribute("principal") UserPrincipal principal
    ) {
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }

        boolean known = userService.findById(principal.userId()).isPresent();
        if (!known) {
            return ResponseEntity.status(401).build();
        }

        // Заглушка: реальная выборка появится вместе с таблицей записей.
        return ResponseEntity.ok(new ArrayList<>());
    }
}