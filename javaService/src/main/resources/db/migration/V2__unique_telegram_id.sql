-- Уникальность telegram_id: два одновременных логина одного пользователя
-- не должны создать две записи, иначе upsert начнёт вести себя непредсказуемо.
--
-- Констрейнт уже объявлен в сущности (@Table uniqueConstraints), но Hibernate
-- при ddl-auto=validate схему не меняет — индекс создаёт только Flyway.
ALTER TABLE user_entity
    ADD CONSTRAINT uk_user_entity_telegram_id UNIQUE (telegram_id);