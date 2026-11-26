package enerjy.muscxTG.telegram.gateway;

import enerjy.muscxTG.messaging.contract.DeliveryStatus;

/** Ошибка вызова Telegram Bot API со статусом, понятным ядру. */
public class TelegramApiException extends RuntimeException {

    private final DeliveryStatus status;
    private final Integer retryAfterSec;

    public TelegramApiException(DeliveryStatus status, Integer retryAfterSec, String message) {
        super(message);
        this.status = status;
        this.retryAfterSec = retryAfterSec;
    }

    public TelegramApiException(DeliveryStatus status, Integer retryAfterSec, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryAfterSec = retryAfterSec;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Integer retryAfterSec() {
        return retryAfterSec;
    }
}
