package enerjy.musicxService.messaging.channel;

import enerjy.musicxService.messaging.KafkaMessagePublisher;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.OutboundCommand;
import org.springframework.stereotype.Component;

/**
 * Telegram через Kafka: команда публикуется в топик
 * {@code outbound.commands.telegram}, исполнение и DeliveryResult — в TG-сервисе.
 */
@Component
public class KafkaOutboundChannel implements OutboundChannel {

    private final KafkaMessagePublisher publisher;

    public KafkaOutboundChannel(KafkaMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String id() {
        return Channels.TELEGRAM;
    }

    @Override
    public void publish(OutboundCommand command) {
        if (!Channels.TELEGRAM.equals(command.channel())) {
            throw new IllegalArgumentException(
                    "channel " + command.channel() + " cannot be published by " + id());
        }
        publisher.publish(command);
    }
}
