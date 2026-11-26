package enerjy.musicxService.messaging.contract;

public enum DeliveryStatus {
    /** Команда выполнена. */
    SENT,
    /** Сообщение для удаления уже отсутствует; для ядра это успех. */
    ALREADY_GONE,
    /** Пользователь заблокировал бота или недоступен навсегда. */
    BLOCKED,
    /** Превышен лимит запросов канала, повторить через retryAfterSec. */
    RATE_LIMITED,
    /** Временный сбой, можно повторить. */
    TRANSIENT_FAILURE,
    /** Окончательный отказ без повтора. */
    FAILED
}
