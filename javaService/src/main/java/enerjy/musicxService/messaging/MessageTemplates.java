package enerjy.musicxService.messaging;

import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderKind;
import enerjy.musicxService.user.userSchedules;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Тексты для пользователя. Канал-агностичны: форматирует ядро, транспорт не меняет. */
public final class MessageTemplates {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Этапы статуса создания записи (черновик в канале). */
    public static final String PARSING = "Разбираю запрос…";
    public static final String VALIDATING = "Проверяю дату и время…";
    public static final String SAVING = "Сохраняю…";

    /** Сбой разбора: повторная попытка безопасна, автоудаление не нужно. */
    public static final String LLM_FAILED =
            "Не смог разобрать сообщение. Попробуйте ещё раз чуть позже.";

    /** Правка/удаление: ни одной записи не нашлось по опорным точкам. */
    public static final String CLARIFY_NOT_FOUND =
            "Не нашёл такую запись. Проверьте дату и название, например: "
                    + "«перенеси урок гитары 27.11 на 16:00».";

    /** Правка/удаление: кандидат больше одного — запись не трогаем без уточнения. */
    public static final String CLARIFY_SEVERAL =
            "Нашёл несколько похожих записей. Уточните время, например: "
                    + "«перенеси урок гитары в 15:00 на 16:00».";

    /** Правка/удаление: модель не дала даже даты искомой записи. */
    public static final String CLARIFY_WHICH =
            "Не понял, какую запись изменить. Укажите дату, например: "
                    + "«перенеси урок гитары 27.11 на 16:00».";

    private MessageTemplates() {
    }

    /** Подтверждение создания: название, дата и время в зоне записи, длительность, описание. */
    public static String confirmation(userSchedules schedule) {
        return eventText("Запись создана", schedule);
    }

    /** Подтверждение переноса: новое время и длительность записи. */
    public static String confirmUpdate(userSchedules schedule) {
        return eventText("Запись обновлена", schedule);
    }

    /** Подтверждение удаления: что именно ушло из календаря. */
    public static String confirmDelete(userSchedules schedule) {
        return eventText("Запись удалена", schedule);
    }

    /**
     * Общий каркас «заголовок + когда + описание» для подтверждений создания,
     * переноса и удаления. Для записи «весь день» ({@code startAt = null})
     * вместо пустого времени пишем дату с пометкой «весь день» — иначе
     * строка форматирования упала бы на null.
     */
    private static String eventText(String headline, userSchedules schedule) {
        var text = new StringBuilder(headline);
        if (schedule.getTitle() != null) {
            text.append(": ").append(schedule.getTitle());
        }
        text.append('\n').append("Когда: ");
        if (schedule.getStartAt() == null) {
            // Безвременная запись: время есть только в локальном поле date.
            text.append(schedule.getDate().format(DATE)).append(" — весь день");
        } else {
            var zone = ZoneId.of(schedule.getTimezone());
            text.append(schedule.getStartAt().atZone(zone).format(DATE_TIME));
            if (schedule.getEndAt() != null) {
                long minutes = Duration.between(schedule.getStartAt(), schedule.getEndAt()).toMinutes();
                text.append('–')
                        .append(schedule.getEndAt().atZone(zone).format(TIME))
                        .append(" (длительность ").append(duration(minutes)).append(')');
            }
        }
        if (schedule.getNote() != null) {
            text.append("\nОписание: ").append(schedule.getNote());
        }
        return text.toString();
    }

    /** Напоминание: за сколько до начала, название и локальное время события. */
    public static String reminder(userSchedules schedule, Reminder reminder, Duration offset) {
        // «Весь день»: времени события нет, отдельная формулировка вместо
        // «через 3 часа» и пустого момента.
        if (reminder.getKind() == ReminderKind.MIDNIGHT) {
            return reminderAllDay(schedule);
        }
        var zone = schedule.getTimezone() != null
                ? ZoneId.of(schedule.getTimezone())
                : java.time.ZoneOffset.UTC;
        var text = new StringBuilder("Напоминание: ");
        long minutes = offset.toMinutes();
        text.append(minutes > 0 ? "через " + duration(minutes) + " — " : "уже скоро — ");
        if (schedule.getTitle() != null) {
            text.append('«').append(schedule.getTitle()).append("» ");
        }
        text.append(schedule.getStartAt().atZone(zone).format(DATE_TIME));
        if (schedule.getNote() != null) {
            text.append("\nОписание: ").append(schedule.getNote());
        }
        return text.toString();
    }

    /**
     * Напоминание по записи «весь день» (kind MIDNIGHT): отправляется ровно в
     * 00:00 дня события в зоне записи, поэтому вместо «через N часов» — «сегодня».
     */
    private static String reminderAllDay(userSchedules schedule) {
        var text = new StringBuilder("Напоминание: сегодня — ");
        if (schedule.getTitle() != null) {
            text.append('«').append(schedule.getTitle()).append("» ");
        }
        text.append(schedule.getDate().format(DATE)).append(", весь день");
        if (schedule.getNote() != null) {
            text.append("\nОписание: ").append(schedule.getNote());
        }
        return text.toString();
    }

    static String duration(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " мин";
        }
        if (rest == 0) {
            return hours + " ч";
        }
        return hours + " ч " + rest + " мин";
    }
}
