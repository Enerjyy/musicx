package enerjy.musicxService.Kafka.CFG;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Проверяет на реальном (встроенном) брокере: топики создаются, ручной ack работает,
 * сообщение, которое не удаётся обработать, попадает в DLT после повторов,
 * а следующее сообщение того же ключа обрабатывается.
 */
@SpringJUnitConfig(ContractKafkaIntegrationTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "app.kafka.error-handler.max-retries=2",
        "app.kafka.error-handler.initial-interval-ms=20",
        "app.kafka.error-handler.max-interval-ms=50"
})
class ContractKafkaIntegrationTest {

    @Configuration
    @EnableKafka
    @Import({KafkaProducerConfig.class, ContractKafkaConfig.class, KafkaTopicsConfig.class})
    static class TestConfig {
        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    static class TestListener {
        final List<String> processed = new CopyOnWriteArrayList<>();
        final List<String> attempts = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "inbound.messages", containerFactory = ContractKafkaConfig.FACTORY)
        void on(String json, Acknowledgment ack) {
            attempts.add(json);
            if (json.contains("boom")) {
                throw new IllegalStateException("cannot process");
            }
            processed.add(json);
            ack.acknowledge();
        }
    }

    @Autowired
    KafkaTemplate<String, String> template;

    @Autowired
    TestListener listener;

    @Test
    void failingMessageGoesToDltAndNextMessageIsStillProcessed() throws Exception {
        String key = "TELEGRAM:1";
        String bad = "boom-" + UUID.randomUUID();
        String good = "ok-" + UUID.randomUUID();

        template.send("inbound.messages", key, bad).get();
        template.send("inbound.messages", key, good).get();

        await().atMost(Duration.ofSeconds(30)).until(() -> listener.processed.contains(good));
        assertThat(listener.processed).doesNotContain(bad);
        // первая попытка плюс два повтора
        assertThat(listener.attempts.stream().filter(bad::equals).count()).isEqualTo(3);

        ConsumerRecord<String, String> dead = awaitDeadLetter(bad);
        assertThat(dead.key()).isEqualTo(key);
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
    }

    private static ConsumerRecord<String, String> awaitDeadLetter(String value) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of("inbound.messages.dlt"));
            long deadline = System.currentTimeMillis() + 30_000;
            List<String> seen = new ArrayList<>();
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    seen.add(record.value());
                    if (value.equals(record.value())) {
                        return record;
                    }
                }
            }
            throw new AssertionError("no dead letter for " + value + ", seen " + seen);
        }
    }
}
