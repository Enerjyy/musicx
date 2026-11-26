package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Пересчёт старых записей (date + time) в start_at с зоной из app.time-zone.
 *
 * Java-миграция, а не SQL: правило интерпретации локального времени в момент
 * времени (DST) у H2 и Postgres разное, а java.time даёт одинаковый результат.
 * end_at остаётся null — у старых записей длительность неизвестна.
 *
 * Зона берётся из Flyway-placeholder'а app-time-zone (см. application.yml).
 */
public class V7__BackfillScheduleStartAt extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        ZoneId zone = ZoneId.of(context.getConfiguration().getPlaceholders()
                .getOrDefault("app-time-zone", "Europe/Moscow"));

        record Row(long id, LocalDateTime local) {
        }
        var rows = new ArrayList<Row>();

        try (Statement statement = context.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT scedule_id, date, time FROM user_schedules WHERE start_at IS NULL")) {
            while (rs.next()) {
                rows.add(new Row(
                        rs.getLong("scedule_id"),
                        rs.getDate("date").toLocalDate().atTime(rs.getTime("time").toLocalTime())));
            }
        }

        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE user_schedules SET start_at = ?, timezone = ?, status = 'ACTIVE', created_at = ? "
                        + "WHERE scedule_id = ?")) {
            Instant now = Instant.now();
            for (Row row : rows) {
                update.setTimestamp(1, Timestamp.from(row.local().atZone(zone).toInstant()));
                update.setString(2, zone.getId());
                update.setTimestamp(3, Timestamp.from(now));
                update.setLong(4, row.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
