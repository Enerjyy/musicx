package enerjy.musicxService.messaging.channel;

import enerjy.musicxService.messaging.contract.OutboundCommand;

/**
 * Исходящий транспорт одного мессенджера. Ядро не знает, Telegram это или
 * другой бот: бизнес-логика собирает канал-агностичные команды и публикует
 * их через этот интерфейс. Новый мессенджер = новая реализация.
 */
public interface OutboundChannel {

    /** Идентификатор канала, совпадает с {@code Channels.*}. */
    String id();

    /** Публикует команду в транспорт. Реализация не теряет ошибки отправки. */
    void publish(OutboundCommand command);
}
