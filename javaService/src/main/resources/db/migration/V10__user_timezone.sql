-- Часовой пояс пользователя (шаг 9): зона в формате IANA и кто её задал.
-- NULL / NULL — зона ещё не определена; AUTO выставляет Mini App при входе,
-- MANUAL — команда /timezone, и автодетект её не перетирает.
ALTER TABLE user_entity ADD COLUMN timezone VARCHAR(64) NULL;
ALTER TABLE user_entity ADD COLUMN timezone_status VARCHAR(16) NULL;
