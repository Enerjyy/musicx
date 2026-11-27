package enerjy.musicxService.Logic.schedule.parse;

/**
 * Что пользователь хочет сделать с записью календаря (контракт v2 парсинга).
 *
 * Интент определяет дальнейший сценарий: {@code CREATE} — обычное создание
 * записи, {@code UPDATE} — перенос/правка существующей, {@code DELETE} —
 * удаление существующей. Для update/delete модель дополнительно возвращает
 * опорные точки искомой записи ({@code match*}-поля {@link ParsedEvent}),
 * по которым код находит кандидатов среди записей пользователя.
 */
public enum Intent {
    CREATE,
    UPDATE,
    DELETE;

    /** Разбор значения из ответа модели; null/неизвестное → CREATE. */
    public static Intent from(String raw) {
        if (raw == null) {
            return CREATE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Модель иногда присылает мусор вместо enum: безопаснее считать это
            // созданием (историческое поведение), а не падать целиком.
            return CREATE;
        }
    }
}
