package enerjy.muscxTG.telegram.gateway;

/**
 * Транспортный шлюз к Telegram Bot API.
 *
 * Реализация — HTTP-вызовы; ошибки канала выражаются через
 * {@link TelegramApiException} со статусом из контракта
 * ({@code DeliveryStatus}), который слушатель команд превращает в DeliveryResult.
 */
public interface TelegramGateway {

    /**
     * Стриминг черновика статуса (sendMessageDraft): пользователь видит
     * «Thinking…» при пустом тексте. Ответ DeliveryResult не требует.
     */
    void sendMessageDraft(long chatId, int draftId, String text);

    /** Отправка сообщения; возвращает message_id отправленного сообщения. */
    long sendMessage(long chatId, String text);

    /** Удаление ранее отправленного сообщения. */
    void deleteMessage(long chatId, long messageId);
}
