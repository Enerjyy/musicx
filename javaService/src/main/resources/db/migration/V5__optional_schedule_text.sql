-- title и note опциональны: пользователь может написать «поиграю 25 ноября в 15:00»,
-- и у записи не будет ни названия, ни заметки. Дата и время при этом обязательны —
-- без них запись не имеет смысла и не создаётся (проверяется в handleSchedule).
ALTER TABLE user_schedules ALTER COLUMN title DROP NOT NULL;
ALTER TABLE user_schedules ALTER COLUMN note DROP NOT NULL;