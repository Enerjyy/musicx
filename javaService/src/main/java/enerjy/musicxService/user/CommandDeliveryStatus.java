package enerjy.musicxService.user;

/** Статус исполнения команды транспортом (по журналу ядра). */
public enum CommandDeliveryStatus {

    /** Отправлена в Kafka, результата ещё нет. */
    PENDING,

    /** Доставлено (SENT) либо удалять уже нечего (ALREADY_GONE). */
    SENT,

    /** Окончательный отказ (например, BLOCKED/FAILED). */
    FAILED
}
