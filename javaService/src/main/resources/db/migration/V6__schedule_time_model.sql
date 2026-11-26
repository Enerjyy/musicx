-- Модель времени расписания (Шаг 4): абсолютные моменты времени рядом
-- с локальными date/time.
--
-- date/time остаются источником для /calendar, start_at/end_at (timestamptz) —
-- источником для напоминаний и проверок «прошлое/будущее». timezone — снимок
-- зоны пользователя на момент создания: её смена позже не двигает записи.
--
-- Колонки nullable, чтобы записи до появления валидатора сохранялись как раньше.
-- Ограничение NOT NULL на start_at/timezone будет добавлено позже, когда путь
-- через валидатор станет единственным (V7 уже заполняет старые строки).

ALTER TABLE user_schedules ADD COLUMN start_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE user_schedules ADD COLUMN end_at   TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE user_schedules ADD COLUMN timezone VARCHAR(64) NULL;
ALTER TABLE user_schedules ADD COLUMN status   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE user_schedules ADD COLUMN source_channel         VARCHAR(32)  NULL;
ALTER TABLE user_schedules ADD COLUMN source_conversation_id VARCHAR(255) NULL;
ALTER TABLE user_schedules ADD COLUMN source_message_id      VARCHAR(64)  NULL;
ALTER TABLE user_schedules ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NULL;

-- Описание может быть длинным: поднимаем лимит с 255 до 1000.
ALTER TABLE user_schedules ALTER COLUMN note SET DATA TYPE VARCHAR(1000);

-- Дедупликация входящих сообщений: одна тройка — одна запись.
-- NULL-комбинации (старые записи и записи через API) индекс не ограничивает.
CREATE UNIQUE INDEX ux_user_schedules_source
    ON user_schedules (source_channel, source_conversation_id, source_message_id);
