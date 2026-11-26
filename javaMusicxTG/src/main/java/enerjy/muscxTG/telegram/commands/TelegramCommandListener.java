package enerjy.muscxTG.telegram.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.Kafka.CFG.ContractKafkaConfig;
import enerjy.muscxTG.messaging.KafkaMessagePublisher;
import enerjy.muscxTG.messaging.contract.Channels;
import enerjy.muscxTG.messaging.contract.DeliveryResult;
import enerjy.muscxTG.messaging.contract.DeliveryStatus;
import enerjy.muscxTG.messaging.contract.DeleteMessage;
import enerjy.muscxTG.messaging.contract.OutboundCommand;
import enerjy.muscxTG.messaging.contract.SendMessage;
import enerjy.muscxTG.messaging.contract.ShowProgress;
import enerjy.muscxTG.messaging.contract.Topics;
import enerjy.muscxTG.telegram.gateway.TelegramApiException;
import enerjy.muscxTG.telegram.gateway.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Исполняет команды ядра и отвечает DeliveryResult.
 *
 * ShowProgress стримится в черновик и не даёт результата; устаревшие
 * (старше {@code app.telegram.progress-ttl}, черновик в Telegram живёт ~30 с)
 * отбрасываются. SendMessage/DeleteMessage дедуплицируются LRU-кэшем по
 * commandId (best effort): при редоставке уже исполненной команды повторная
 * отправка не выполняется; после устранимого сбоя id освобождается, чтобы
 * редоставка могла повторить команду.
 */
@Component
public class TelegramCommandListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandListener.class);

    private final ObjectMapper objectMapper;
    private final TelegramGateway gateway;
    private final KafkaMessagePublisher publisher;
    private final LruCommandCache processedCommands;
    private final Duration progressTtl;

    public TelegramCommandListener(
            ObjectMapper objectMapper,
            TelegramGateway gateway,
            KafkaMessagePublisher publisher,
            @Value("${app.telegram.progress-ttl:PT30S}") Duration progressTtl,
            @Value("${app.telegram.dedup-capacity:10000}") int dedupCapacity
    ) {
        this.objectMapper = objectMapper;
        this.gateway = gateway;
        this.publisher = publisher;
        this.progressTtl = progressTtl;
        this.processedCommands = new LruCommandCache(dedupCapacity);
    }

    @KafkaListener(topics = Topics.OUTBOUND_COMMANDS_TELEGRAM, containerFactory = ContractKafkaConfig.FACTORY)
    public void handle(String json, Acknowledgment ack) throws JsonProcessingException {
        OutboundCommand command = objectMapper.readValue(json, OutboundCommand.class);
        handle(command);
        ack.acknowledge();
    }

    void handle(OutboundCommand command) {
        if (command instanceof ShowProgress progress) {
            showProgress(progress);
        } else if (command instanceof SendMessage send) {
            send(send);
        } else if (command instanceof DeleteMessage delete) {
            delete(delete);
        }
    }

    private void showProgress(ShowProgress progress) {
        if (isStale(progress.occurredAt())) {
            log.debug("stale progress dropped: eventId={}", progress.eventId());
            return;
        }
        try {
            gateway.sendMessageDraft(
                    chatId(progress),
                    Integer.parseInt(progress.draftRef()),
                    progress.text());
        } catch (TelegramApiException e) {
            // Прогресс не ретраится и DeliveryResult не требует.
            log.warn("progress draft failed: eventId={}, status={}", progress.eventId(), e.status());
        } catch (NumberFormatException e) {
            log.warn("bad ids in progress command: eventId={}", progress.eventId());
        }
    }

    private void send(SendMessage command) {
        if (!processedCommands.add(command.commandId())) {
            log.debug("duplicate send command skipped: commandId={}", command.commandId());
            return;
        }
        try {
            long messageId = gateway.sendMessage(chatId(command), command.text());
            publisher.publish(DeliveryResult.create(
                    command.channel(), command.conversationId(), command.commandId(),
                    DeliveryStatus.SENT, String.valueOf(messageId), null, null));
        } catch (TelegramApiException e) {
            publishFailure(command, e);
        }
    }

    private void delete(DeleteMessage command) {
        if (!processedCommands.add(command.commandId())) {
            log.debug("duplicate delete command skipped: commandId={}", command.commandId());
            return;
        }
        try {
            gateway.deleteMessage(chatId(command), Long.parseLong(command.externalMessageId()));
            publisher.publish(DeliveryResult.create(
                    command.channel(), command.conversationId(), command.commandId(),
                    DeliveryStatus.SENT, command.externalMessageId(), null, null));
        } catch (TelegramApiException e) {
            String externalMessageId = e.status() == DeliveryStatus.ALREADY_GONE
                    ? command.externalMessageId()
                    : null;
            publisher.publish(DeliveryResult.create(
                    command.channel(), command.conversationId(), command.commandId(),
                    e.status(), externalMessageId, e.retryAfterSec(), e.getMessage()));
        }
    }

    private void publishFailure(SendMessage command, TelegramApiException e) {
        if (e.status() == DeliveryStatus.RATE_LIMITED || e.status() == DeliveryStatus.TRANSIENT_FAILURE) {
            // Устранимый сбой: освобождаем id, чтобы редоставка повторила команду.
            processedCommands.remove(command.commandId());
        }
        publisher.publish(DeliveryResult.create(
                command.channel(), command.conversationId(), command.commandId(),
                e.status(), null, e.retryAfterSec(), e.getMessage()));
    }

    /** Черновик живёт около 30 секунд — опоздавший прогресс только смутит пользователя. */
    private boolean isStale(String occurredAt) {
        try {
            return Duration.between(Instant.parse(occurredAt), Instant.now()).compareTo(progressTtl) > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static long chatId(OutboundCommand command) {
        return Long.parseLong(command.conversationId());
    }
}
