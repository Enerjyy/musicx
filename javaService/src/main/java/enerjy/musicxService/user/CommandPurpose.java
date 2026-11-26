package enerjy.musicxService.user;

/** Зачем ядру эта команда: связывает DeliveryResult с целью отправки. */
public enum CommandPurpose {

    /** Подтверждение создания записи (удаляется через 30 с, Шаг 6). */
    CONFIRM_SCHEDULE,

    /** Фоновое удаление сообщения. */
    DELETE_CONFIRMATION,

    /** Напоминание о событии. */
    REMINDER
}
