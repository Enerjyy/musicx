package enerjy.musicxService.security;

/** Токен не прошёл проверку: подпись, срок, формат. */
public class TokenException extends RuntimeException {

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}