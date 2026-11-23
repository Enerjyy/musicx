package enerjy.musicxService.api;

import enerjy.musicxService.LlmRequests.LlmRequestException;
import enerjy.musicxService.security.InitDataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Ошибки в JSON, а не в HTML.
 *
 * Без этого Spring отдаёт свою страницу с трейсбеком, фронт не может её
 * распарсить, и вместо понятной ошибки прилетает «Unexpected token <».
 *
 * Наружу не отдаём текст исключения: в сообщениях валидатора могут быть
 * детали вроде имени параметра. Клиенту достаточно кода и общего смысла.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InitDataException.class)
    public ResponseEntity<Map<String, String>> handleInitData(InitDataException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid initData"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid request"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad request"));
    }

    @ExceptionHandler(LlmRequestException.class)
    public ResponseEntity<Map<String, String>> handleLlm(LlmRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "ai unavailable"));
    }
}