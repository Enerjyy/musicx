package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.api.dto.UserDto;
import enerjy.musicxService.user.UserPrincipal;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Профиль текущего пользователя и список всех пользователей.
 *
 * Оба метода защищены фильтром: без валидного токена запрос не дойдёт.
 * userId берётся из токена, а не из параметров — иначе можно было бы
 * запросить чужой профиль.
 */
@RestController
@RequestMapping("/user")
public class userController {

    private final userService userService;

    public userController(userService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@RequestAttribute("principal") UserPrincipal principal) {
        return userService.findById(principal.userId())
                .map(user -> ResponseEntity.ok(UserDto.from(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userService.findAll().stream()
                .map(UserDto::from)
                .toList();
        return ResponseEntity.ok(users);
    }
}