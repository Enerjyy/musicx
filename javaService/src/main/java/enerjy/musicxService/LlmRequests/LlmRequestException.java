package enerjy.musicxService.LlmRequests;

public class LlmRequestException extends RuntimeException {

    public LlmRequestException(String message) {
        super(message);
    }

    public LlmRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}