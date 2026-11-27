package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Безвременные записи («весь день»): колонка time в user_schedules становится
 * nullable. Дата при этом остаётся обязательной — день события известен всегда,
 * отсутствует только момент внутри дня. start_at/end_at уже nullable (V6/V7),
 * отдельную колонку длительности не заводим — она считается из start/end.
 *
 * Java-миграция, а не SQL: V7 сделана на Java ради одинакового поведения H2 и
 * Postgres; здесь правка тривиальная, но держим стиль миграций единым.
 */
public class V11__ScheduleTimelessEntries extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE user_schedules ALTER COLUMN time DROP NOT NULL");
        }
    }
}
