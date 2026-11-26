package enerjy.muscxTG.Kafka.CFG;

import com.fasterxml.jackson.core.JsonProcessingException;
import enerjy.muscxTG.messaging.contract.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

/**
 * Консьюмеры контракта v1 в TG-сервисе: ручной ack, повторы с нарастающей
 * паузой и DLT. Зеркало конфигурации ядра; топики создаёт ядро.
 */
@Configuration
public class ContractKafkaConfig {

    public static final String FACTORY = "contractListenerContainerFactory";

    @Bean(name = FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> contractListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:telegram-bot-group}") String groupId,
            @Value("${app.kafka.error-handler.max-retries:4}") int maxRetries,
            @Value("${app.kafka.error-handler.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${app.kafka.error-handler.multiplier:2.0}") double multiplier,
            @Value("${app.kafka.error-handler.max-interval-ms:10000}") long maxIntervalMs,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(
                errorHandler(kafkaTemplate, maxRetries, initialIntervalMs, multiplier, maxIntervalMs));
        return factory;
    }

    static DefaultErrorHandler errorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            int maxRetries,
            long initialIntervalMs,
            double multiplier,
            long maxIntervalMs
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, ContractKafkaConfig::deadLetterDestination);

        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    /** Партиция -1: Kafka выбирает её по ключу, порядок внутри диалога сохраняется. */
    static TopicPartition deadLetterDestination(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(Topics.dlt(record.topic()), -1);
    }
}
